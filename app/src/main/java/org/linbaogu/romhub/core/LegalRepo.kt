package org.linbaogu.romhub.core

import android.content.Context
import org.linbaogu.romhub.data.Api
import org.linbaogu.romhub.data.LegalInfo

/**
 * 用户协议 / 隐私政策 —— 客户端侧的统一入口。
 *
 * ## 内容从哪来
 * ```
 * 云端 KV（data/legal.json）  ← 站长在管理端改一次，所有客户端下次启动就能拿到
 *      ↓ 读不到（断网 / 源站没开）
 * 本地缓存（上次成功同步下来的）
 *      ↓ 也没有（刚装好、一次都没同步过）
 * APK 内置 assets（保底，永远读得到）
 * ```
 *
 * ## 为什么要弹窗
 * 协议内容变了，用户有权知道。做法是比对**版本号**：
 * 云端版本 > 用户「已同意」的版本 → 弹一次窗；同意过就不再打扰。
 * 所以站长改十次也只会在用户那边弹一次（除非又改了）。
 */
object LegalRepo {

    /** 协议的两份内容。 */
    enum class Doc(val scheme: String, val asset: String) {
        TERMS("legal://terms", "romhub_terms.md"),
        PRIVACY("legal://privacy", "romhub_privacy.md"),
    }

    /**
     * 同步一次云端协议，成功时写缓存并返回内容；失败返回 null（调用方走缓存/内置）。
     */
    suspend fun fetch(ctx: Context): LegalInfo? {
        val info = Api.legal(ctx) ?: return null
        // 空内容不算数：宁可继续用缓存，也不要拿一份空协议把界面刷白
        if (info.terms.isBlank() && info.privacy.isBlank()) return null
        Prefs.setLegalVersion(ctx, info.version)
        Prefs.setLegalUpdatedAt(ctx, info.updatedAt)
        Prefs.setLegalText(ctx, info.terms, info.privacy)
        return info
    }

    /**
     * 有没有「用户还没同意过的新版本」。
     * @return 需要提示时返回云端版本号，否则 null
     */
    fun pendingVersion(ctx: Context, info: LegalInfo?): Int? {
        val v = info?.version ?: Prefs.legalVersion(ctx)
        if (v <= 0) return null
        return if (v > Prefs.legalAckedVersion(ctx)) v else null
    }

    /** 用户点了「同意 / 我知道了」。 */
    fun ack(ctx: Context, version: Int) {
        Prefs.setLegalAckedVersion(ctx, version)
    }

    /** 取正文：云端缓存 → APK 内置。 */
    fun text(ctx: Context, doc: Doc): String {
        val cached = when (doc) {
            Doc.TERMS -> Prefs.legalTerms(ctx)
            Doc.PRIVACY -> Prefs.legalPrivacy(ctx)
        }
        if (cached.isNotBlank()) return cached
        return runCatching {
            ctx.assets.open(doc.asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }

    /** 当前生效的版本号（云端缓存里的）。 */
    fun version(ctx: Context): Int = Prefs.legalVersion(ctx)

    /** 最后更新时间（给「关于」页显示用）。 */
    fun updatedAt(ctx: Context): String = Prefs.legalUpdatedAt(ctx)
}
