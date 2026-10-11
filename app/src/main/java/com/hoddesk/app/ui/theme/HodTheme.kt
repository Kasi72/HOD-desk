package com.hoddesk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * iOS-style design tokens for Psychiatry HOD Desk.
 * Status hues follow Apple's system palette; their text variants are darkened
 * so labels on white keep at least 4.5:1 contrast.
 */
@Immutable
data class HodColors(
    val tint: Color,
    val onTint: Color,
    val tintSoft: Color,
    val groupedBackground: Color,
    val surface: Color,
    val fill: Color,
    val segmentTrack: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val chevron: Color,
    val orange: Color,
    val orangeText: Color,
    val red: Color,
    val redText: Color,
    val green: Color,
    val greenText: Color,
    val blue: Color,
    val blueText: Color,
    val indigo: Color,
    val purple: Color,
    val yellow: Color,
    val gray: Color,
    val evidenceMark: Color,
    val evidenceText: Color,
    val hatch: Color,
)

val LightHodColors = HodColors(
    tint = Color(0xFF0A7A70),
    onTint = Color.White,
    tintSoft = Color(0x1F0A7A70),
    groupedBackground = Color(0xFFF2F2F7),
    surface = Color.White,
    fill = Color(0xFFE3E3E8),
    segmentTrack = Color(0xFFEEEEF0),
    separator = Color(0xFFD1D1D6),
    label = Color(0xFF1C1C1E),
    secondaryLabel = Color(0xFF6C6C70),
    tertiaryLabel = Color(0xFF8E8E93),
    chevron = Color(0xFFC7C7CC),
    orange = Color(0xFFFF9500),
    orangeText = Color(0xFFC25E00),
    red = Color(0xFFFF3B30),
    redText = Color(0xFFD70015),
    green = Color(0xFF34C759),
    greenText = Color(0xFF248A3D),
    blue = Color(0xFF007AFF),
    blueText = Color(0xFF0062CC),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    yellow = Color(0xFFFFCC00),
    gray = Color(0xFF8E8E93),
    evidenceMark = Color(0x290A7A70),
    evidenceText = Color(0xFF064E47),
    hatch = Color(0xFFF2F2F7),
)

val DarkHodColors = HodColors(
    tint = Color(0xFF4FC3B5),
    onTint = Color(0xFF00201C),
    tintSoft = Color(0x334FC3B5),
    groupedBackground = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    fill = Color(0xFF2C2C2E),
    segmentTrack = Color(0xFF2C2C2E),
    separator = Color(0xFF38383A),
    label = Color(0xFFF2F2F7),
    secondaryLabel = Color(0xFFAEAEB2),
    tertiaryLabel = Color(0xFF8E8E93),
    chevron = Color(0xFF5A5A5E),
    orange = Color(0xFFFF9F0A),
    orangeText = Color(0xFFFFB340),
    red = Color(0xFFFF453A),
    redText = Color(0xFFFF6961),
    green = Color(0xFF30D158),
    greenText = Color(0xFF4CD964),
    blue = Color(0xFF0A84FF),
    blueText = Color(0xFF64A8FF),
    indigo = Color(0xFF5E5CE6),
    purple = Color(0xFFBF5AF2),
    yellow = Color(0xFFFFD60A),
    gray = Color(0xFF8E8E93),
    evidenceMark = Color(0x404FC3B5),
    evidenceText = Color(0xFFB4F0E8),
    hatch = Color(0xFF2C2C2E),
)

val LocalHodColors = staticCompositionLocalOf { LightHodColors }

/** iOS text styles, mapped to sp. The system font carries Tamil through Noto Sans Tamil. */
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

@Immutable
data class HodType(
    val largeTitle: TextStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, lineHeight = 40.sp, platformStyle = NoFontPadding),
    val title1: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, lineHeight = 34.sp, platformStyle = NoFontPadding),
    val title2: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em, lineHeight = 27.sp, platformStyle = NoFontPadding),
    val title3: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp, platformStyle = NoFontPadding),
    val headline: TextStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, platformStyle = NoFontPadding),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, platformStyle = NoFontPadding),
    val callout: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, platformStyle = NoFontPadding),
    val subheadline: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, platformStyle = NoFontPadding),
    val footnote: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, platformStyle = NoFontPadding),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, platformStyle = NoFontPadding),
    val tabLabel: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, platformStyle = NoFontPadding),
)

val LocalHodType = staticCompositionLocalOf { HodType() }

