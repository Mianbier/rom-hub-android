/*
 * This file is part of ROM Hub, released under the GNU Affero General Public License v3.0.
 *
 * 本文件移植 / 改写自 HyperCeiler（AGPL-3.0，Copyright (C) 2023-2026 HyperCeiler
 * Contributions），或为其等价替身实现 —— 完整来源与鸣谢见项目根目录 NOTICE.md。
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linbaogu.romhub.hc.provision

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linbaogu.romhub.core.Prefs
import org.linbaogu.romhub.core.Role
import org.linbaogu.romhub.core.Session
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.DevApplyReq

/**
 * 引导流程里「登录」这一步的桥。
 *
 * 为什么要单独一个 Kotlin 文件：`Api.devLogin` 是 suspend 函数，从 Java 的
 * Fragment 里调要自己造 Continuation，太丑；这里包一层回调式的静态方法，
 * Java 侧直接 `ProvisionLogin.dev(ctx, user, pwd, err -> {...})` 就行。
 *
 * 登录结果直接写进 [Prefs] 的会话（和主 App 用的是同一份），
 * 所以引导页登完，进主界面就已经是登录态了。
 */
object ProvisionLogin {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前是不是已经有身份了（游客也算）。 */
    @JvmStatic
    fun isLoggedIn(ctx: Context): Boolean = Prefs.session(ctx).role != Role.NONE

    /** 游客：直接写会话，不需要网络。 */
    @JvmStatic
    fun guest(ctx: Context) {
        Prefs.setSession(ctx, Session(role = Role.GUEST))
    }

    /**
     * 开发者账号登录。
     * @param cb 回调在主线程；`null` = 成功，否则是错误文案。
     */
    @JvmStatic
    fun dev(ctx: Context, username: String, password: String, cb: (String?) -> Unit) {
        val appCtx = ctx.applicationContext
        if (username.isBlank() || password.isBlank()) {
            cb("账号和密码都不能为空")
            return
        }
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    val r = Api.devLogin(appCtx, username.trim(), password)
                    if (!r.ok || r.token.isBlank()) {
                        r.error.ifBlank { "账号或密码错误，或账号还没通过审核" }
                    } else {
                        Prefs.setSession(
                            appCtx,
                            Session(
                                role = Role.DEV,
                                username = r.username.ifBlank { username.trim() },
                                token = r.token,
                            ),
                        )
                        null
                    }
                } catch (e: Exception) {
                    e.message ?: "网络异常"
                }
            }
            cb(err)
        }
    }

    /**
     * 提交**开发者账号申请**。站长要求的四样：账号（邮箱）/ 名称 / 酷安名 / 密码。
     *
     * 本地先做一遍格式校验，省得白跑一趟网络再被服务端打回来。
     * @param cb 回调在主线程；`null` = 成功，否则是错误文案。
     */
    @JvmStatic
    fun apply(
        ctx: Context,
        email: String,
        name: String,
        coolapk: String,
        password: String,
        cb: (String?) -> Unit,
    ) {
        val appCtx = ctx.applicationContext
        val mail = email.trim().lowercase()
        val err = when {
            "@" !in mail || "." !in mail.substringAfter("@") || " " in mail ->
                "账号要填邮箱，例如 you@example.com"
            name.isBlank() -> "请填写名称"
            coolapk.isBlank() -> "请填写酷安名"
            password.length < 6 -> "密码至少 6 位"
            else -> null
        }
        if (err != null) {
            cb(err)
            return
        }
        scope.launch {
            val e = withContext(Dispatchers.IO) {
                try {
                    val r = Api.devApply(
                        appCtx,
                        DevApplyReq(
                            username = mail,
                            password = password,
                            name = name.trim(),
                            coolapk = coolapk.trim(),
                        ),
                    )
                    if (r.ok) null else r.error.ifBlank { "申请失败" }
                } catch (ex: Exception) {
                    ex.message ?: "网络异常"
                }
            }
            cb(e)
        }
    }
}
