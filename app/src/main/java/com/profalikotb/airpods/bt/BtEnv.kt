package com.profalikotb.airpods.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.profalikotb.airpods.core.ConnectionTracker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class BondedAudio(val address: String, val name: String, val looksLikeApple: Boolean)

/** All Bluetooth environment queries, each permission-checked so nothing throws SecurityException. */
object BtEnv {
    private const val AAP_UUID = "74ec2172-0bad-4d01-8f77-997b2be0722a"

    fun runtimePermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
        else add(Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()

    fun notificationPermission(): String? = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun hasBtPermissions(ctx: Context) = runtimePermissions().all { has(ctx, it) }
    fun canConnect(ctx: Context) = Build.VERSION.SDK_INT < 31 || has(ctx, Manifest.permission.BLUETOOTH_CONNECT)
    fun canScan(ctx: Context) = if (Build.VERSION.SDK_INT >= 31) has(ctx, Manifest.permission.BLUETOOTH_SCAN) else has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun canNotify(ctx: Context) = notificationPermission()?.let { has(ctx, it) } ?: true

    fun adapter(ctx: Context): BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    fun isOn(ctx: Context): Boolean = runCatching { adapter(ctx)?.isEnabled == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun bondedAudio(ctx: Context): List<BondedAudio> {
        if (!canConnect(ctx)) return emptyList()
        val devs = runCatching { adapter(ctx)?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        return devs.mapNotNull { d ->
            val name = runCatching { d.name }.getOrNull() ?: d.address
            val major = runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()
            val uuids = runCatching { d.uuids?.map { it.uuid.toString().lowercase() } }.getOrNull().orEmpty()
            val apple = AAP_UUID in uuids || name.contains("AirPods", true)
            val audio = major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
            if (apple || audio) BondedAudio(d.address, name, apple) else null
        }.sortedWith(compareByDescending<BondedAudio> { it.looksLikeApple }.thenBy { it.name })
    }

    /** Which profiles currently report [address] connected. Bounded by a timeout; never hangs. */
    @SuppressLint("MissingPermission")
    suspend fun connectedSources(ctx: Context, address: String): Set<ConnectionTracker.Source> {
        if (!canConnect(ctx) || !isOn(ctx)) return emptySet()
        val ad = adapter(ctx) ?: return emptySet()
        val out = mutableSetOf<ConnectionTracker.Source>()
        for ((profile, src) in listOf(BluetoothProfile.A2DP to ConnectionTracker.Source.A2DP, BluetoothProfile.HEADSET to ConnectionTracker.Source.HEADSET)) {
            val connected = withTimeoutOrNull(2_500) {
                suspendCancellableCoroutine { cont ->
                    val ok = runCatching {
                        ad.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                                val r = runCatching { proxy.connectedDevices.any { it.address.equals(address, true) } }.getOrDefault(false)
                                runCatching { ad.closeProfileProxy(p, proxy) }
                                if (cont.isActive) cont.resume(r)
                            }
                            override fun onServiceDisconnected(p: Int) { if (cont.isActive) cont.resume(false) }
                        }, profile)
                    }.getOrDefault(false)
                    if (!ok && cont.isActive) cont.resume(false)
                }
            } ?: false
            if (connected) out += src
        }
        return out
    }

    @SuppressLint("MissingPermission")
    fun device(ctx: Context, address: String): BluetoothDevice? =
        runCatching { adapter(ctx)?.getRemoteDevice(address) }.getOrNull()

    /**
     * Android's own headset battery, derived by the Bluetooth stack from Apple's HFP
     * AT+IPHONEACCEV report (single combined value). Hidden API accessed reflectively;
     * returns -1 when unavailable or blocked — never a guess.
     */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun systemBattery(ctx: Context, address: String): Int {
        if (!canConnect(ctx)) return -1
        val d = device(ctx, address) ?: return -1
        return runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int }
            .getOrDefault(-1).takeIf { it in 0..100 } ?: -1
    }
}
