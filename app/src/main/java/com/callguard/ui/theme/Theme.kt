package com.callguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.callguard.R

/** Palette from the design inspirations: pure black, graphite surfaces, signal red, white. */
object CgColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF1B1B1D)      // round keys, chips
    val SurfaceHigh = Color(0xFF2A2A2D)  // sheets, pills
    val Divider = Color(0xFF1F1F21)
    val Red = Color(0xFFD71921)
    val White = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF9A9A9E)
    val TextFaint = Color(0xFF5C5C60)
    val Green = Color(0xFF3DDC84)
    val Amber = Color(0xFFF2B33D)
}

/** Dot-matrix display face for names, numbers, and headings. */
val DotFont = FontFamily(
    Font(R.font.doto_bold, FontWeight.Bold),
    Font(R.font.doto_black, FontWeight.Black),
)

/** Monospaced face for list rows and labels (set in caps with wide tracking). */
val MonoFont = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

object CgType {
    val DisplayXL = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = 2.sp)
    val Display = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 1.sp)
    val Title = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 1.sp)
    val Row = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 2.sp)
    val RowSub = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 1.sp)
    val Label = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
}

private val scheme = darkColorScheme(
    primary = CgColors.White,
    onPrimary = CgColors.Black,
    secondary = CgColors.Red,
    onSecondary = CgColors.White,
    background = CgColors.Black,
    onBackground = CgColors.White,
    surface = CgColors.Black,
    onSurface = CgColors.White,
    surfaceVariant = CgColors.Surface,
    onSurfaceVariant = CgColors.TextDim,
    surfaceContainer = CgColors.Surface,
    surfaceContainerHigh = CgColors.SurfaceHigh,
    outline = CgColors.TextFaint,
    error = CgColors.Red,
)

@Composable
fun CallGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
