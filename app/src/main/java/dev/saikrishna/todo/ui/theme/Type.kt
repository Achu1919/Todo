package dev.saikrishna.todo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.saikrishna.todo.R

// Open fonts bundled under res/font (SIL OFL 1.1; licences in /licenses):
//   Inter (variable wght+opsz), Newsreader (variable wght+opsz, roman + italic), JetBrains Mono.

private fun inter(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Between medium and semibold: the reference pill labels read slightly heavier than Inter 500. */
val LabelWeight = FontWeight(560)

val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(LabelWeight),
    inter(FontWeight.SemiBold),
)

/** Display serif: Newsreader at a medium weight and display optical size reads as a sturdy, warm headline. */
private fun newsreader(res: Int, weight: Int, style: FontStyle) = Font(
    res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", 28f)),
)

val SerifFamily = FontFamily(
    newsreader(R.font.newsreader_variable, 500, FontStyle.Normal),
    newsreader(R.font.newsreader_italic_variable, 400, FontStyle.Italic),
)

val JetBrainsMonoFamily = FontFamily(
    Font(
        R.font.jetbrains_mono_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/** Spec 2.2. Colours are applied at the call site from [AppColors]. */
@Immutable
data class AppTypography(
    val display: TextStyle,
    val displayItalic: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val meta: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

val DefaultTypography = AppTypography(
    display = TextStyle(
        fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight(500), letterSpacing = (-0.01).em,
    ),
    displayItalic = TextStyle(
        fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight(400), fontStyle = FontStyle.Italic, letterSpacing = (-0.01).em,
    ),
    title = TextStyle(
        fontFamily = InterFamily, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em,
    ),
    body = TextStyle(
        fontFamily = InterFamily, fontSize = 15.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.005).em,
    ),
    meta = TextStyle(
        fontFamily = InterFamily, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum",
    ),
    /** Pill segments, chips, buttons: 14 sp medium, slightly tight (measured from the reference pill). */
    label = TextStyle(
        fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 18.sp,
        fontWeight = LabelWeight, letterSpacing = (-0.005).em,
    ),
    mono = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 13.sp, lineHeight = 18.sp),
)

val LocalAppTypography = staticCompositionLocalOf { DefaultTypography }
