package com.nutricart.app.ui.ember

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalEmberColors = staticCompositionLocalOf { EmberLight }
val LocalEmberType = staticCompositionLocalOf { EmberTypeDefault }
val LocalEmberMotion = staticCompositionLocalOf { EmberMotion() }

/** The way screens read the design: `Ember.colors.surface`, `Ember.type.body`, `Ember.motion.reduced`. */
object Ember {
    val colors: EmberColors
        @Composable @ReadOnlyComposable get() = LocalEmberColors.current
    val type: EmberType
        @Composable @ReadOnlyComposable get() = LocalEmberType.current
    val motion: EmberMotion
        @Composable @ReadOnlyComposable get() = LocalEmberMotion.current
}

/**
 * The app theme. Ember tokens for our own components, and Material 3 underneath with every colour
 * role, type style, shape and motion spec mapped from them, so any M3 widget we still host (the
 * sheet, the date and time pickers, pull to refresh) is already Ember. Dynamic colour stays off:
 * the app looks the same on every phone.
 */
@Composable
fun EmberTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) EmberDark else EmberLight
    val motion = rememberEmberMotion()
    CompositionLocalProvider(
        LocalEmberColors provides colors,
        LocalEmberType provides EmberTypeDefault,
        LocalEmberMotion provides motion,
    ) {
        // No motionScheme here: Material3 1.4.0 keeps MotionScheme internal (see EmberMotionScheme).
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            shapes = EmberM3Shapes,
            typography = EmberM3Typography,
        ) {
            // Text outside any Surface would otherwise default to black, unreadable in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.label, content = content)
        }
    }
}

/**
 * Every Material 3 role set from Ember, so nothing falls back to the M3 baseline purple (error,
 * outlineVariant and the snackbar colours used to).
 */
fun EmberColors.toColorScheme(): ColorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = ink, onPrimary = onInk,                     // hosted DatePicker / TimePicker select in ink
    primaryContainer = fill, onPrimaryContainer = label,
    inversePrimary = tint,
    secondary = tint, onSecondary = onInk, secondaryContainer = fill, onSecondaryContainer = label,
    tertiary = emberInk, onTertiary = onAccent, tertiaryContainer = emberTrack, onTertiaryContainer = label,
    background = bg, onBackground = label,
    surface = surface, onSurface = label, surfaceVariant = surface2, onSurfaceVariant = label2,
    surfaceTint = Color.Transparent,                      // no tonal elevation anywhere
    inverseSurface = ink, inverseOnSurface = onInk,
    error = danger, onError = Color.White, errorContainer = danger.copy(alpha = .10f), onErrorContainer = danger,
    outline = sepStrong, outlineVariant = sep, scrim = scrim,
    surfaceBright = surface, surfaceDim = surface3,
    surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
    surfaceContainerHigh = sheet, surfaceContainerHighest = surface2,
)

val EmberM3Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

/** Every M3 role in Inter, so a hosted M3 widget (DatePicker, TimePicker) speaks the same type. */
val EmberM3Typography: Typography = EmberTypeDefault.let { t ->
    val display = t.largeTitle.copy(fontWeight = FontWeight.Bold)
    Typography(
        displayLarge = display.copy(fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = display.copy(fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = display.copy(fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = t.largeTitle,
        headlineMedium = t.title1,
        headlineSmall = t.title2,
        titleLarge = t.title2,
        titleMedium = t.headline,
        titleSmall = t.subhead,
        bodyLarge = t.body,
        bodyMedium = t.callout,
        bodySmall = t.footnote,
        labelLarge = t.subhead,
        labelMedium = t.caption,
        labelSmall = t.tab,
    )
}

/**
 * Material's six motion roles on Ember springs. The plan was to pass this to MaterialTheme so hosted
 * M3 widgets (the sheet above all) move on Ember springs, but Material3 1.4.0 keeps its MotionScheme
 * interface and the MaterialTheme overload that takes one internal. So these are plain specs:
 * EmberSheet animates with [defaultSpatialSpec] itself (the sheet spring, 0.76 / 182, a 2.5%
 * overshoot the floating card hides) and the scrim with [defaultEffectsSpec]. When Material exposes
 * MotionScheme, make this object implement it and hand it to MaterialTheme in [EmberTheme].
 */
object EmberMotionScheme {
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(0.82f, 828f)       // ≈ 300 ms
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = EmberSprings.sheet()
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = EmberSprings.smooth()
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 3335f)         // ≈ 160 ms, no bounce
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 1764f)      // ≈ 220 ms
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 949f)          // ≈ 300 ms
}
