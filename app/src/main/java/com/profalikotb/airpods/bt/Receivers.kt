package com.profalikotb.airpods.bt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.profalikotb.airpods.core.ConnectionTracker.Event
import com.profalikotb.airpods.core.ConnectionTracker.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** App-wide scope for receivers using goAsync(); every job is bounded to < 9 s. */
internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { withTimeoutOrNull(8_500) { block() } }
        catch (t: Throwable) { Log.w("AirPodsRx", "receiver job failed", t) }
        finally { pending.finish() }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcel(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val dev: BluetoothDevice? = intent.parcel(BluetoothDevice.EXTRA_DEVICE)
        val event: Event? = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> dev?.let { Event.Up(it.address, Source.ACL) }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> dev?.let { Event.Down(it.address, Source.ACL) }
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" -> {
                val src = if (intent.action!!.contains("a2dp")) Source.A2DP else Source.HEADSET
                when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                    BluetoothProfile.STATE_CONNECTED -> dev?.let { Event.Up(it.address, src) }
                    BluetoothProfile.STATE_DISCONNECTED -> dev?.let { Event.Down(it.address, src) }
                    else -> return
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> Event.BluetoothOff
                else -> return
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> null // full resync
            else -> return
        }
        runAsync { LinkManager.onEvent(ctx.applicationContext, event) }
    }
}

class ScanResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BackgroundScan.ACTION) return
        val err = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (err != 0) { Log.w("AirPodsRx", "PI scan error $err"); return }
        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java).orEmpty()
        else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT).orEmpty()
        if (results.isEmpty()) return
        runAsync { BatteryProcessor.process(ctx.applicationContext, results) }
    }
}
