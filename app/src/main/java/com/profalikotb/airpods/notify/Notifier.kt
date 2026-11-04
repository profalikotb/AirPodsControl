package com.profalikotb.airpods.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.profalikotb.airpods.R
import com.profalikotb.airpods.bt.BtEnv
import com.profalikotb.airpods.core.AirPodsModel
import com.profalikotb.airpods.core.BatterySnapshot
import com.profalikotb.airpods.core.Freshness
import com.profalikotb.airpods.core.FreshnessPolicy
import com.profalikotb.airpods.core.LowBatteryAlerter
import com.profalikotb.airpods.data.AppState
import com.profalikotb.airpods.data.Lang
import com.profalikotb.airpods.ui.MainActivity
import com.profalikotb.airpods.ui.Str

object Notifier {
    private const val CH_LOW = "low_battery"
    private const val CH_CONN = "connection"
    private const val ID_CONN = 100

    fun createChannels(ctx: Context, lang: Lang) {
        val s = Str.of(lang)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LOW, s.chLow, NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_CONN, s.chConn, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Text built only from real readings; Unavailable stays "Unavailable". */
    fun summary(s: Str, snap: BatterySnapshot): String =
        if (snap.overEar) "${s.headphones} ${s.level(snap.single)}"
        else buildString {
            append("${s.left} ${s.level(snap.left)}${if (snap.leftCharging) " ⚡" else ""} · ")
            append("${s.right} ${s.level(snap.right)}${if (snap.rightCharging) " ⚡" else ""} · ")
            append("${s.case} ${s.level(snap.case)}${if (snap.caseCharging) " ⚡" else ""}")
        }

    @SuppressLint("MissingPermission")
    fun lowBattery(ctx: Context, a: LowBatteryAlerter.Alert, snap: BatterySnapshot, lang: Lang) {
        if (!BtEnv.canNotify(ctx)) return
        val s = Str.of(lang)
        val part = when (a.part) {
            LowBatteryAlerter.Part.LEFT -> s.left; LowBatteryAlerter.Part.RIGHT -> s.right
            LowBatteryAlerter.Part.CASE -> s.case; LowBatteryAlerter.Part.HEADPHONES -> s.headphones
        }
        val title = "${snap.model.takeIf { it != AirPodsModel.UNKNOWN }?.display(s.ar) ?: "AirPods"} — $part: " +
            (if (a.severity == LowBatteryAlerter.Severity.CRITICAL) s.criticalAlert else s.lowAlert)
        val n = NotificationCompat.Builder(ctx, CH_LOW)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(summary(s, snap))
            .setContentIntent(open(ctx)).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(200 + a.part.ordinal, n) }
    }

    @SuppressLint("MissingPermission")
    fun connected(ctx: Context, st: AppState) {
        if (!BtEnv.canNotify(ctx) || !st.connected) return
        val s = Str.of(st.lang)
        val now = System.currentTimeMillis()
        val snap = st.snapshot
        val text = when (val f = FreshnessPolicy.classify(snap, true, now)) {
            Freshness.Live -> summary(s, snap!!)
            is Freshness.LastKnown -> "${s.lastKnown} (${s.age(f.ageMs)}): ${summary(s, snap!!)}"
            Freshness.Unavailable -> s.never
        }
        val n = NotificationCompat.Builder(ctx, CH_CONN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${st.selectedName ?: "AirPods"} · ${s.connected}")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx)).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_CONN, n) }
    }

    fun cancelConnected(ctx: Context) = runCatching { NotificationManagerCompat.from(ctx).cancel(ID_CONN) }
}
