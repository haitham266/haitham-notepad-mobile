package com.haitham.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

object Brand {
    val Indigo = Color(0xFF4B3BF5)
    val Deep = Color(0xFF1E1466)
    val Dark = Color(0xFF140F3D)
    val Gold = Color(0xFFFFB020)
    val Pink = Color(0xFFFF4F8B)
    val Teal = Color(0xFF19D3A2)
    val Red = Color(0xFFE5484D)
    val gradient = Brush.linearGradient(listOf(Color(0xFF5B4BFF), Color(0xFF2A1F99)))
}

/** ألوان الملاحظات والأقسام (مثل ColorNote) — فاتح / داكن / لون مميز */
data class NoteColor(val name: String, val light: Color, val dark: Color, val accent: Color)

val NoteColors = listOf(
    NoteColor("افتراضي", Color(0xFFFFFFFF), Color(0xFF1E1B33), Color(0xFF6B6890)),
    NoteColor("أصفر", Color(0xFFFFF4B8), Color(0xFF4A4220), Color(0xFFF2B705)),
    NoteColor("برتقالي", Color(0xFFFFE0C2), Color(0xFF4D3320), Color(0xFFFF8A1F)),
    NoteColor("وردي", Color(0xFFFFD6E2), Color(0xFF4D2333), Color(0xFFFF4F8B)),
    NoteColor("بنفسجي", Color(0xFFE6DCFF), Color(0xFF34285A), Color(0xFF7B5CFF)),
    NoteColor("أزرق", Color(0xFFD3E6FF), Color(0xFF1F3354), Color(0xFF2F80ED)),
    NoteColor("فيروزي", Color(0xFFCFF5EC), Color(0xFF1C433C), Color(0xFF14B8A6)),
    NoteColor("أخضر", Color(0xFFDDF5C9), Color(0xFF2B4220), Color(0xFF43A047)),
    NoteColor("رمادي", Color(0xFFE5E7EB), Color(0xFF34363D), Color(0xFF757B8A)),
)

fun noteColor(i: Int) = NoteColors.getOrElse(i) { NoteColors[0] }

val LocalDark = staticCompositionLocalOf { false }

@Composable
fun noteBg(i: Int): Color = if (LocalDark.current) noteColor(i).dark else noteColor(i).light

private val Light = lightColorScheme(
    primary = Brand.Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF), onPrimaryContainer = Brand.Deep,
    secondary = Brand.Gold, tertiary = Brand.Teal,
    background = Color(0xFFF5F4FB), surface = Color.White, onSurface = Color(0xFF1A1833),
    surfaceVariant = Color(0xFFEDEBF7), onSurfaceVariant = Color(0xFF5E5B7A), error = Brand.Red,
    surfaceContainer = Color(0xFFF0EEFA), surfaceContainerHigh = Color(0xFFEAE7F8), surfaceContainerLow = Color(0xFFF7F6FD),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8F84FF), onPrimary = Color(0xFF140F3D),
    primaryContainer = Color(0xFF2E2585), onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Brand.Gold, tertiary = Brand.Teal,
    background = Color(0xFF0F0D22), surface = Color(0xFF17142E), onSurface = Color(0xFFEAE8F7),
    surfaceVariant = Color(0xFF252142), onSurfaceVariant = Color(0xFFA9A5C8), error = Color(0xFFFF6B6B),
    surfaceContainer = Color(0xFF1B1835), surfaceContainerHigh = Color(0xFF221E3F), surfaceContainerLow = Color(0xFF15122B),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun HnTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDark provides dark) {
            content()
        }
    }
}
