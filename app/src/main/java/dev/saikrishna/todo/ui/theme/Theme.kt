package dev.saikrishna.todo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * App theme. Material 3 is only used for primitives, so its scheme is mapped onto our tokens
 * (no tonal elevation, no dynamic colour).
 */
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val m3 = if (darkTheme) {
        darkColorScheme(
            primary = colors.ink, onPrimary = colors.paper,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
            surfaceTint = colors.paper, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.ink, onPrimary = colors.paper,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
            surfaceTint = colors.paper, error = colors.danger,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides DefaultTypography,
    ) {
        MaterialTheme(colorScheme = m3, content = content)
    }
}

/** Accessors: `AppTheme.colors.ink`, `AppTheme.type.title`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
}
