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

package org.linbaogu.romhub.hc.provision.activity

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.linbaogu.romhub.R
import org.linbaogu.romhub.hc.provision.fragment.LoginFragment

/**
 * 引导流程里的「登录」页 —— 位置在 **基础设置之后、开始使用之前**。
 *
 * 它继承 [BaseActivity]，所以外壳（返回箭头 / 预览图 / 标题 / 底部那颗蓝色按钮组）
 * 和权限页、协议页、基础设置页**完全一致**，都是 HyperCeiler 的 `provision_detail_layout`
 * 骨架 + `fan.appcompat` 的 `addGroupButtons()` 按钮面板。
 *
 * 底部两个按钮：
 *  · 「继续」→ 用填的账号密码走开发者登录；
 *  · 「跳过」→ 直接以游客身份进入。
 * 两个都成功后才 finish(RESULT_OK)，状态机接着推进到「开始使用」页。
 */
class LoginActivity : BaseActivity() {

    /**
     * ⚠ 必须在这里补 ViewTree 的 owner。
     *
     * `fan.appcompat.app.AppCompatActivity` 自己重写了 `setContentView`，
     * **没有走 androidx `ComponentActivity` 的 `initializeViewTreeOwners()`** →
     * 整棵视图树上都没有 `ViewTreeLifecycleOwner`。
     * Compose 的 `ComposeView` 一 attach 就会：
     *   1. 从自己往上走到**根节点**，
     *   2. 在根节点上调 `createLifecycleAwareWindowRecomposer()` 找 owner，
     *   3. 找不到 → `IllegalStateException: ViewTreeLifecycleOwner not found from ...provision_lyt`
     *      → 直接闪退。
     *
     * 只在 ComposeView 自身上设 owner **不管用**（第 2 步是在根节点上找的），
     * 所以这里挂在 `window.decorView` 上 —— 它覆盖整棵树，任何层级的 ComposeView 都能找到。
     */
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val decor = window.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun getLogoDrawableId(): Int = 0

    override fun getPreviewDrawable(): Int = R.drawable.provision_service_state

    override fun getTitleStringId(): Int = R.string.provision_login_title

    override fun getListDescCharSequence(): CharSequence? = null

    override fun getFragment(): Fragment = LoginFragment()

    override fun getFragmentTag(): String = LoginFragment::class.java.simpleName

    /** 表单里的「以游客身份继续」直接走这里收尾。 */
    fun finishWithOk() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onNextAminStart() {
        super.onNextAminStart()
        val f = mFragment as? LoginFragment ?: run {
            setResult(Activity.RESULT_OK); finish(); return
        }
        f.submit { err ->
            if (err == null) {
                setResult(Activity.RESULT_OK)
                finish()
            }
            // 失败时停在当前页，错误文案由表单自己显示
        }
    }

    override fun onSkipAminStart() {
        super.onSkipAminStart()
        (mFragment as? LoginFragment)?.continueAsGuest()
        setResult(Activity.RESULT_OK)
        finish()
    }
}
