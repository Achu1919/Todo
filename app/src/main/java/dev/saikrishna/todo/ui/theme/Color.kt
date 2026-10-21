package dev.saikrishna.todo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Our own "ink over misty sky" palette (milestone 4 retheme). Technique taken from the reference
 * study: one ink colour used at a few alphas (100 / 50 / 30 / 14 / 7 / 5 %), translucent white
 * surfaces floating over a soft sky gradient, an ink capsule for selection. Every hex value here
 * is our own choice, picked by eye for tonality; none are copied from any other app.
 */
@Immutable
data class AppColors(
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val inkStroke: Color,
    val inkHairline: Color,
    val inkFill: Color,
    /** Text/icons drawn on an ink capsule. */
    val onInk: Color,
    /** Flat base colour (behind the sky, and the reduced-motion fallback for it). */
    val paper: Color,
    val paperRaised: Color,
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val skyGlow: Color,
    /** Deeper tone at the very bottom of the sky, so the light composer glass separates from it. */
    val skyDeep: Color,
    /** Header edge tint over the blurred content. */
    val glassChrome: Color,
    /** Opaque-ish chrome when blur is off (reduced motion). */
    val glassChromeFallback: Color,
    /** Floating controls: pill track, circle buttons. */
    val glassControl: Color,
    val glassControlFallback: Color,
    /** 1 dp light rim on glass controls and cards. */
    val glassRim: Color,
    val glassCard: Color,
    /** Glass tokens (v0.5): one fill / rim / shadow set for every glass surface. */
    val glassFill: Color,
    val glassRimTop: Color,
    val glassRimBottom: Color,
    val glassShadow: Color,
    /** Tint over the live blur of the composer and the floating panel. */
    val glassTint: Color,
    val composer: Color,
    val glassSolid: Color,
    val bevelTop: Color,
    val bevelBottom: Color,
    val accent: Color,
    val accentOnInk: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val scrimFocus: Color,
    val scrimExpand: Color,
    val scrimSheet: Color,
    val shadow: Color,
    val isDark: Boolean,
    /** v0.7 list dots: eight of our own hues, index stored per list ([ListPalette.size]). */
    val listPalette: List<Color> = emptyList(),
) {
    fun listColor(index: Int): Color = listPalette.getOrElse(index.mod(ListPalette.size)) { inkSecondary }
}

/** Names for the list colour picker, same order as [AppColors.listPalette]. */
object ListPalette {
    val names = listOf("Blue", "Amber", "Green", "Rose", "Violet", "Teal", "Coral", "Graphite")
    val size get() = names.size
}

private val InkLight = Color(0xFF1B1C20)
private val OnInkLight = Color(0xFFF7F8FA)
private val InkDark = Color(0xFFF1F2F5)
private val OnInkDark = Color(0xFF15161A)

