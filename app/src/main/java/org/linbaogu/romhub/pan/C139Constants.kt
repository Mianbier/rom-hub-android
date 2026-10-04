/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linbaogu.romhub.pan

import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * 139 网盘（中国移动·和彩云）登录常量（依据《139网盘解析方法-alist逆向.md》§3.5）。
 * 登录方案：WebView 加载 yun.139.com（PC UA）由用户手动登录，再提取 Cookie 持久化。
 * 登录态两种形式（都支持）：
 *  - A：mail.10086.cn 的 Os_SSo_Sid + RMKEY（alist fast login 路径，§3.4/§3.5.3）；
 *  - B：yun.139.com 网页版直接给出的 authorization（§3.5.5，连 fast login 都省了）。
 */
object C139Constants {

    /**
     * WebView 登录页：139 云盘**手机版**登录页（用户实际操作的页面）。
     *
     * ⚠️ 为什么用手机版而不是电脑版：电脑版 SPA 有深度环境检测
     * （`navigator.plugins` / `window.chrome` 等特征在 WebView 里缺失），
     * **在手机 WebView 里必白屏**，JS 不跑就什么都拿不到（用户实测「电脑版还是白的」）。
     *
     * ✅ 手机版一样能拿到 `authorization`：抓 `app.b95b950e.js` 可以看到登录接口返回后
     * 立刻执行 `commit("setAuthorization", enCodeToken(token, ..., "pc"))`，
     * 由 `setAuthorization` 以 **30 天有效期写入名为 `authorization` 的 Cookie**
     * （值形如 `Basic base64("pc:账号:token")`）—— 与个人网盘接口要的完全一致。
     * 所以不需要任何「切 PC UA 补 Cookie」的旁门左道，老老实实走手机版登录即可。
     */
    /**
     * ⚠️ 必须带 `#/login` —— 手机版是 **hash 路由**，裸 `/m/` 只是 SPA 入口壳，
     *    渲染出来的是「未登录首页」（所以之前用户点「进入中国移动云盘」后压根没到登录页，
     *    自然也就没有登录态）。上游 YunX 用的同样是 `/m/#/login`。
     *    实测：`/m/main` 404、`/m/#/main` 200 → 确认是 hash 模式。
     */
    const val MOBILE_LOGIN_URL = "https://yun.139.com/m/#/login"

    /** 主机名（用于 URL → 内部导航的映射） */
    const val HOST = "yun.139.com"

    /**
     * 网页版**文件列表页**（从 `app.b95b950e.js` 的 Vue 路由表里挖出来的）。
     *
     * 为什么需要它：手机版首页 `/m/` 的「进入中国移动云盘」按钮跳的是
     * `mcloud://main/tab?params=...&pullapp=...` —— 那是**中国移动云盘 App 的私有 scheme**
     * （`pullapp` 就是「拉起 App」的参数）。WebView 收到未知 scheme 会直接渲染
     * `net::ERR_UNKNOWN_URL_SCHEME` 错误页，整个登录流程就卡死在那里了（用户实测截图）。
     *
     * ⚠️⚠️ 这个地址踩过两次坑，两个点都不能想当然：
     *
     *  1. **路由名不是 `main/tab`** —— 深链里的 `main/tab` 中 `tab` 是 **App 内部页面标识**，
     *     网页版路由表里根本没有这一段（表里是 `path:"/main"`）。
     *  2. **这是 hash 路由，不能写 `/m/main`** —— 手机版用的是 `vue-router` 的 hash 模式，
     *     路径必须挂在 `#` 后面。实测：
     *     ```
     *     GET https://yun.139.com/m/main     → 404 Not Found (nginx)   ← history 写法，不存在
     *     GET https://yun.139.com/m/#/main   → 200                      ← 正确
     *     ```
     *     写成 `/m/main` 就是用户截图里那个 nginx 404 页。
     */
    const val MOBILE_FILES_PATH = "/m/#/main"

    /** 139 App 的私有 scheme（自己家的，用于「拉起 App」） */
    const val APP_SCHEME_MCLOUD = "mcloud://"

    /** 139 App 的另一个私有 scheme（安卓端 `androidAppUrl: "hecaiyun://launch"`） */
    const val APP_SCHEME_HECAIYUN = "hecaiyun://"

