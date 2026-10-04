package org.linbaogu.romhub.ui.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 网页加载时的顶部细进度条。
 *
 * 直接用 Material3 的不定态进度条 —— 登录页本身是网页壳子，不需要跟 Miuix 主题严格对齐，
 * 少引一个动效依赖就少一处会出问题的地方。
 */
@Composable
fun WebLoadingBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(modifier = modifier.fillMaxWidth())
}
