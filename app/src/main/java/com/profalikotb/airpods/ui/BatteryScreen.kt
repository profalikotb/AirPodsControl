package com.profalikotb.airpods.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.profalikotb.airpods.core.AirPodsModel
import com.profalikotb.airpods.core.BatteryLevel
import com.profalikotb.airpods.core.Freshness
import com.profalikotb.airpods.core.FreshnessPolicy
import com.profalikotb.airpods.data.AppState

@Composable
fun BatteryScreen(s: Str, st: AppState, env: Env, now: Long, scanning: Boolean, result: RefreshResult, onRefresh: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val snap = st.snapshot
    val fresh = FreshnessPolicy.classify(snap, st.connected, now)
    val stale = fresh !is Freshness.Live
    val model = (st.lockedModelId ?: snap?.modelId)?.let { AirPodsModel.fromId(it) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header: device, model, link status ──
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.selectedName ?: s.noDevice, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    StatusChip(
                        text = when { !env.btOn -> s.btOff; st.connected -> s.connected; else -> s.disconnected },
                        on = st.connected && env.btOn,
                    )
                }
                Text(
                    when {
                        model == null -> s.modelUnconfirmed
                        model == AirPodsModel.UNKNOWN -> "${model.display(s.ar)} · ${AirPodsModel.hex(st.lockedModelId ?: snap!!.modelId)}"
                        else -> "${model.display(s.ar)} · BLE ${AirPodsModel.hex(model.modelId)}"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
                Text(
                    "${s.updated}: " + (snap?.let { s.stamp(it.timestampMs) } ?: s.never) +
                        when (fresh) { Freshness.Live -> " · ${s.live}"; is Freshness.LastKnown -> " · ${s.lastKnown} (${s.age(fresh.ageMs)})"; Freshness.Unavailable -> "" },
                    style = MaterialTheme.typography.labelMedium, color = if (fresh == Freshness.Live) cs.primary else cs.onSurfaceVariant,
                )
            }
        }

        // ── Gauges. Kept physically left→right even in Arabic (left bud is on the left). ──
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
            val show = snap != null && fresh != Freshness.Unavailable
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (show && snap!!.overEar) {
                        Gauge(s, s.headphones, snap.single, false, Glyph.HEADPHONES, stale, 128)
                    } else {
                        Gauge(s, s.left, if (show) snap!!.left else BatteryLevel.Unavailable, show && snap!!.leftCharging, Glyph.LEFT_BUD, stale, 96)
                        Gauge(s, s.right, if (show) snap!!.right else BatteryLevel.Unavailable, show && snap!!.rightCharging, Glyph.RIGHT_BUD, stale, 96)
                        Gauge(s, s.case, if (show) snap!!.case else BatteryLevel.Unavailable, show && snap!!.caseCharging, Glyph.CASE, stale, 96)
                    }
                }
            }
            Text(s.stepNote, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        }

        // ── Notices ──
        val recentAmbiguous = now - st.lastAmbiguousAt < 2 * 60_000
        if (recentAmbiguous || result == RefreshResult.AMBIGUOUS) Notice(s.ambiguous, warn = true)
        when (result) {
            RefreshResult.TIMEOUT -> Notice(s.noReading, warn = false)
            RefreshResult.NOT_CONNECTED -> Notice("${s.disconnected}. ${s.lastKnown}.", warn = false)
            RefreshResult.BT_OFF -> Notice(s.btOff, warn = true)
            RefreshResult.NO_PERMISSION -> Notice(s.permsTitle, warn = true)
            RefreshResult.FAILED -> Notice(if (s.ar) "تعذّر بدء الفحص. أغلق البلوتوث وافتحه ثم حاول مجددًا." else "Scan could not start. Toggle Bluetooth and try again.", warn = true)
            else -> Unit
        }

        if (st.systemBattery in 0..100 && st.connected) {
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.systemBattery, style = MaterialTheme.typography.labelLarge)
                        Text(s.systemBatteryNote, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                    Text("${st.systemBattery}%", style = MaterialTheme.typography.titleLarge, color = cs.primary)
                }
            }
        }

        Button(
            onClick = onRefresh, enabled = !scanning && st.selectedAddress != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                Spacer(Modifier.width(10.dp)); Text(s.scanning)
            } else {
                Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text(s.refresh)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Gauge(s: Str, label: String, level: BatteryLevel, charging: Boolean, glyph: Glyph, stale: Boolean, sizeDp: Int) {
    val cs = MaterialTheme.colorScheme
    val color = cs.levelColor(level, stale)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryGauge(level, glyph, color, cs.surfaceVariant, cs.onSurface.copy(alpha = 0.82f), sizeDp.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            s.level(level),
            style = if (level is BatteryLevel.Known) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
            color = if (level is BatteryLevel.Known) (if (stale) cs.onSurfaceVariant else cs.onSurface) else cs.outline,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        if (charging) Text("⚡ ${s.charging}", style = MaterialTheme.typography.labelMedium, color = cs.primary, fontWeight = FontWeight.SemiBold)
        else Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun StatusChip(text: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(50), color = if (on) cs.primaryContainer else cs.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}

@Composable
fun Notice(text: String, warn: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = if (warn) cs.error.copy(alpha = 0.12f) else cs.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            if (warn) { Icon(Icons.Filled.Warning, null, tint = cs.error); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warn) cs.onSurface else cs.onSurfaceVariant)
        }
    }
}

