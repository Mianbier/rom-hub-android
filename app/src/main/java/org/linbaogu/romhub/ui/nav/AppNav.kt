package org.linbaogu.romhub.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 页面栈里的一层。参照 KernelSU 的 Navigator：单一栈，pop 只在 size>1 时发生。 */
sealed interface Screen {
    data class Tab(val index: Int) : Screen
    data class DeviceDetail(val code: String) : Screen
    data class VersionList(
        val code: String,
        val region: String,
        val branch: String,
        val highlight: String = "",
    ) : Screen
    data class PortDetail(val id: Long) : Screen
}

/**
 * 导航状态。
 *
 * 返回优先级照搬 KernelSU：
 *  ① 还有更深的页面 → pop
 *  ② 已在 tab 根、但不在第 0 个 tab → 回第 0 个 tab
 *  ③ 已在第 0 个 tab 的根 → 交回系统（Activity 自己决定退出）
 *
 * 另外会把整条栈序列化存进 [Prefs]，这样跳浏览器（或系统把进程回收）之后回来，
 * 还停在原来那一页，不会被踹回首页。
 */
class AppNavState(private val persist: (String) -> Unit = {}) {

    val stack = mutableStateListOf<Screen>(Screen.Tab(0))

    var tabCount by mutableStateOf(3)

    val current: Screen get() = stack.last()

    val isAtRoot: Boolean get() = stack.size == 1

    val currentTab: Int get() = (stack.firstOrNull() as? Screen.Tab)?.index ?: 0

    fun switchTab(index: Int) {
        if (index !in 0 until tabCount) return
        stack.clear()
        stack.add(Screen.Tab(index))
        save()
    }

    fun push(screen: Screen) {
        if (stack.size > 24) return
        stack.add(screen)
        save()
    }

    fun replaceTop(screen: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(screen)
        save()
    }

    /** @return true 表示已消费；false 表示该交给系统了 */
    fun back(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            save()
            return true
        }
        val tab = currentTab
        if (tab != 0) {
            switchTab(0)
            return true
        }
        return false
    }

    fun toRoot() {
        if (stack.size <= 1) return
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        save()
    }

    // ------------------------------------------------------------ 持久化

    private fun save() {
        runCatching { persist(snapshot()) }
    }

    /** 把整条栈编成一个字符串，例如 `t2;d:athens;r:athens,cn,stable,OS1.0`。 */
    fun snapshot(): String = stack.joinToString(";") { s ->
        when (s) {
            is Screen.Tab -> "t${s.index}"
            is Screen.DeviceDetail -> "d${s.code}"
            is Screen.VersionList -> "r${s.code},${s.region},${s.branch},${s.highlight}"
            is Screen.PortDetail -> "p${s.id}"
        }
    }

    /**
     * 从字符串恢复。任何一段解析不出来就丢掉那一段；
     * 第一层必须是个 Tab，否则整条作废（回到首页）。
     * 注意：这里**不触发保存**，避免启动时把空串写回去。
     */
    fun restore(raw: String?) {
        val list = raw.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull(::decodeScreen)
        if (list.isEmpty() || list.first() !is Screen.Tab) return
        val fixed = list.map { if (it is Screen.Tab) Screen.Tab(it.index) else it }
            .take(24)
            .toMutableList()
        // 第一层的 tab 索引要合法
        val head = fixed.first()
        if ((head as Screen.Tab).index !in 0 until tabCount) fixed[0] = Screen.Tab(0)
        stack.clear()
        stack.addAll(fixed)
    }

    private fun decodeScreen(s: String): Screen? = when (s.firstOrNull()) {
        't' -> s.drop(1).toIntOrNull()?.let { Screen.Tab(it) }
        'd' -> s.drop(1).takeIf { it.isNotBlank() }?.let { Screen.DeviceDetail(it) }
        'p' -> s.drop(1).toLongOrNull()?.let { Screen.PortDetail(it) }
        'r' -> {
            val f = s.drop(1).split(',')
            if (f.size >= 3 && f[0].isNotBlank()) {
                Screen.VersionList(f[0], f[1], f[2], f.getOrElse(3) { "" })
            } else {
                null
            }
        }
        else -> null
    }
}

/** 解析 romhub:// 深链。通知点击后走这里。 */
fun parseDeepLink(uri: String): Screen? {
    if (!uri.startsWith("romhub://")) return null
    val body = uri.removePrefix("romhub://")
    val path = body.substringBefore('?')
    val query = body.substringAfter('?', "")
    val q: Map<String, String> = query.split('&')
        .mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to decode(it.substring(i + 1))
        }
        .toMap()

    return when (path) {
        "ver" -> {
            val code = q["code"].orEmpty()
            if (code.isBlank()) null
            else Screen.VersionList(
                code = code,
                region = q["region"].orEmpty().ifBlank { "all" },
                branch = q["branch"].orEmpty().ifBlank { "all" },
                highlight = q["ver"].orEmpty(),
            )
        }

        "port" -> q["id"]?.toLongOrNull()?.let { Screen.PortDetail(it) }
        "dev" -> q["code"]?.takeIf { it.isNotBlank() }?.let { Screen.DeviceDetail(it) }
        "feed" -> Screen.Tab(1)
        "about" -> Screen.Tab(2)
        else -> null
    }
}

private fun decode(s: String): String = runCatching {
    java.net.URLDecoder.decode(s, "UTF-8")
}.getOrDefault(s)
