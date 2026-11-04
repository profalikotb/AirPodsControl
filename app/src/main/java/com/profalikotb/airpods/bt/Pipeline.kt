package com.profalikotb.airpods.bt

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.profalikotb.airpods.core.BatterySnapshot
import com.profalikotb.airpods.core.ConnectionTracker
import com.profalikotb.airpods.core.LinkState
import com.profalikotb.airpods.core.LowBatteryAlerter
import com.profalikotb.airpods.core.OwnershipFilter
import com.profalikotb.airpods.core.ProximityPairingParser
import com.profalikotb.airpods.data.Prefs
import com.profalikotb.airpods.notify.Notifier
import com.profalikotb.airpods.widget.BatteryWidget
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AirPodsPipeline"

private fun appleFilter(): ScanFilter = ScanFilter.Builder()
    .setManufacturerData(ProximityPairingParser.APPLE_COMPANY_ID, ProximityPairingParser.SCAN_FILTER_DATA, ProximityPairingParser.SCAN_FILTER_MASK)
    .build()

// ─────────────────────────── Battery processing ───────────────────────────

/** Single entry point for every scan result (foreground callback or background PendingIntent). */
object BatteryProcessor {
    enum class Outcome { ACCEPTED, AMBIGUOUS, NO_MATCH, NOT_READY }

    private val mutex = Mutex()
    private var filter: OwnershipFilter? = null
    private var filterThreshold = Int.MIN_VALUE
    private const val REWRITE_UNCHANGED_MS = 60_000L

    fun resetFilter() { filter?.reset() }

    suspend fun process(ctx: Context, results: List<ScanResult>): Outcome = mutex.withLock {
        val prefs = Prefs.get(ctx)
        val st = prefs.current()
        if (st.selectedAddress == null || !st.connected) return Outcome.NOT_READY
        if (filter == null || filterThreshold != st.rssiThreshold) { filter = OwnershipFilter(st.rssiThreshold); filterThreshold = st.rssiThreshold }
        val f = filter!!
        val now = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtime()

        var best: Pair<ScanResult, com.profalikotb.airpods.core.ProximityMessage>? = null
        var ambiguous = false
        for (r in results) {
            val msg = ProximityPairingParser.parse(r.scanRecord?.getManufacturerSpecificData(ProximityPairingParser.APPLE_COMPANY_ID)) ?: continue
            when (f.evaluate(r.device.address, r.rssi, msg.modelId, st.lockedModelId, st.connected, mono)) {
                OwnershipFilter.Decision.Accept -> if (best == null || r.rssi > best.first.rssi) best = r to msg
                OwnershipFilter.Decision.Ambiguous -> ambiguous = true
                is OwnershipFilter.Decision.Reject -> Unit
            }
        }
        if (ambiguous) {
            if (now - st.lastAmbiguousAt > 30_000) prefs.markAmbiguous(now)
            return Outcome.AMBIGUOUS
        }
        val (res, msg) = best ?: return Outcome.NO_MATCH
        val snap = BatterySnapshot.from(msg, res.rssi, now)
        val prev = st.snapshot
        val changed = !snap.sameContentAs(prev)
        if (changed || prev == null || now - prev.timestampMs > REWRITE_UNCHANGED_MS) {
            prefs.saveSnapshot(snap, lockModel = true)
            val sys = BtEnv.systemBattery(ctx, st.selectedAddress)
            if (sys >= 0) prefs.setSystemBattery(sys, now)
            BatteryWidget.refresh(ctx)
            if (changed) {
                if (st.lowBatteryNotif) {
                    val alerter = LowBatteryAlerter(thresholdPct = st.lowThreshold).apply { importState(st.alerterState) }
                    alerter.evaluate(snap).forEach { Notifier.lowBattery(ctx, it, snap, st.lang) }
                    prefs.setAlerterState(alerter.exportState())
                }
                if (st.connectionNotif) Notifier.connected(ctx, prefs.current())
            }
        }
        Outcome.ACCEPTED
    }
}