/** Continuous-corner radii, in the same units as the design canvas. */
object HodRadius {
    val icon: Dp = 9.dp
    val pill: Dp = 10.dp
    val small: Dp = 14.dp
    val button: Dp = 20.dp
    val card: Dp = 26.dp
    val sheet: Dp = 38.dp
}

/**
 * A squircle (continuous corner) shape. Each corner is a superellipse quadrant
 * |x|^n + |y|^n = 1 with n = 4, which reads like iOS continuous corners instead of
 * the circular arcs of RoundedCornerShape. Radius is clamped to half the short side.
 */
class SquircleShape(
    private val radius: Dp,
    private val topStart: Boolean = true,
    private val topEnd: Boolean = true,
    private val bottomEnd: Boolean = true,
    private val bottomStart: Boolean = true,
    private val exponent: Float = 4f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // A superellipse reaches further into the corner than a circle; scale up so
        // the visual radius matches the nominal one, as iOS does.
        val r = (with(density) { radius.toPx() } * 1.28f).coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path()
        val steps = 14
        fun corner(cx: Float, cy: Float, startAngle: Float, rounded: Boolean, cornerX: Float, cornerY: Float, first: Boolean) {
            if (!rounded || r <= 0f) {
                if (first) path.moveTo(cornerX, cornerY) else path.lineTo(cornerX, cornerY)
                return
            }
            for (i in 0..steps) {
                val t = Math.toRadians((startAngle + 90f * i / steps).toDouble())
                val c = cos(t).toFloat()
                val s = sin(t).toFloat()
                val x = cx + r * sign(c) * abs(c).pow(2f / exponent)
                val y = cy + r * sign(s) * abs(s).pow(2f / exponent)
                if (first && i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        val w = size.width
        val h = size.height
        val (tl, tr) = if (layoutDirection == LayoutDirection.Ltr) topStart to topEnd else topEnd to topStart
        val (br, bl) = if (layoutDirection == LayoutDirection.Ltr) bottomEnd to bottomStart else bottomStart to bottomEnd
        corner(r, r, 180f, tl, 0f, 0f, first = true)
        corner(w - r, r, 270f, tr, w, 0f, first = false)
        corner(w - r, h - r, 0f, br, w, h, first = false)
        corner(r, h - r, 90f, bl, 0f, h, first = false)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SquircleShape && other.radius == radius &&
        other.topStart == topStart && other.topEnd == topEnd && other.bottomEnd == bottomEnd && other.bottomStart == bottomStart
    override fun hashCode(): Int = radius.hashCode() * 31 + listOf(topStart, topEnd, bottomEnd, bottomStart).hashCode()
}

object Squircles {
    val icon = SquircleShape(HodRadius.icon)
    val pill = SquircleShape(HodRadius.pill)
    val small = SquircleShape(HodRadius.small)
    val button = SquircleShape(HodRadius.button)
    val card = SquircleShape(HodRadius.card)
    val sheetTop = SquircleShape(HodRadius.sheet, bottomEnd = false, bottomStart = false)
}

object Hod {
    val colors: HodColors @Composable get() = LocalHodColors.current
    val type: HodType @Composable get() = LocalHodType.current
}

@Composable
fun HodTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkHodColors else LightHodColors
    val scheme = if (dark) darkColorScheme(
        primary = colors.tint, onPrimary = colors.onTint, background = colors.groupedBackground,
        surface = colors.surface, onSurface = colors.label, onBackground = colors.label, error = colors.red,
        surfaceVariant = colors.fill, onSurfaceVariant = colors.secondaryLabel, outline = colors.separator,
    ) else lightColorScheme(
        primary = colors.tint, onPrimary = colors.onTint, background = colors.groupedBackground,
        surface = colors.surface, onSurface = colors.label, onBackground = colors.label, error = colors.red,
        surfaceVariant = colors.fill, onSurfaceVariant = colors.secondaryLabel, outline = colors.separator,
    )
    val type = HodType()
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(bodyLarge = type.body, bodyMedium = type.subheadline, bodySmall = type.footnote, titleLarge = type.title2, titleMedium = type.headline, labelLarge = type.headline),
        // Material slots need CornerBasedShape; our own components use the true squircles above.
        shapes = Shapes(
            extraSmall = RoundedCornerShape(HodRadius.pill), small = RoundedCornerShape(HodRadius.small),
            medium = RoundedCornerShape(HodRadius.button), large = RoundedCornerShape(HodRadius.card), extraLarge = RoundedCornerShape(HodRadius.sheet),
        ),
    ) {
        CompositionLocalProvider(LocalHodColors provides colors, LocalHodType provides type, content = content)
    }
}
