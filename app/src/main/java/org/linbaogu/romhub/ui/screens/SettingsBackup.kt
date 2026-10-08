package org.linbaogu.romhub.ui.screens

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * 设置备份 / 恢复 / 重置 —— 仿 HyperCeiler 设置页里的「备份 / 恢复 / 重置」三项结构性功能。
 *
 * 备份内容：本 App 自己的 SharedPreferences（`romhub_prefs`，订阅、通知开关、底栏样式、缓存等）。
 * 不包含任何敏感凭据（那些走 SecureStore，且本来就该重新登录）。
 */
object SettingsBackup {

    private const val PREF = "romhub_prefs"

    private fun dir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "rom-hub")

    private fun file(): File = File(dir(), "settings.json")

    /** 备份到 Download/rom-hub/settings.json。返回给用户看的提示。 */
    fun backup(ctx: Context): String = runCatching {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = JSONObject()
        for ((k, v) in sp.all) json.put(k, v)
        dir().mkdirs()
        file().writeText(json.toString(2), Charsets.UTF_8)
        "已备份到 Download/rom-hub/settings.json（${sp.all.size} 项）"
    }.getOrElse { "备份失败：${it.message}" }

    /** 从备份文件读回设置。返回给用户看的提示。 */
    fun restore(ctx: Context): String = runCatching {
        val f = file()
        if (!f.exists()) return "没找到备份文件：Download/rom-hub/settings.json"
        val json = JSONObject(f.readText(Charsets.UTF_8))
        val e = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        for (k in json.keys()) {
            when (val v = json.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                else -> e.putString(k, v.toString())
            }
        }
        e.apply()
        "已恢复 ${json.length()} 项设置"
    }.getOrElse { "恢复失败：${it.message}" }

    /** 重置：清空设置与订阅（不可恢复）。 */
    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        // 底栏开关状态也回到默认
        org.linbaogu.romhub.hc.HcNavState.load(ctx)
    }
}