// ───────────────────────── Bounded foreground scan ─────────────────────────

object BleScan {
    /** Cold flow: scanning runs only while collected and always stops on cancellation. */
    @SuppressLint("MissingPermission")
    fun results(ctx: Context): Flow<ScanResult> = callbackFlow {
        val scanner = BtEnv.adapter(ctx)?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null || !BtEnv.canScan(ctx)) { close(IllegalStateException("scanner unavailable")); return@callbackFlow }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { trySend(result) }
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { trySend(it) } }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("scan failed $errorCode")) }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(appleFilter()), settings, cb) }.onFailure { close(it) }
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }
}

// ─────────────────── OS-managed background scan (while connected) ───────────────────

/**
 * While — and only while — the selected AirPods are connected, a hardware-filtered,
 * LOW_POWER PendingIntent scan is registered with the OS. No service, no wake-lock,
 * no polling; the OS wakes [ScanResultReceiver] when an Apple 07 19 advert is seen.
 */
object BackgroundScan {
    const val ACTION = "com.profalikotb.airpods.SCAN_RESULT"
    private var lastStart = 0L

    private fun pi(ctx: Context): PendingIntent {
        val i = Intent(ctx, ScanResultReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 7, i, flags)
    }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStart < 6_000) return // Android blocks apps starting >5 scans / 30 s
        val scanner = BtEnv.adapter(ctx)?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        if (!BtEnv.canScan(ctx)) return
        lastStart = now
        runCatching {
            scanner.stopScan(pi(ctx))
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            val rc = scanner.startScan(listOf(appleFilter()), settings, pi(ctx))
            if (rc != 0) Log.w(TAG, "background scan start rc=$rc")
        }.onFailure { Log.w(TAG, "background scan start failed", it) }
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) {
        runCatching { BtEnv.adapter(ctx)?.bluetoothLeScanner?.stopScan(pi(ctx)) }
    }
}

// ───────────────────────────── Link manager ─────────────────────────────

object LinkManager {
    private val mutex = Mutex()
    private var tracker: ConnectionTracker? = null

    /** Feed a Bluetooth event; applies side effects only on a real state change. */
    suspend fun onEvent(ctx: Context, event: ConnectionTracker.Event?) = mutex.withLock {
        val prefs = Prefs.get(ctx)
        val st = prefs.current()
        val addr = st.selectedAddress
        if (addr == null) { BackgroundScan.stop(ctx); return@withLock }
        val t = tracker?.also { it.select(addr) } ?: ConnectionTracker(addr).also { tracker = it }
        if (event == null || t.state == LinkState.DISCONNECTED) {
            // (Re)seed from an authoritative, time-bounded profile query.
            t.seed(BtEnv.connectedSources(ctx, addr))
        }
        event?.let { t.onEvent(it) }
        if (!BtEnv.hasBtPermissions(ctx)) t.onEvent(ConnectionTracker.Event.PermissionLost)
        applyState(ctx, t.state == LinkState.CONNECTED)
    }

    private suspend fun applyState(ctx: Context, connected: Boolean) {
        val prefs = Prefs.get(ctx)
        val st = prefs.current()
        if (connected) {
            BackgroundScan.start(ctx)
            if (!st.connected) {
                prefs.setConnected(true, System.currentTimeMillis())
                BatteryProcessor.resetFilter()
                val sys = BtEnv.systemBattery(ctx, st.selectedAddress!!)
                if (sys >= 0) prefs.setSystemBattery(sys, System.currentTimeMillis())
                if (st.connectionNotif) Notifier.connected(ctx, prefs.current())
                BatteryWidget.refresh(ctx)
            }
        } else {
            BackgroundScan.stop(ctx)
            if (st.connected) {
                prefs.setConnected(false, System.currentTimeMillis())
                BatteryProcessor.resetFilter()
                Notifier.cancelConnected(ctx)
                BatteryWidget.refresh(ctx)
            }
        }
    }
}
