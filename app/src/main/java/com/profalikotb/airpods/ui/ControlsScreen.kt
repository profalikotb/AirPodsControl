package com.profalikotb.airpods.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.profalikotb.airpods.core.AirPodsModel
import com.profalikotb.airpods.core.Exposure
import com.profalikotb.airpods.core.GestureCatalog
import com.profalikotb.airpods.core.GestureRow
import com.profalikotb.airpods.core.MediaCommand
import com.profalikotb.airpods.data.AppState
import com.profalikotb.airpods.probe.GestureProbe

@Composable
fun ControlsScreen(s: Str, st: AppState, probe: GestureProbe, onClearProbe: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val model = (st.lockedModelId ?: st.snapshot?.modelId)?.let { AirPodsModel.fromId(it) }
    val observed = st.probeResults.keys.mapNotNull { k -> runCatching { MediaCommand.valueOf(k) }.getOrNull() }.toSet()
    val probeStatus by probe.status.collectAsStateWithLifecycle()
    val events by probe.events.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { probe.stop() } } // never leave the probe running off-screen

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(model?.display(s.ar) ?: s.modelUnconfirmed, style = MaterialTheme.typography.titleLarge)

        if (model == null || model == AirPodsModel.UNKNOWN) Notice(s.modelUnknownControls, warn = false)
        else GestureCatalog.rowsFor(model).forEach { GestureCard(s, it, observed) }

        // Why no switches
        Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                Icon(Icons.Filled.Info, null, tint = cs.primary); Spacer(Modifier.width(10.dp))
                Text(s.reassignWhy, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }

        // Noise control guidance
        model?.let { GestureCatalog.noiseGuidance(it, s.ar) }?.let { g ->
            Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.noiseTitle, style = MaterialTheme.typography.titleMedium)
                    Text(s.noiseApp, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Text(g, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Gesture probe
        Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.probeTitle, style = MaterialTheme.typography.titleMedium)
                Text(s.probeBody, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                when (probeStatus) {
                    GestureProbe.Status.RUNNING -> {
                        Text(s.probeRunning, color = cs.primary, style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick = { probe.stop() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(s.probeStop) }
                    }
                    else -> {
                        if (probeStatus == GestureProbe.Status.BLOCKED_CALL) Text(s.probeBlockedCall, color = cs.error)
                        if (probeStatus == GestureProbe.Status.FAILED) Text(if (s.ar) "تعذّر بدء الاختبار." else "Test could not start.", color = cs.error)
                        Button(onClick = { probe.start() }, enabled = st.connected, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(s.probeStart) }
                        if (!st.connected) Text(s.disconnected, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                if (events.isNotEmpty()) {
                    HorizontalDivider()
                    events.takeLast(8).reversed().forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = cs.primary); Spacer(Modifier.width(8.dp))
                            Text(s.command(e.command), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(e.via, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (observed.isNotEmpty() && probeStatus != GestureProbe.Status.RUNNING) {
                    TextButton(onClick = onClearProbe) { Text(if (s.ar) "مسح نتائج الاختبار" else "Clear test results") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GestureCard(s: Str, row: GestureRow, observed: Set<MediaCommand>) {
    val cs = MaterialTheme.colorScheme
    val measured = row.expectedCommand?.let { exp ->
        if (exp == MediaCommand.VOLUME_UP) (MediaCommand.VOLUME_UP in observed || MediaCommand.VOLUME_DOWN in observed) else exp in observed
    } ?: false
    Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.gesture(row.gesture), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (row.needsDeviceTest) StatusChip(s.needsTest, on = false)
            }
            Field(s.nativeFn, if (s.ar) row.nativeAr else row.nativeEn)
            Field(s.androidEvent, s.exposure(row.exposure) +
                if (row.expectedCommand != null) " · " + (if (measured) "${s.observed} ✓" else s.notObserved) else "")
            // Deliberately NOT a switch: a read-only, locked label.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = cs.outline); Spacer(Modifier.width(8.dp))
                Text("${s.reassign}: ${s.notReassignable}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            if (row.exposure == Exposure.FIRMWARE_CONFIGURED) Text(
                if (s.ar) "يمكن تغيير هذا الإعداد فقط من iPhone أو iPad أو Mac، ويبقى محفوظًا في السماعة عند استخدامها مع Android."
                else "Changeable only from an iPhone, iPad or Mac; the choice stays stored in the AirPods when used with Android.",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
