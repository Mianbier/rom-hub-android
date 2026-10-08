package org.linbaogu.romhub.pan.store

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 网盘账号的本地存储。
 *
 * 云析那边用的是 Room；这里改成「一个账号一个 JSON 文件」，原因：
 *   · 每个账号就一条记录（id 固定），SQLite 纯属杀鸡用牛刀；
 *   · 不用给 ROM Hub 引 KSP + Room 插件，构建链少两个会出问题的环节。
 * 对外保留和 Room Dao 一样的方法名（observeAccount / upsert / getAccount / clear），
 * 所以从云析搬过来的 repository 几乎不用改。
 */

// ---------------------------------------------------------------- 实体

@Serializable
data class BaiduAccountEntity(
    val id: String = "baidu",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class QuarkAccountEntity(
    val id: String = "quark",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class UCAccountEntity(
    val id: String = "uc",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Pan115AccountEntity(
    val id: String = "pan115",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Pan123AccountEntity(
    val id: String = "pan123",
    /** Bearer JWT，作为 repository 的 cookie 参数传 */
    val accessToken: String = "",
    /** 登录账号（网页登录拿不到手机号，留空） */
    val account: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class C139AccountEntity(
    val id: String = "c139",
    val cookie: String = "",
    val nickname: String = "",
    val authorization: String = "",
    /** 最近一次校验时抓到的空间信息（字节）；0 表示还没取到 */
    val quotaUsed: Long = 0L,
    val quotaTotal: Long = 0L,
    val quotaUpdatedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class XunleiAccountEntity(
    val id: String = "xunlei",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val captchaToken: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------- 通用存储

/** 单个 JSON 文件里存一个对象。写坏了下回读出来是 null，不会崩。 */
internal class JsonStore<E>(
    private val ctx: Context,
    private val name: String,
    private val ser: KSerializer<E>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(ctx.filesDir, "pan_$name.json")

    fun load(): E? = runCatching {
        if (!file.exists()) return null
        val text = file.readText()
        if (text.isBlank()) null else json.decodeFromString(ser, text)
    }.getOrNull()

    fun save(value: E) {
        runCatching {
            val tmp = File(ctx.filesDir, "pan_$name.json.tmp")
            tmp.writeText(json.encodeToString(ser, value))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}

/**
 * 各网盘账号 Dao。
 *
 * 都是「单条记录 + 状态流」：界面 collect [observeAccount]，写入走 [upsert]。
 */
class BaiduAccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "baidu_account", BaiduAccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<BaiduAccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: BaiduAccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): BaiduAccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class QuarkAccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "quark_account", QuarkAccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<QuarkAccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: QuarkAccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): QuarkAccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class UCAccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "uc_account", UCAccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<UCAccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: UCAccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): UCAccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class Pan115AccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "pan115_account", Pan115AccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<Pan115AccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: Pan115AccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): Pan115AccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class Pan123AccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "pan123_account", Pan123AccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<Pan123AccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: Pan123AccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): Pan123AccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class C139AccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "c139_account", C139AccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<C139AccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: C139AccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): C139AccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}

class XunleiAccountDao(ctx: Context) {
    private val store = JsonStore(ctx, "xunlei_account", XunleiAccountEntity.serializer())
    private val state = MutableStateFlow(store.load())
    fun observeAccount(): Flow<XunleiAccountEntity?> = state.asStateFlow()
    suspend fun upsert(account: XunleiAccountEntity) { store.save(account); state.value = account }
    suspend fun getAccount(): XunleiAccountEntity? = state.value
    suspend fun clear() { store.clear(); state.value = null }
}
