package com.profalikotb.airpods.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.profalikotb.airpods.bt.BatteryProcessor
import com.profalikotb.airpods.bt.BleScan
import com.profalikotb.airpods.bt.BondedAudio
import com.profalikotb.airpods.bt.BtEnv
import com.profalikotb.airpods.bt.LinkManager
import com.profalikotb.airpods.data.AppState
import com.profalikotb.airpods.data.Lang
import com.profalikotb.airpods.data.Prefs
import com.profalikotb.airpods.data.ThemeMode
import com.profalikotb.airpods.notify.Notifier
import com.profalikotb.airpods.probe.GestureProbe
import com.profalikotb.airpods.widget.BatteryWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class RefreshResult { NONE, ACCEPTED, AMBIGUOUS, TIMEOUT, NOT_CONNECTED, BT_OFF, NO_PERMISSION, FAILED }

data class Env(val btOn: Boolean, val btPerms: Boolean, val notifPerm: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs.get(app)
    val state: StateFlow<AppState?> = prefs.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _scanning = MutableStateFlow(false); val scanning = _scanning.asStateFlow()
    private val _result = MutableStateFlow(RefreshResult.NONE); val result = _result.asStateFlow()
    private val _env = MutableStateFlow(readEnv()); val env = _env.asStateFlow()
    private val _bonded = MutableStateFlow<List<BondedAudio>>(emptyList()); val bonded = _bonded.asStateFlow()
    /** Ticks every 20 s so "N min ago" and Live→Last-known transitions stay truthful on screen. */
    val now: StateFlow<Long> = kotlinx.coroutines.flow.flow { while (true) { emit(System.currentTimeMillis()); delay(20_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    val probe = GestureProbe(app)
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            probe.events.collect { list ->
                if (list.isEmpty()) return@collect
                val cur = prefs.current().probeResults.toMutableMap()
                list.forEach { cur[it.command.name] = it.atMs.toString() }
                prefs.saveProbe(cur)
            }
        }
    }

    private fun readEnv(): Env {
        val a = getApplication<Application>()
        return Env(BtEnv.isOn(a), BtEnv.hasBtPermissions(a), BtEnv.canNotify(a))
    }

    /** Called on every resume: permissions / Bluetooth may have changed while away. */
    fun onResume() {
        _env.value = readEnv()
        loadBonded()
        viewModelScope.launch {
            val a = getApplication<Application>()
            Notifier.createChannels(a, prefs.current().lang)
            LinkManager.onEvent(a, null)
            val st = prefs.current()
            val stale = st.snapshot == null || System.currentTimeMillis() - st.snapshot.timestampMs > 60_000
            if (st.connected && stale && _env.value.btPerms) refresh()
        }
    }

    fun loadBonded() { _bonded.value = BtEnv.bondedAudio(getApplication()) }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val a = getApplication<Application>()
            _env.value = readEnv()
            _result.value = when {
                !_env.value.btPerms -> RefreshResult.NO_PERMISSION
                !_env.value.btOn -> RefreshResult.BT_OFF
                else -> {
                    _scanning.value = true
                    LinkManager.onEvent(a, null)
                    if (!prefs.current().connected) RefreshResult.NOT_CONNECTED
                    else {
                        var sawAmbiguous = false
                        var failed = false
                        val got = withTimeoutOrNull(15_000) {
                            BleScan.results(a)
                                .map { BatteryProcessor.process(a, listOf(it)) }
                                .catch { failed = true; emit(BatteryProcessor.Outcome.NOT_READY) }
                                .first { o ->
                                    if (o == BatteryProcessor.Outcome.AMBIGUOUS) sawAmbiguous = true
                                    o == BatteryProcessor.Outcome.ACCEPTED || failed
                                }
                        }
                        when {
                            failed -> RefreshResult.FAILED
                            got == BatteryProcessor.Outcome.ACCEPTED -> RefreshResult.ACCEPTED
                            sawAmbiguous -> RefreshResult.AMBIGUOUS
                            else -> RefreshResult.TIMEOUT
                        }
                    }
                }
            }
            _scanning.value = false
        }
    }

    fun select(d: BondedAudio) = viewModelScope.launch {
        prefs.selectDevice(d.address, d.name)
        _result.value = RefreshResult.NONE
        LinkManager.onEvent(getApplication(), null)
        BatteryWidget.refresh(getApplication())
        if (prefs.current().connected) refresh()
    }
    fun forget() = viewModelScope.launch {
        prefs.clearDevice(); LinkManager.onEvent(getApplication(), null); BatteryWidget.refresh(getApplication())
    }
    fun relockModel() = viewModelScope.launch { prefs.unlockModel(); refresh() }
    fun setLang(l: Lang) = viewModelScope.launch {
        prefs.setLang(l); Notifier.createChannels(getApplication(), l); BatteryWidget.refresh(getApplication())
    }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefs.setTheme(t) }
    fun setLowNotif(b: Boolean) = viewModelScope.launch { prefs.setLowNotif(b) }
    fun setConnNotif(b: Boolean) = viewModelScope.launch {
        prefs.setConnNotif(b)
        val a = getApplication<Application>()
        if (b) Notifier.connected(a, prefs.current()) else Notifier.cancelConnected(a)
    }
    fun setThreshold(v: Int) = viewModelScope.launch { prefs.setThreshold(v) }
    fun setRssi(v: Int) = viewModelScope.launch { prefs.setRssi(v) }
    fun finishOnboarding() = viewModelScope.launch { prefs.setOnboarded() }
    fun clearProbe() = viewModelScope.launch { prefs.saveProbe(emptyMap()) }

    override fun onCleared() { probe.stop(); super.onCleared() }
}
