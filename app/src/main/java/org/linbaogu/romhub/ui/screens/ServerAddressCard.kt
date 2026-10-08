package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.ui.component.GhostButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 *「服务器地址」卡片。
 *
 * 服务端能跑在三个地方，网络也一直在变，所以这里让地址可见、可切换：
 *   · 手机本机（127.0.0.1:8787）—— 服务跑在手机上，任何网络都通，最稳
 *   · 局域网电脑（http://192.168.x.x:8787）—— 同一个 WiFi 下的电脑跑服务
 *   · 公网域名 —— 电脑开着隧道的时候用
 *
 * 平时不用管：请求失败会自动往下倒（见 Api.pickNextBase）；这里主要是给用户一个
 * 手动兜底和「一眼看到现在连的是谁」的地方。
 */
@Composable
fun ServerAddressCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(Api.currentBase(ctx)) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text("当前地址", fontSize = MiuixTheme.textStyles.footnote2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(2.dp))
        Text(cur, fontSize = MiuixTheme.textStyles.body1.fontSize)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy) "探测中…" else "自动探测") {
                busy = true
                scope.launch {
                    val found = withContext(Dispatchers.IO) { Api.autoDetect(ctx) }
                    cur = Api.currentBase(ctx)
                    msg = if (found != null) "已连上：$found" else "三个地址都连不上 —— 确认服务端起来了吗？"
                    busy = false
                }
            }
            GhostButton("用手机本机") {
                busy = true
                scope.launch {
                    val url = Prefs.localBases().first()
                    val ok = withContext(Dispatchers.IO) {
                        Prefs.setServerBase(ctx, url)
                        Api.probe(url)
                    }
                    cur = Api.currentBase(ctx)
                    msg = if (ok) "已切到手机本机服务：$url"
                    else "手机本机 $url 上没有服务在跑"
                    busy = false
                }
            }
            GhostButton("用公网") {
                Prefs.clearServerBase(ctx)
                cur = Api.currentBase(ctx)
                msg = "已切回公网：${Prefs.DEFAULT_BASE}"
            }
        }

        if (msg.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                msg,
                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "服务端跑在手机上就用「用手机本机」；跑在电脑上时，手机和电脑连同一个 WiFi，" +
                    "把电脑的局域网 IP（如 http://192.168.1.5:8787）填进去即可 —— 换网络后地址可能要重填。",
            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