val LightAppColors = AppColors(
    ink = InkLight,
    inkSecondary = InkLight.copy(alpha = 0.50f),
    inkTertiary = InkLight.copy(alpha = 0.30f),
    inkStroke = InkLight.copy(alpha = 0.14f),
    inkHairline = InkLight.copy(alpha = 0.07f),
    inkFill = InkLight.copy(alpha = 0.05f),
    onInk = OnInkLight,
    paper = Color(0xFFC9D2E1),
    paperRaised = Color(0xFFE6EBF3),
    skyTop = Color(0xFF66748F),
    skyMid = Color(0xFFA3B0C8),
    skyLow = Color(0xFFDAE2EE),
    skyGlow = Color(0xFFF1F4F9),
    skyDeep = Color(0xFFB7C2D6),
    glassChrome = Color(0xFF8391AC).copy(alpha = 0.22f),
    glassChromeFallback = Color(0xFFB4BFD3).copy(alpha = 0.96f),
    glassControl = Color.White.copy(alpha = 0.62f),
    glassControlFallback = Color(0xFFE3E8F0),
    glassRim = Color.White.copy(alpha = 0.55f),
    glassCard = Color.White.copy(alpha = 0.62f),
    glassFill = Color.White.copy(alpha = 0.58f),
    glassRimTop = Color.White.copy(alpha = 0.95f),
    glassRimBottom = Color(0xFF3A4766).copy(alpha = 0.16f),
    glassShadow = Color(0xFF1E2A44).copy(alpha = 0.55f),
    glassTint = Color.White.copy(alpha = 0.72f), // v0.7.2 frost (was 0.50, over an un-sampled list)
    composer = Color(0xFFF2F3F6).copy(alpha = 0.90f),
    glassSolid = Color(0xFFFCFCFD).copy(alpha = 0.985f),
    bevelTop = Color.White.copy(alpha = 0.85f),
    bevelBottom = InkLight.copy(alpha = 0.05f),
    accent = Color(0xFFE9562B),
    accentOnInk = Color(0xFFFF8A63),
    success = Color(0xFF12A26B),
    danger = Color(0xFFD63A40),
    warning = Color(0xFFDF962B),
    scrimFocus = Color.Black.copy(alpha = 0.30f),
    scrimExpand = Color.Black.copy(alpha = 0.16f),
    scrimSheet = Color.Black.copy(alpha = 0.12f),
    shadow = Color(0xFF26324A).copy(alpha = 0.12f),
    isDark = false,
    listPalette = listOf(
        Color(0xFF4F74C9), Color(0xFFD08A1E), Color(0xFF2E9A6C), Color(0xFFCC4F74),
        Color(0xFF7D62CC), Color(0xFF26899B), Color(0xFFDB6440), Color(0xFF6E7685),
    ),
)

val DarkAppColors = AppColors(
    ink = InkDark,
    inkSecondary = InkDark.copy(alpha = 0.55f),
    inkTertiary = InkDark.copy(alpha = 0.32f),
    inkStroke = InkDark.copy(alpha = 0.16f),
    inkHairline = InkDark.copy(alpha = 0.08f),
    inkFill = InkDark.copy(alpha = 0.07f),
    onInk = OnInkDark,
    paper = Color(0xFF10141D),
    paperRaised = Color(0xFF1B202B),
    skyTop = Color(0xFF070A11),
    skyMid = Color(0xFF111723),
    skyLow = Color(0xFF1E2636),
    skyGlow = Color(0xFF2B3550),
    skyDeep = Color(0xFF151B29),
    glassChrome = Color(0xFF05070C).copy(alpha = 0.30f),
    glassChromeFallback = Color(0xFF0B0F17).copy(alpha = 0.96f),
    glassControl = Color(0xFF2A303D).copy(alpha = 0.62f),
    glassControlFallback = Color(0xFF232937),
    glassRim = Color.White.copy(alpha = 0.09f),
    glassCard = Color(0xFF262C39).copy(alpha = 0.66f),
    glassFill = Color(0xFF2A303D).copy(alpha = 0.62f),
    glassRimTop = Color.White.copy(alpha = 0.14f),
    glassRimBottom = Color.White.copy(alpha = 0.03f),
    glassShadow = Color.Black.copy(alpha = 0.85f),
    glassTint = Color(0xFF262C39).copy(alpha = 0.70f),
    composer = Color(0xFF232833).copy(alpha = 0.92f),
    glassSolid = Color(0xFF181C25).copy(alpha = 0.985f),
    bevelTop = Color.White.copy(alpha = 0.08f),
    bevelBottom = Color.Black.copy(alpha = 0.25f),
    accent = Color(0xFFFF7247),
    accentOnInk = Color(0xFFD2471E),
    success = Color(0xFF34C48A),
    danger = Color(0xFFEC5F63),
    warning = Color(0xFFF0B04E),
    scrimFocus = Color.Black.copy(alpha = 0.45f),
    scrimExpand = Color.Black.copy(alpha = 0.30f),
    scrimSheet = Color.Black.copy(alpha = 0.30f),
    shadow = Color.Black.copy(alpha = 0.40f),
    isDark = true,
    listPalette = listOf(
        Color(0xFF7E9CEB), Color(0xFFEBB050), Color(0xFF52C493), Color(0xFFEB7C9C),
        Color(0xFFA68CF0), Color(0xFF4DB8C9), Color(0xFFF08B66), Color(0xFF9BA3B3),
    ),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
