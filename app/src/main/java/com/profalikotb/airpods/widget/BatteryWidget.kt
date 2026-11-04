package com.profalikotb.airpods.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.profalikotb.airpods.core.BatteryLevel
import com.profalikotb.airpods.core.Freshness
import com.profalikotb.airpods.core.FreshnessPolicy
import com.profalikotb.airpods.data.AppState
import com.profalikotb.airpods.data.Prefs
import com.profalikotb.airpods.ui.MainActivity
import com.profalikotb.airpods.ui.Str

class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val st = Prefs.get(context).current()
        provideContent { Content(st) }
    }

    companion object {
        suspend fun refresh(ctx: Context) { runCatching { BatteryWidget().updateAll(ctx) } }
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

private val bg: GColor = ColorProvider(day = Color(0xFFF2FAF9), night = Color(0xFF122222))
private val fg: GColor = ColorProvider(day = Color(0xFF0B2B2A), night = Color(0xFFE6F4F3))
private val muted: GColor = ColorProvider(day = Color(0xFF5B7372), night = Color(0xFF9DB5B3))

private fun levelColor(l: BatteryLevel, stale: Boolean): GColor {
    if (stale || l !is BatteryLevel.Known) return muted
    return when {
        l.approxPercent <= 10 -> ColorProvider(Color(0xFFD93A3A), Color(0xFFFF7A7A))
        l.approxPercent <= 20 -> ColorProvider(Color(0xFFC77700), Color(0xFFFFB648))
        else -> ColorProvider(Color(0xFF0E8C86), Color(0xFF4FD1C5))
    }
}

@Composable
private fun Content(st: AppState) {
    val s = Str.of(st.lang)
    val now = System.currentTimeMillis()
    val snap = st.snapshot
    val fresh = FreshnessPolicy.classify(snap, st.connected, now)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(22.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val header = when {
            st.selectedAddress == null -> s.noDevice
            st.connected -> "${st.selectedName ?: "AirPods"} · ${s.connected}"
            else -> "${st.selectedName ?: "AirPods"} · ${s.disconnected}"
        }
        Text(header, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 1)
        if (snap == null || fresh == Freshness.Unavailable) {
            Text(s.unavailable, style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            return@Column
        }
        val stale = fresh !is Freshness.Live
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cells = if (snap.overEar) listOf(Triple(s.headphones, snap.single, false))
            else listOf(Triple("L", snap.left, snap.leftCharging), Triple("R", snap.right, snap.rightCharging), Triple(s.case, snap.case, snap.caseCharging))
            cells.forEachIndexed { i, (label, lvl, chg) ->
                if (i > 0) Spacer(GlanceModifier.width(14.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((if (chg) "⚡" else "") + s.level(lvl), style = TextStyle(color = levelColor(lvl, stale), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Text(label, style = TextStyle(color = muted, fontSize = 11.sp))
                }
            }
        }
        if (fresh is Freshness.LastKnown) Text("${s.lastKnown} · ${s.age(fresh.ageMs)}", style = TextStyle(color = muted, fontSize = 10.sp))
    }
}
