package com.profalikotb.airpods.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.profalikotb.airpods.core.BatterySnapshot
import com.profalikotb.airpods.core.OwnershipFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore("airpods")

enum class Lang { SYSTEM, EN, AR }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppState(
    val selectedAddress: String? = null,
    val selectedName: String? = null,
    /** Model ID locked from the first accepted advert while connected; null until then. */
    val lockedModelId: Int? = null,
    val snapshot: BatterySnapshot? = null,
    val connected: Boolean = false,
    val connectionChangedAt: Long = 0,
    val lastAmbiguousAt: Long = 0,
    /** Android's own headset battery (HFP, Apple AT extension); -1 = not reported. */
    val systemBattery: Int = -1,
    val systemBatteryAt: Long = 0,
    val lang: Lang = Lang.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lowBatteryNotif: Boolean = true,
    val connectionNotif: Boolean = false,
    val lowThreshold: Int = 20,
    val rssiThreshold: Int = OwnershipFilter.DEFAULT_RSSI_THRESHOLD,
    val onboardingDone: Boolean = false,
    /** "SINGLE=NEXT;DOUBLE=…" — commands actually observed by the gesture probe. */
    val probeResults: Map<String, String> = emptyMap(),
    val alerterState: String = "",
)

class Prefs private constructor(private val ctx: Context) {
    private object K {
        val addr = stringPreferencesKey("addr"); val name = stringPreferencesKey("name")
        val model = intPreferencesKey("model"); val snap = stringPreferencesKey("snap")
        val conn = booleanPreferencesKey("conn"); val connAt = longPreferencesKey("connAt")
        val amb = longPreferencesKey("amb"); val sys = intPreferencesKey("sys"); val sysAt = longPreferencesKey("sysAt")
        val lang = stringPreferencesKey("lang"); val theme = stringPreferencesKey("theme")
        val nLow = booleanPreferencesKey("nLow"); val nConn = booleanPreferencesKey("nConn")
        val thr = intPreferencesKey("thr"); val rssi = intPreferencesKey("rssi")
        val onb = booleanPreferencesKey("onb"); val probe = stringPreferencesKey("probe")
        val alert = stringPreferencesKey("alert")
    }

    val state: Flow<AppState> = ctx.store.data.map { p ->
        AppState(
            selectedAddress = p[K.addr], selectedName = p[K.name], lockedModelId = p[K.model],
            snapshot = BatterySnapshot.decode(p[K.snap]), connected = p[K.conn] ?: false,
            connectionChangedAt = p[K.connAt] ?: 0, lastAmbiguousAt = p[K.amb] ?: 0,
            systemBattery = p[K.sys] ?: -1, systemBatteryAt = p[K.sysAt] ?: 0,
            lang = p[K.lang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.SYSTEM,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            lowBatteryNotif = p[K.nLow] ?: true, connectionNotif = p[K.nConn] ?: false,
            lowThreshold = p[K.thr] ?: 20, rssiThreshold = p[K.rssi] ?: OwnershipFilter.DEFAULT_RSSI_THRESHOLD,
            onboardingDone = p[K.onb] ?: false,
            probeResults = p[K.probe].orEmpty().split(";").mapNotNull { e ->
                e.split("=").takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
            alerterState = p[K.alert].orEmpty(),
        )
    }

    suspend fun current(): AppState = state.first()

    suspend fun selectDevice(address: String, name: String?) = ctx.store.edit {
        if (it[K.addr] != address) { it.remove(K.model); it.remove(K.snap); it.remove(K.probe); it.remove(K.alert); it.remove(K.sys) }
        it[K.addr] = address; if (name != null) it[K.name] = name else it.remove(K.name)
    }
    suspend fun clearDevice() = ctx.store.edit {
        it.remove(K.addr); it.remove(K.name); it.remove(K.model); it.remove(K.snap)
        it.remove(K.probe); it.remove(K.alert); it.remove(K.sys); it[K.conn] = false
    }
    suspend fun saveSnapshot(s: BatterySnapshot, lockModel: Boolean) = ctx.store.edit {
        it[K.snap] = s.encode(); if (lockModel && it[K.model] == null) it[K.model] = s.modelId
    }
    suspend fun unlockModel() = ctx.store.edit { it.remove(K.model) }
    suspend fun setConnected(c: Boolean, at: Long) = ctx.store.edit { it[K.conn] = c; it[K.connAt] = at }
    suspend fun markAmbiguous(at: Long) = ctx.store.edit { it[K.amb] = at }
    suspend fun setSystemBattery(v: Int, at: Long) = ctx.store.edit { it[K.sys] = v; it[K.sysAt] = at }
    suspend fun setLang(l: Lang) = ctx.store.edit { it[K.lang] = l.name }
    suspend fun setTheme(t: ThemeMode) = ctx.store.edit { it[K.theme] = t.name }
    suspend fun setLowNotif(b: Boolean) = ctx.store.edit { it[K.nLow] = b }
    suspend fun setConnNotif(b: Boolean) = ctx.store.edit { it[K.nConn] = b }
    suspend fun setThreshold(v: Int) = ctx.store.edit { it[K.thr] = v.coerceIn(10, 50) }
    suspend fun setRssi(v: Int) = ctx.store.edit { it[K.rssi] = v.coerceIn(-85, -40) }
    suspend fun setOnboarded() = ctx.store.edit { it[K.onb] = true }
    suspend fun setAlerterState(s: String) = ctx.store.edit { it[K.alert] = s }
    suspend fun saveProbe(results: Map<String, String>) = ctx.store.edit {
        it[K.probe] = results.entries.joinToString(";") { e -> "${e.key}=${e.value}" }
    }

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs = inst ?: synchronized(this) { inst ?: Prefs(ctx.applicationContext).also { inst = it } }
    }
}
