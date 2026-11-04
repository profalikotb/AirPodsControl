package com.profalikotb.airpods.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.profalikotb.airpods.BuildConfig
import com.profalikotb.airpods.bt.BondedAudio
import com.profalikotb.airpods.data.AppState
import com.profalikotb.airpods.data.Lang
import com.profalikotb.airpods.data.ThemeMode

data class SettingsActions(
    val setLang: (Lang) -> Unit, val setTheme: (ThemeMode) -> Unit,
    val setLow: (Boolean) -> Unit, val setThreshold: (Int) -> Unit, val setConn: (Boolean) -> Unit,
    val setRssi: (Int) -> Unit, val pickDevice: () -> Unit, val forget: () -> Unit, val relock: () -> Unit,
    val requestNotif: () -> Unit, val requestBt: () -> Unit,
)

@Composable
fun SettingsScreen(s: Str, st: AppState, env: Env, a: SettingsActions) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(s.device) {
            Text(st.selectedName ?: s.noDevice, style = MaterialTheme.typography.titleMedium)
            st.selectedAddress?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = a.pickDevice) { Text(s.change) }
                if (st.selectedAddress != null) OutlinedButton(onClick = a.forget) { Text(s.forget) }
            }
            if (st.lockedModelId != null) {
                TextButton(onClick = a.relock) { Text(s.relock) }
                Text(s.relockNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!env.btPerms || !env.notifPerm) Section(if (s.ar) "الأذونات" else "Permissions") {
            if (!env.btPerms) PermRow(s.permsTitle, s.grant, a.requestBt)
            if (!env.notifPerm) PermRow(s.notifTitle, s.grant, a.requestNotif)
        }

        Section(s.language) {
            Segmented(listOf(Lang.SYSTEM to s.sysDefault, Lang.EN to "English", Lang.AR to "العربية"), st.lang, a.setLang)
        }
        Section(s.theme) {
            Segmented(listOf(ThemeMode.SYSTEM to s.sysDefault, ThemeMode.LIGHT to s.light, ThemeMode.DARK to s.dark), st.theme, a.setTheme)
        }

        Section(if (s.ar) "الإشعارات" else "Notifications") {
            SwitchRow(s.lowNotif, st.lowBatteryNotif, a.setLow)
            if (st.lowBatteryNotif) {
                var t by remember(st.lowThreshold) { mutableFloatStateOf(st.lowThreshold.toFloat()) }
                Text("${s.lowThreshold}: ≤${t.toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = t, onValueChange = { t = it }, onValueChangeFinished = { a.setThreshold(t.toInt()) },
                    valueRange = 10f..50f, steps = 3)
            }
            SwitchRow(s.connNotif, st.connectionNotif, a.setConn)
        }

        Section(s.rssi) {
            var r by remember(st.rssiThreshold) { mutableFloatStateOf(st.rssiThreshold.toFloat()) }
            Text("${r.toInt()} dBm", style = MaterialTheme.typography.labelLarge)
            Slider(value = r, onValueChange = { r = it }, onValueChangeFinished = { a.setRssi(r.toInt()) },
                valueRange = -85f..-45f, steps = 7)
            Text(s.rssiNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(s.about) {
            Text(s.aboutBody, style = MaterialTheme.typography.bodyMedium)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermRow(label: String, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp)); FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(selected = v == selected, onClick = { onSelect(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

/** Bonded-device chooser. Only devices already paired in Android settings are listed. */
@Composable
fun DevicePickerDialog(s: Str, devices: List<BondedAudio>, current: String?, onPick: (BondedAudio) -> Unit, onOpenBt: () -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(devices.firstOrNull { it.address == current } ?: devices.firstOrNull { it.looksLikeApple }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.selectAirPods) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (devices.isEmpty()) { Text(s.noBonded, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text(s.pairInSettings) }
                else {
                    Text(s.chooseDevice, style = MaterialTheme.typography.bodyMedium)
                    devices.forEach { d ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { sel = d }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel == d, onClick = { sel = d })
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                Text(if (d.looksLikeApple) "Apple · ${d.address}" else d.address,
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (devices.isEmpty()) TextButton(onClick = onOpenBt) { Text(s.openBtSettings) }
            else TextButton(onClick = { sel?.let(onPick) }, enabled = sel != null) { Text(if (s.ar) "اختيار" else "Select") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.ar) "إلغاء" else "Cancel") } },
    )
}
