package com.profalikotb.airpods.ui

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.profalikotb.airpods.R
import com.profalikotb.airpods.bt.BtEnv
import com.profalikotb.airpods.data.AppState

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val st by vm.state.collectAsStateWithLifecycle()
            val state = st
            if (state == null) { Box(Modifier.fillMaxSize()); return@setContent } // first DataStore read (≈ms)
            val s = Str.of(state.lang)
            AppTheme(state.theme) {
                CompositionLocalProvider(LocalLayoutDirection provides if (s.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(s, state) }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); vm.onResume() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Root(s: Str, st: AppState) {
        val ctx = LocalContext.current
        val env by vm.env.collectAsStateWithLifecycle()
        val bonded by vm.bonded.collectAsStateWithLifecycle()
        val scanning by vm.scanning.collectAsStateWithLifecycle()
        val result by vm.result.collectAsStateWithLifecycle()
        val now by vm.now.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var picker by rememberSaveable { mutableStateOf(false) }
        var permAsked by rememberSaveable { mutableStateOf(false) }

        // Keep Bluetooth on/off state truthful while the screen is visible.
        DisposableEffect(Unit) {
            val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { vm.onResume() } }
            ContextCompat.registerReceiver(ctx, r, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { runCatching { ctx.unregisterReceiver(r) } }
        }

        val btPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onResume() }
        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onResume(); vm.finishOnboarding() }
        val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onResume() }

        fun requestBt() {
            val perms = BtEnv.runtimePermissions()
            val permanentlyDenied = permAsked && perms.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) } && !BtEnv.hasBtPermissions(ctx)
            if (permanentlyDenied) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            else { permAsked = true; btPerms.launch(perms) }
        }
        fun requestNotif() { BtEnv.notificationPermission()?.let { notifPerm.launch(it) } ?: vm.finishOnboarding() }
        fun openBtSettings() { runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }

        // ── First-run / recovery flow ──
        when {
            !env.btPerms -> { Onboard(s, s.welcomeTitle, s.welcomeBody + "\n\n" + s.permsTitle + "\n" + s.permsBody, s.grant, ::requestBt); return }
            !env.btOn -> { Onboard(s, s.btOff, s.welcomeBody, s.turnOnBt, { runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }; Unit }); return }
            st.selectedAddress == null -> {
                Onboard(s, s.selectAirPods, s.chooseDevice, s.selectAirPods, { vm.loadBonded(); picker = true }, secondary = s.openBtSettings to { openBtSettings() })
                if (picker) DevicePickerDialog(s, bonded, null, { vm.select(it); picker = false }, { openBtSettings() }, { picker = false })
                return
            }
            !st.onboardingDone && !env.notifPerm -> { Onboard(s, s.notifTitle, s.notifBody, s.grant, ::requestNotif, secondary = (if (s.ar) "تخطي" else "Skip") to { vm.finishOnboarding(); Unit }); return }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(s.appTitle, style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text(s.tabBattery) },
                        icon = { Icon(painterResource(R.drawable.ic_tab_battery), null) })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text(s.tabControls) },
                        icon = { Icon(painterResource(R.drawable.ic_tab_controls), null) })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, label = { Text(s.tabSettings) },
                        icon = { Icon(Icons.Filled.Settings, null) })
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> BatteryScreen(s, st, env, now, scanning, result, vm::refresh)
                    1 -> ControlsScreen(s, st, vm.probe, vm::clearProbe)
                    else -> SettingsScreen(s, st, env, SettingsActions(
                        setLang = vm::setLang, setTheme = vm::setTheme, setLow = vm::setLowNotif, setThreshold = vm::setThreshold,
                        setConn = { b -> if (b && !env.notifPerm) requestNotif(); vm.setConnNotif(b) },
                        setRssi = vm::setRssi, pickDevice = { vm.loadBonded(); picker = true }, forget = vm::forget, relock = vm::relockModel,
                        requestNotif = ::requestNotif, requestBt = ::requestBt,
                    ))
                }
            }
        }
        if (picker) DevicePickerDialog(s, bonded, st.selectedAddress, { vm.select(it); picker = false }, { openBtSettings() }, { picker = false })
    }
}

@Composable
private fun Onboard(s: Str, title: String, body: String, action: String, onAction: () -> Unit, secondary: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                BatteryGauge(com.profalikotb.airpods.core.BatteryLevel.Unavailable, Glyph.CASE,
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.onPrimaryContainer, 96.dp)
            }
        }
        Text(s.appTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) { Text(action) }
        secondary?.let { (t, f) -> TextButton(onClick = f) { Text(t) } }
    }
}

