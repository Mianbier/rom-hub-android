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

package org.linbaogu.romhub.pan.repo

import android.webkit.CookieManager
import org.linbaogu.romhub.pan.store.C139AccountDao
import org.linbaogu.romhub.pan.store.C139AccountEntity
import org.linbaogu.romhub.pan.C139Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 139 网盘账号数据仓库：Room 持久化 + Cookie 校验。
 * 登录态 = mail.10086.cn 的 Os_SSo_Sid + RMKEY（WebView 登录后提取）。
 */
class C139AccountRepository(
    private val dao: C139AccountDao
) {

    fun observeAccount(): Flow<C139AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): C139AccountEntity? = dao.getAccount()

    /** 退出登录：清理 WebView Cookie + 清除本地记录 */
    suspend fun logoutC139() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    /**
     * 校验并落库 139 登录态（对齐上游 YunX：`isValidCookie` 通过即算登录成功）。
     *
     * 两道门槛的分工：
     *  1. **`isValidCookie`（上游同款）** —— 路径 A（Os_SSo_Sid + RMKEY）
     *     或路径 B（authorization + 能解出账号）任一成立。
     *  2. **`authorization` 必须存在**（我们比上游多的一道，是踩坑换来的）：
     *     它是 139 个人网盘接口（列目录 / 取直链 / 空间详情）的**唯一凭证**，
     *     只放行路径 A 会把「半个登录态」当成功存下来 —— 表现就是提示「登录成功」、
     *     进云盘却报「登录态缺少 authorization」（用户实测踩过）。
     *     ⚠️ 手机版登录**本来就会写**这个 Cookie（登录接口返回即
     *     `setAuthorization(..., 30天)`），所以正常登录后这道门槛不会误伤。
     *
     * 空间信息是**尽力而为**：探针成功就顺带记下容量，失败（限流 / 网络抖动 /
     * 接口改版）**不影响落库** —— 登录态本身已经由上面两道门槛保证可用了。
     */
    suspend fun saveC139Account(cookie: String): Boolean {
        if (!C139Constants.isValidCookie(cookie)) return false
        val authorization = C139Constants.extractAuthorization(cookie)
            ?.takeIf { it.isNotBlank() } ?: return false
        val quota = runCatching { org.linbaogu.romhub.pan.PanHub.c139Api().getQuota(cookie) }
            .getOrNull()
        val nickname = C139Constants.extractAccount(cookie) ?: "139用户"
        dao.upsert(
            C139AccountEntity(
                id = "c139",
                cookie = cookie,
                nickname = nickname,
                authorization = authorization,
                quotaUsed = quota?.used ?: 0L,
                quotaTotal = quota?.total ?: 0L,
                quotaUpdatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }
}