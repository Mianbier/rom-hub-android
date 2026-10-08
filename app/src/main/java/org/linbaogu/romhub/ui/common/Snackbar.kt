package org.linbaogu.romhub.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局提示条通道：任何位置（Composable / 工具函数）调 [show] 即可弹出提示，
 * 页面层用 [rememberGlobalSnackbarHostState] 挂一个宿主渲染。
 *
 * 搬运自云析（CYQawa/YunX，AGPL-3.0）的 SnackbarController。
 * 保留它「自增序号 + 只清自己那条」的设计：并发多条提示时不会互相盖掉 ——
 * 每条 show() 都生成独立事件，前一条消失后，期间排队的下一条会自动顶上。
 *
 * 多个宿主同时活跃（主界面 / 各登录页 / 各弹窗）时共享同一份广播，各自渲染一份。
 */
object SnackbarController {

    internal data class Event(val seq: Long, val message: String)

    private val _events = MutableStateFlow<Event?>(null)
    private var seq = 0L

    internal val events: StateFlow<Event?> = _events

    fun show(message: String) {
        // 每次都换新 Event（seq 递增）→ StateFlow 的值必然变化 → 所有收集者都能收到
        _events.value = Event(++seq, message)
    }

    /** 消费事件：只有「当前事件正是刚显示的那条」才清空，避免把期间新来的消息误清掉。 */
    fun consume(shownSeq: Long) {
        val cur = _events.value
        if (cur != null && cur.seq == shownSeq) _events.value = null
    }
}

/** 渲染提示条并监听全局事件；放进页面最外层 Box 即可（盖在内容之上，但不拦点击）。 */
@Composable
fun GlobalSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SnackbarHost(hostState = hostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 给 Scaffold(snackbarHost = ...) 或页面 Box 用的宿主状态（自动监听全局事件）。 */
@Composable
fun rememberGlobalSnackbarHostState(): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
    return hostState
}
