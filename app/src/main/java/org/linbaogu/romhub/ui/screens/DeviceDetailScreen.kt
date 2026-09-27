package org.linbaogu.romhub.ui.screens

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.data.PortPackage
import org.linbaogu.romhub.data.Repo
import org.linbaogu.romhub.data.RomItem
import org.linbaogu.romhub.ui.common.ErrorHint
import org.linbaogu.romhub.ui.common.Hint
import org.linbaogu.romhub.ui.common.InfoRow
import org.linbaogu.romhub.ui.common.ListScreen
import org.linbaogu.romhub.ui.common.SectionLabel
import org.linbaogu.romhub.ui.common.StateChip
import org.linbaogu.romhub.data.Api
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceDetailScreen(
    code: String,
    bottomInnerPadding: Dp,
    onBack: () -> Unit,
    onOpenVersions: (RomItem) -> Unit,
    onOpenPort: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    var device by remember { mutableStateOf(org.linbaogu.romhub.data.DeviceItem(code = code)) }
    var roms by remember { mutableStateOf<List<RomItem>>(emptyList()) }
    var ports by remember { mutableStateOf<List<PortPackage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var subscribed by remember(code) { mutableStateOf(Prefs.isSubscribed(ctx, code)) }

    LaunchedEffect(code, reload) {
        loading = true
        error = ""
        runCatching {
            val d = Api.device(ctx, code)
            device = d.device
            roms = d.roms
            ports = runCatching { Api.ports(ctx, code).items }.getOrDefault(emptyList())
        }.onSuccess { loading = false }
            .onFailure { error = it.message ?: "加载失败"; loading = false }
    }

    ListScreen(
        title = device.displayName,
        largeTitle = device.displayName,
        subtitle = code,
        onBack = onBack,
        bottomInnerPadding = bottomInnerPadding,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeviceThumb(device, 62.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.displayName,
                                fontSize = MiuixTheme.textStyles.title4.fontSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = listOfNotNull(
                                    device.brandZh.takeIf { it.isNotBlank() },
                                    device.series.takeIf { it.isNotBlank() },
                                    code,
                                ).joinToString(" · "),
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    InfoRow("支持系统", device.supports)
                    InfoRow("Android", device.android)
                    InfoRow("最近更新", device.updatedAt)
                }
            }
        }

        item {
            Card {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("订阅更新提醒", fontSize = MiuixTheme.textStyles.body1.fontSize)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "这款机型有新的官方包或移植包时发系统通知",
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = subscribed,
                        onCheckedChange = {
                            subscribed = Prefs.toggleSubscription(ctx, code)
                        },
                    )
                }
            }
        }

        if (loading) {
            item { Hint("正在加载版本…") }
        } else if (error.isNotBlank()) {
            item { ErrorHint(error) { reload++ } }
        }

        if (ports.isNotEmpty()) {
            item { SectionLabel("社区移植包（${ports.size}）") }
            items(ports, key = { "p${it.id}" }) { p ->
                Card(onClick = { onOpenPort(p.id) }, showIndication = true) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateChip(state = "移植包")
                            if (p.portType.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    p.portType,
                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.title.ifBlank { p.fileName },
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = listOfNotNull(
                                p.author.takeIf { it.isNotBlank() }?.let { "作者 $it" },
                                p.fileSize.takeIf { it.isNotBlank() },
                                p.shortDate.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        if (roms.isNotEmpty()) {
            item { SectionLabel("官方包（按地区 × 分支）") }
            items(roms, key = { it.id }) { r ->
                Card(onClick = { onOpenVersions(r) }, showIndication = true) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StateChip(state = "", textOverride = r.regionZh.ifBlank { r.region })
                                Spacer(Modifier.width(6.dp))
                                StateChip(state = "", textOverride = r.branchZh.ifBlank { r.branch })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = r.latestVersion.ifBlank { "暂无版本号" },
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = listOfNotNull(
                                    r.latestDate.takeIf { it.isNotBlank() },
                                    if (r.versionCount > 0) "${r.versionCount} 个版本" else null,
                                ).joinToString(" · "),
                                fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Text(
                            "›",
                            fontSize = MiuixTheme.textStyles.title3.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        if (!loading && error.isBlank() && roms.isEmpty() && ports.isEmpty()) {
            item { Hint("这款机型还没有收录版本") }
        }
    }
}