    /**
     * 把网页里的**私有 scheme 深链**翻译成能在 WebView 里打开的网页版地址。
     *
     * 已知会被点到的（取自手机版首页 JS）：
     *  - `mcloud://main/tab?params=<base64>&pullapp=<base64>` → 网页版 `/m/main`
     *  - `hecaiyun://launch...` → 网页版 `/m/main`
     *  - 其它任何 `mcloud://` / `hecaiyun://` 前缀 → 退回 `/m/` 首页
     * 其它 scheme（`alipays://`、`weixin://` 等）返回 null，交给调用方按「打不开」处理。
     *
     * @param backUrl 当前页面地址 `https://yun.139.com/m/`，用来补成同域的完整地址
     */
    fun rewriteDeepLink(url: String?, backUrl: String = MOBILE_LOGIN_URL): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase()
        if (!lower.startsWith(APP_SCHEME_MCLOUD) && !lower.startsWith(APP_SCHEME_HECAIYUN)) return null
        // 取同源前缀，如 "https://yun.139.com"（拿不到就退回默认 host）
        val schemeEnd = backUrl.indexOf("://")
        val origin = if (schemeEnd > 0) {
            val pathStart = backUrl.indexOf('/', schemeEnd + 3)
            if (pathStart > 0) backUrl.substring(0, pathStart) else backUrl
        } else {
            "https://$HOST"
        }
        // 深链的路径段一律只用来做「是不是要去盘列表」的判断，不做拼接 ——
        // App 内部标识（tab / launch 等）和网页路由名不是一回事，拼进去必 404。
        val path = url.substringAfter("://", "").substringBefore('?').trim('/').lowercase()
        // 「去盘列表」的几种已知写法（main / main/tab / launch / index）
        val goesToFiles = path.isEmpty() ||
            path.startsWith("main") ||
            path.startsWith("launch") ||
            path.startsWith("index") ||
            path.startsWith("tab")
        return if (goesToFiles) origin + MOBILE_FILES_PATH else origin + "/m/"
    }

    /** 提取 Cookie 的主域名（fast login 核心：Os_SSo_Sid + RMKEY 在此域） */
    const val COOKIE_DOMAIN = "https://mail.10086.cn"

    /** 备用 Cookie 域名（authorization / ud_id 等在此域，网页版直接给） */
    const val COOKIE_DOMAIN_BACKUP = "https://yun.139.com"

    /** 分享专用 host（§9/§12：share-kd-njs.yun.139.com） */
    const val SHARE_BASE = "https://share-kd-njs.yun.139.com"

    /** 分享列目录（§7/§15 share 类型；7.13+ 请求/响应加密，§14） */
    const val SHARE_LIST_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/getOutLinkInfoV6"

    /** 分享取直链（§8/§15 share 类型；7.13+ 请求/响应加密，§14） */
    const val SHARE_LINK_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/dlFromOutLinkV3"

    /** 分享标题信息（getOutLinkGeneral → outLinkGeneral[].lkName） */
    const val SHARE_GENERAL_URL = "$SHARE_BASE/yun-share/richlifeApp/devapp/IOutLink/getOutLinkGeneral"

    /** §14 分享接口 AES-CBC 固定密钥（16 字节，所有账号共用） */
    const val SHARE_AES_KEY = "PVGDwmcvfs1uV3d1"

    // ---------- 个人网盘管理（§1：明文 JSON，无 Cookie；host personal-kd-njs） ----------

    /** 个人网盘管理 host（明文 JSON + Authorization + mcloud-sign） */
    const val CLOUD_BASE = "https://personal-kd-njs.yun.139.com"

    // —— 渠道/上下文头（《139网盘管理认证失败修复》：缺失 → 04000005 认证失败；值来自成功抓包写死）——

    /** 渠道 source / app-channel / huawei-channelSrc（三者同值） */
    const val YUN_CHANNEL_SOURCE = "10000034"

    /** mcloud-version */
    const val MCLOUD_VERSION = "7.17.9"

    /** mcloud-client */
    const val MCLOUD_CLIENT = "10701"

    /** mcloud-channel */
    const val MCLOUD_CHANNEL = "1000101"

    /** x-yun-module-type */
    const val YUN_MODULE_TYPE = "100"

    /** x-m4c-src */
    const val M4C_SRC = "10002"

    /** x-m4c-caller */
    const val M4C_CALLER = "PC"

    /** X-Deviceinfo */
    const val X_DEVICEINFO = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||"

    /** x-yun-client-info */
    const val X_CLIENT_INFO = "||9|7.17.9|chrome|116.0.0.0|2cdaf7ada9e353c70eba99092e177991||windows 10||zh-CN|||dW5kZWZpbmVk||"

    /** 列目录（可加 type:"folder" 仅列文件夹） */
    const val FILE_LIST_URL = "$CLOUD_BASE/hcy/file/list"

    /**
     * 新建目录 / 上传预创建（同一个端点，靠 `type` 区分：`folder` = 建目录、`file` = 上传；
     * 文档 §3.13「新建目录 / 上传预创建 · POST $cloudBase/file/create」）
     */
    const val FILE_CREATE_URL = "$CLOUD_BASE/hcy/file/create"

    /** 重命名 */
    const val FILE_UPDATE_URL = "$CLOUD_BASE/hcy/file/update"

    /** 移动（异步，返回 taskId） */
    const val BATCH_MOVE_URL = "$CLOUD_BASE/hcy/file/batchMove"

    /** 删除（异步移入回收站，返回 taskId） */
    const val BATCH_TRASH_URL = "$CLOUD_BASE/hcy/recyclebin/batchTrash"

    /** 下载直链（OBS 预签名，900s 有效） */
    const val DOWNLOAD_URL = "$CLOUD_BASE/hcy/file/getDownloadUrl"

    /** 异步任务轮询 */
    const val TASK_GET_URL = "$CLOUD_BASE/hcy/task/get"

    /** 创建分享（yun.139.com orchestration，需 Cookie + mcloud-skey + Authorization） */
    const val OUTLINK_CREATE_URL =
        "https://yun.139.com/orchestration/personalCloud-rebuild/outlink/v1.0/getOutLink"

    /** 转存：创建任务（share host，AES 加密） */
    const val TRANSFER_CREATE_URL =
        "$SHARE_BASE/yun-share/richlifeApp/devapp/IBatchOprTask/createOuterLinkBatchOprTask"

    /** 转存：查询结果（share host，AES 加密） */
    const val TRANSFER_QUERY_URL =
        "$SHARE_BASE/yun-share/richlifeApp/devapp/IBatchOprTask/queryBatchOprTaskDetail"

    /** 分享接口必带设备/渠道上下文头（设备头修复文档 §3：缺任一即 9530） */
    const val SHARE_X_DEVICEINFO = "||3|12.27.0|||||chrome 150.0.0.0|360X444|zh-cn|||"
    const val SHARE_X_HUAWEI_CHANNELSRC = "10245500"
    const val SHARE_X_MM_SOURCE = "0002"

    /** 分享接口 User-Agent（必须浏览器/WebView UA，不能用 okhttp/4.x，设备头修复文档 §3.1） */
    const val SHARE_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/150.0.0.0 Mobile Safari/537.36"

    /** PC 桌面 UA（对齐文档 §9 请求头的 chrome/120.0.0.0 windows10；WebView 需桌面版页面才会下发 authorization） */
    const val PC_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    /** 快速登录必须同时存在的关键字段（路径 A，§3.5.3） */
    private val REQUIRED_FAST_KEYS = setOf("Os_SSo_Sid", "RMKEY")

    /** 尽量保留的字段（§3.5.3 推荐 + 网页版实际字段，按重要性排序） */
    private val KEEP_KEYS = setOf(
        // 路径 A：fast login 核心
        "Os_SSo_Sid", "RMKEY", "UserData", "Login_UserNumber",
        "_139_index_isLoginType", "UUIDToken", "JSESSIONID",
        "areaCode8011", "provCode8011",
        // 路径 B：网页版直接给出的登录态（authorization 可直接作为请求头）
        "authorization", "auth_token", "token", "ud_id",
        // 账号信息
        "ORCHES-I-ACCOUNT-SIMPLIFY", "ORCHES-I-ACCOUNT-ENCRYPT", "nation_code",
        // 其他会话/埋点
        "platform", "cutover_status", "isUserDomainError", "a_k", "skey", "WT_FPC",
        "hecaiyun_stay_url", "hecaiyun_stay_time",
        "hecaiyundata2021jssdkcross", "sajssdk_2015_cross_new_user"
    )

    /**
     * 从 CookieManager 提取 139 登录态 Cookie：
     * mail.10086.cn 优先，yun.139.com 兜底，只收 KEEP_KEYS 中的字段，拼成 "k=v; ..."。
     * @param getCookie CookieManager.getCookie(domain) 的适配（便于测试）
     */
    fun extractCookies(getCookie: (String) -> String?): String {
        val out = linkedMapOf<String, String>()
        val domains = listOf(COOKIE_DOMAIN, COOKIE_DOMAIN_BACKUP)
        for (domain in domains) {
            val raw = getCookie(domain) ?: continue
            for (kv in raw.split(";")) {
                val kv2 = kv.trim()
                val eq = kv2.indexOf('=')
                if (eq <= 0) continue
                val k = kv2.substring(0, eq)
                val v = kv2.substring(eq + 1)
                if (k in KEEP_KEYS && k !in out) out[k] = v
            }
        }
        return out.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }


    /**
     * 关键字段是否齐全（两种形式任一成立即视为有效登录态）：
     *  - 路径 A：Os_SSo_Sid + RMKEY 同时存在且非空；
     *  - 路径 B：authorization 存在且非空（且能解出真实账号）。
     *
     * ⚠️ 路径 B 为什么要额外要求「能解出账号」：139 的**个人网盘解析接口必须带完整手机号**
     * （见 [extractAccountFull]），只有一个光秃秃的 authorization 根本调不通；
     * 而登录**中间态**恰好就是「authorization 已写入、账号字段还没到」——
     * 只判 authorization 会把中间态误认成已登录（用户反馈：登录后网页直接退出了）。
     */
    fun isValidCookie(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        // 路径 B：网页版直接给的 authorization（照抄上游 YunX，不再额外要求解出账号 ——
        // 那层额外校验会把「authorization 有效但账号字段写法变了」的好登录态误判成无效）
        if (cookie.split(";").any {
                val kv = it.trim()
                kv.startsWith("authorization=") && kv.length > "authorization=".length
            }
        ) return true
        // 路径 A：fast login 双字段
        return REQUIRED_FAST_KEYS.all { key ->
            cookie.split(";").any {
                val kv = it.trim()
                kv.startsWith("$key=") && kv.length > key.length + 1
            }
        }
    }

    /**
     * Cookie 里有没有拿到 authorization。
     *
     * 单独抽出来是因为：个人网盘接口（列目录 / 取直链 / 空间）**只认这一个凭证**，
     * 而 `isValidCookie` 认的「路径 A（Os_SSo_Sid + RMKEY）」是不带它的 ——
     * 所以「预检 + 落库」两处都要在 `isValidCookie` 之外再单独卡一道这个。
     */
    fun hasAuthorization(cookie: String?): Boolean = !extractAuthorization(cookie).isNullOrBlank()

    // ------------------------------------------------------------------ 登录态自愈 + 诊断

    /** 从 Cookie 里按名取值（兼容 CookieManager 给值加引号的情况） */
    private fun cookieValue(cookie: String, name: String): String? {
        for (kv in cookie.split(";")) {
            val kv2 = kv.trim()
            if (!kv2.startsWith("$name=", ignoreCase = true)) continue
            val v = kv2.substringAfter('=').trim().trim('"')
            if (v.isNotBlank()) return v
        }
        return null
    }

    /**
     * 给一份「凭据」补齐 139 个人网盘接口唯一认的 `authorization`。
     *
     * 老存档很可能只有网页那一堆会话 Cookie、偏偏缺 `authorization`（早年版本就是这么存下来的），
     * 直接拿去调接口必然抛「登录态缺少 authorization」。这里按优先级补齐，能让多少算多少：
     *
     *  1. 串里已经有 `authorization=` → 原样返回（正常路径，零开销）；
     *  2. 有 `auth_token`（或 `token`）+ 能解出账号 → 现场算出
     *     `Basic base64("pc:账号:token")` 追加进去 —— 这正是手机版登录页
     *     `enCodeToken(account, token, "pc")` 的算法，纯本地运算无需网络；
     *  3. 都没有 → 原样返回（[describeMissing] 会告诉用户该补什么）。
     */
    fun ensureAuthorization(cookie: String, storedAuthorization: String = ""): String {
        if (cookie.isBlank()) {
            val a = storedAuthorization.trim()
            return if (a.isBlank()) cookie else "authorization=$a"
        }
        if (hasAuthorization(cookie)) return cookie

        val account = extractAccountFull(cookie)
        val token = cookieValue(cookie, "auth_token") ?: cookieValue(cookie, "token")
        if (!account.isNullOrBlank() && !token.isNullOrBlank()) {
            val auth = runCatching {
                "Basic " + Base64.encodeToString(
                    "pc:$account:$token".toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP,
                )
            }.getOrNull()
            if (!auth.isNullOrBlank()) return "$cookie; authorization=$auth"
        }

        val a = storedAuthorization.trim().trim('"')
        if (a.isNotBlank()) return "$cookie; authorization=$a"
        return cookie
    }

    // ------------------------------------------------------------------ 登录态深度抓取（WebView 内部）

    /**
     * 上一次落库失败时挖到的线索（给界面当失败提示用），用完就清。
     *
     * 存在这里而不是走返回值，是因为「抓凭证」是 spec 里的 lambda，返回值被定成了凭证串；
     * 诊断信息是旁路信息，走线程安全的可空变量最省事。
     */
    @Volatile
    var lastProbe: String? = null

    private fun putPairs(raw: String, out: LinkedHashMap<String, String>) {
        for (kv in raw.split(";")) {
            val kv2 = kv.trim()
            val eq = kv2.indexOf('=')
            if (eq <= 0) continue
            val k = kv2.substring(0, eq).trim()
            val v = kv2.substring(eq + 1).trim().trim('"')
            if (v.isBlank()) continue
            if (out[k].isNullOrBlank()) out[k] = v
        }
    }

    /** 从一段文本里抠出 authorization / token：兼容「裸值」「JSON」「被转义过的嵌套 JSON」 */
    private fun extractAuthFromText(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        // 1) 整个值就是 Basic xxx
        if (t.startsWith("Basic", ignoreCase = true)) return t
        // 嵌套 JSON 常被转义成 \"authorization\":\"Basic xxx\"，先还原引号再匹配
        val unescaped = t.replace("\\\"", "\"").replace("\\/", "/")
        val value = Regex(
            "\"(authorization|Authorization|authToken|auth_token|accessToken|token)\"\\s*:\\s*\"([^\"]{8,})\"",
        ).find(unescaped)?.groupValues?.getOrNull(2)
        if (!value.isNullOrBlank()) {
            return if (value.startsWith("Basic", ignoreCase = true)) value else "Basic $value"
        }
        // 2) 文本里散落的 Basic xxx
        val loose = Regex("Basic[ \\t]+[A-Za-z0-9+/=]{20,}").find(unescaped)?.value
        if (!loose.isNullOrBlank()) return loose
        return null
    }

    /**
     * 把「CookieManager 那一份」和「网页内部（document.cookie + localStorage + sessionStorage）
     * 挖出来的一份」合并成最终要落库的 Cookie 串。
     *
     * 为什么必须挖网页内部：139 手机版的 `authorization` 未必会落进 Set-Cookie
     * （也可能是别的域、别的 path，或者干脆只写进了 storage），
     * 上游 YunX 只做 `CookieManager.getCookie()` 那一步，拿不到就只能让用户去电脑上复制。
     *
     * @param jsDump 网页内部抓出来的扁平 JSON（键形如 `__dc` / `ls:xxx` / `ss:xxx`）
     * @param cookieManagerCookie [extractCookies] 的结果
     */
    fun buildFromSources(jsDump: String, cookieManagerCookie: String): String {
        val pairs = linkedMapOf<String, String>()
        putPairs(cookieManagerCookie, pairs)
        lastProbe = null

        val dump = runCatching { org.json.JSONObject(jsDump) }.getOrNull()
        val storageKeys = mutableListOf<String>()
        if (dump != null) {
            // document.cookie：可能比 CookieManager 多（跨域 / path 不同的那份）
            dump.optString("__dc")?.let { putPairs(it, pairs) }

            // localStorage / sessionStorage 里找 authorization
            val keys = dump.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == "__dc") continue
                val v = dump.optString(k)
                if (v.isNullOrBlank()) continue
                storageKeys += k.removePrefix("ls:").removePrefix("ss:")
                val auth = extractAuthFromText(v)
                if (!auth.isNullOrBlank()) {
                    pairs["authorization"] = auth.replace("\\s+".toRegex(), " ").trim()
                    break
                }
            }
        }

        val merged = pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val result = ensureAuthorization(merged)

        // 留线索：把网页里实际存在哪些 storage 键告诉界面，下次失败就有得看
        if (storageKeys.isNotEmpty()) {
            lastProbe = "网页内可读到的存储键：${storageKeys.take(12).joinToString("、")}" +
                if (storageKeys.size > 12) " 等 ${storageKeys.size} 个" else ""
        }
        return result
    }

    /**
     * 缺 `authorization` 时给一句能照着操作的话，并把当前存档里到底有哪些字段摊开。
     * 有 `authorization` 时返回 null（没问题就别打扰用户）。
     */
    fun describeMissing(cookie: String): String? {
        if (hasAuthorization(cookie)) return null
        val present = buildList {
            for (k in listOf("Os_SSo_Sid", "RMKEY", "auth_token", "token", "ORCHES-I-ACCOUNT-ENCRYPT", "Login_UserNumber")) {
                if (cookieValue(cookie, k) != null) add(k)
            }
        }
        val detail = if (present.isEmpty()) {
            "当前存档里一个关键字段都没有（存档可能已损坏）"
        } else {
            "当前存档有：${present.joinToString("、")}"
        }
        return "登录态缺 authorization（139 网盘接口只认这一个凭证）。$detail。" +
            "请在电脑浏览器登录 yun.139.com → F12 → Application/Cookie 里复制 authorization 的值，" +
            "回到本页用右上角「粘贴」图标填入（也可以先退出登录再重新登录一次）。"
    }

    /** 提取 authorization（§3.5.5，形如 "Basic cGM6..."）；没有返回 null */
    fun extractAuthorization(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        for (kv in cookie.split(";")) {
            val kv2 = kv.trim()
            if (kv2.startsWith("authorization=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    /**
     * 智能解析用户粘贴的内容，抽成规范的 `k=v; k=v` Cookie 串。
     *
     * 139 的 authorization 在真机上极难自动抓（见 [MOBILE_LOGIN_URL] 的说明），
     * 官方支持的做法是让用户从电脑浏览器复制过来。但用户实际会粘进来的东西五花八门，
     * 直接当 Cookie 存只会失败。这里统一容错：
     *
     *  1. **完整 Cookie 串** —— `authorization=Basic xxx; skey=yyy; ...`（最理想，原样规范化）
     *  2. **裸 authorization 值** —— `Basic cGM6...` 或只有 `cGM6...`（alist / OpenList /
     *     cloud139 CLI 的官方文档就是这么让用户复制的）→ 补成 `authorization=<值>`
     *  3. **JSON** —— `{"authorization":"Basic ..."}`（从抓包工具 / 插件导出）
     *  4. **cURL / 请求头粘贴** —— 含 `authorization: Basic ...` 行（F12 → Copy as cURL）
     *
     * 都识别不出来就原样返回，交给上层预检拒绝。
     */
    fun normalizeCookieInput(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return text

        // 1) JSON：{"authorization":"Basic ..."} 或 {"cookie":"a=b; c=d"}
        if (text.startsWith("{") && text.endsWith("}")) {
            for (key in listOf("authorization", "Authorization", "cookie", "Cookie", "token")) {
                val v = jsonStringValue(text, key)
                if (!v.isNullOrBlank()) {
                    return if (key.equals("cookie", true) || key.equals("token", true)) {
                        normalizeCookieInput(v)
                    } else {
                        toAuthorizationCookie(v)
                    }
                }
            }
        }

        // 2) 已经是 `k=v` 形式的 Cookie 串（含 authorization= 或其它已知字段）
        if (text.contains("=") && !text.startsWith("Basic ") && !looksLikeBase64Token(text)) {
            val pairs = text.split(";", "\n")
                .map { it.trim().removePrefix("Cookie:").trim() }
                .filter { it.contains("=") && !it.startsWith("#") }
            if (pairs.any { it.substringBefore('=').trim().equals("authorization", true) }) {
                return pairs.joinToString("; ")
            }
        }

        // 3) cURL / 请求头：抓 `authorization: xxx` 那一行
        val headerLine = text.lineSequence()
            .map { it.trim().removePrefix("-H").trim().trim('\'', '"') }
            .firstOrNull { it.startsWith("authorization:", true) || it.startsWith("authorization ", true) }
        if (headerLine != null) {
            val v = headerLine.substringAfter(':').trim()
            if (v.isNotBlank()) return toAuthorizationCookie(v)
        }

        // 4) 裸值：`Basic xxx` / 裸 base64 / 裸 uuid 串
        return toAuthorizationCookie(text)
    }

    /** 把「值」补成 `authorization=<值>`；缺 `Basic ` 前缀时按需补上 */
    private fun toAuthorizationCookie(value: String): String {
        val v = value.trim().trim('\'', '"').removeSurrounding("Bearer ").trim()
        val normalized = when {
            v.startsWith("Basic ", ignoreCase = true) -> v
            // 标准形态 Basic base64("pc:账号:authToken")，解码后含两个冒号 → 补 Basic
            isPcAuthToken(v) -> "Basic $v"
            else -> v
        }
        return "authorization=$normalized"
    }

    /** 形如 base64("pc:账号:authToken")：base64 解开后恰好是「pc:xxx:yyy」 */
    private fun isPcAuthToken(value: String): Boolean = runCatching {
        val decoded = String(Base64.decode(value, Base64.DEFAULT), StandardCharsets.UTF_8)
        decoded.startsWith("pc:") && decoded.count { it == ':' } >= 2
    }.getOrDefault(false)

    /** 长得像一团裸 token（无 `=`、较长、base64 字符集）→ 不是 Cookie 串 */
    private fun looksLikeBase64Token(text: String): Boolean =
        !text.contains(";") && !text.contains("=") && text.length >= 20

    /** 极简 JSON 取值：只处理本项目用得到的「单层、字符串值」场景 */
    private fun jsonStringValue(json: String, key: String): String? = runCatching {
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.getOrNull(1)
    }.getOrNull()

    /**
     * 从 Cookie 提取完整账号（解析接口用，必须完整手机号）：
     * 优先 ORCHES-I-ACCOUNT-ENCRYPT（base64 解码）→ authorization 解码 → Login_UserNumber。
     * 拿不到返回 null。
     */
    fun extractAccountFull(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        // 1) ORCHES-I-ACCOUNT-ENCRYPT：base64 手机号
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-ENCRYPT=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) {
                    val decoded = runCatching {
                        String(Base64.decode(v, Base64.DEFAULT), StandardCharsets.UTF_8)
                    }.getOrNull()
                    if (!decoded.isNullOrBlank()) return decoded
                }
            }
        }
        // 2) authorization："Basic base64(pc:账号:authToken)"
        extractAuthorization(cookie)?.let { auth ->
            val account = runCatching {
                val b64 = auth.removePrefix("Basic").trim()
                String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
                    .split(":").getOrNull(1)
            }.getOrNull()
            if (!account.isNullOrBlank()) return account
        }
        // 3) Login_UserNumber：手机号/账号
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("Login_UserNumber=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    /**
     * 从 Cookie 提取账号昵称（优先脱敏手机号 → base64 手机号 → Login_UserNumber）；
     * 拿不到返回 null。
     */
    fun extractAccount(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        // 1) ORCHES-I-ACCOUNT-SIMPLIFY：脱敏手机号，形如 177****8634
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-SIMPLIFY=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        // 2) ORCHES-I-ACCOUNT-ENCRYPT：base64 手机号
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("ORCHES-I-ACCOUNT-ENCRYPT=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) {
                    return runCatching {
                        String(Base64.decode(v, Base64.DEFAULT), StandardCharsets.UTF_8)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
                }
            }
        }
        // 3) Login_UserNumber：手机号/账号
        cookie.split(";").forEach { kv ->
            val kv2 = kv.trim()
            if (kv2.startsWith("Login_UserNumber=")) {
                val v = kv2.substringAfter('=')
                if (v.isNotBlank()) return v
            }
        }
        return null
    }
}