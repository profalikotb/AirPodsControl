package com.profalikotb.airpods.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.profalikotb.airpods.core.BatteryLevel
import com.profalikotb.airpods.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF0E8C86), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFEC), onPrimaryContainer = Color(0xFF00302D),
    secondary = Color(0xFF4A6361), surface = Color(0xFFF7FBFA), onSurface = Color(0xFF0E1D1C),
    surfaceVariant = Color(0xFFE3EEEC), onSurfaceVariant = Color(0xFF4C6260),
    surfaceContainer = Color(0xFFEDF5F4), surfaceContainerHigh = Color(0xFFE6F0EF),
    background = Color(0xFFF4F8F8), outline = Color(0xFF9AB0AE), error = Color(0xFFD93A3A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF4FD1C5), onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF0E4D49), onPrimaryContainer = Color(0xFFB9F1EB),
    secondary = Color(0xFFB0CCC9), surface = Color(0xFF0F1817), onSurface = Color(0xFFE2EEEC),
    surfaceVariant = Color(0xFF1E2C2B), onSurfaceVariant = Color(0xFFA6BDBA),
    surfaceContainer = Color(0xFF162221), surfaceContainerHigh = Color(0xFF1C2A29),
    background = Color(0xFF0D1515), outline = Color(0xFF5B716F), error = Color(0xFFFF7A7A),
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}

/** Battery colour bands. Unknown / stale → neutral, never green. */
fun ColorScheme.levelColor(l: BatteryLevel, stale: Boolean): Color = when {
    l !is BatteryLevel.Known || stale -> outline
    l.approxPercent <= 10 -> error
    l.approxPercent <= 20 -> if (primary == Color(0xFF0E8C86)) Color(0xFFC77700) else Color(0xFFFFB648)
    else -> primary
}

enum class Glyph { LEFT_BUD, RIGHT_BUD, CASE, HEADPHONES }

/**
 * Ring gauge with an earbud / case / headphone illustration inside.
 * Unavailable levels draw a dashed empty ring — no partial fill is ever implied.
 */
@Composable
fun BatteryGauge(level: BatteryLevel, glyph: Glyph, color: Color, track: Color, ink: Color, size: Dp = 96.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = this.size.minDimension * 0.075f
            val inset = sw / 2 + 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            if (level is BatteryLevel.Known) {
                drawArc(track, 135f, 270f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                if (level.tenths > 0) drawArc(color, 135f, 270f * level.tenths / 10f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            } else {
                drawArc(track, 135f, 270f, false, tl, arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 0.6f, sw * 1.6f))))
            }
            drawGlyph(glyph, ink)
        }
    }
}

private fun DrawScope.drawGlyph(g: Glyph, ink: Color) {
    val w = size.width; val h = size.height
    val c = Offset(w / 2, h / 2)
    when (g) {
        Glyph.LEFT_BUD, Glyph.RIGHT_BUD -> scale(if (g == Glyph.RIGHT_BUD) -1f else 1f, 1f, c) {
            // head
            drawOval(ink, Offset(w * 0.36f, h * 0.27f), Size(w * 0.26f, h * 0.22f))
            drawCircle(ink.copy(alpha = 0.35f), w * 0.035f, Offset(w * 0.56f, h * 0.36f))
            // stem
            drawRoundRect(ink, Offset(w * 0.385f, h * 0.42f), Size(w * 0.085f, h * 0.33f), CornerRadius(w * 0.05f))
        }
        Glyph.CASE -> {
            drawRoundRect(ink, Offset(w * 0.31f, h * 0.32f), Size(w * 0.38f, h * 0.34f), CornerRadius(w * 0.09f))
            drawLine(ink.copy(alpha = 0.35f), Offset(w * 0.31f, h * 0.43f), Offset(w * 0.69f, h * 0.43f), strokeWidth = w * 0.012f)
            drawCircle(ink.copy(alpha = 0.45f), w * 0.016f, Offset(w * 0.5f, h * 0.55f))
        }
        Glyph.HEADPHONES -> {
            drawArc(ink, 180f, 180f, false, Offset(w * 0.30f, h * 0.28f), Size(w * 0.40f, h * 0.40f), style = Stroke(w * 0.05f, cap = StrokeCap.Round))
            drawRoundRect(ink, Offset(w * 0.26f, h * 0.45f), Size(w * 0.12f, h * 0.22f), CornerRadius(w * 0.04f))
            drawRoundRect(ink, Offset(w * 0.62f, h * 0.45f), Size(w * 0.12f, h * 0.22f), CornerRadius(w * 0.04f))
        }
    }
}
