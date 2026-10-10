package com.nutricart.app.ui.ember

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

// The shared day ring of "back from Add" (A1): the 28 dp ring in Food search's budget chip flies and
// grows into Today's hero ring (or the Diary's summary ring). AppNavHost wraps the NavHost in a
// SharedTransitionLayout and provides both scopes; everywhere else (screenshot tests, previews) they
// are null and the modifier does nothing.

/** The app's SharedTransitionLayout scope, provided by AppNavHost. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current destination's AnimatedVisibilityScope, provided inside every `composable {}`. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Sheet-spring bounds for the shared ring (520 ms, 2.5% overshoot). */
private val SheetBounds = BoundsTransform { _, _ -> EmberSprings.sheet() }

/**
 * Marks this element as the shared element [key] (for the day ring: "day-ring/<epochDay>"). A no-op
 * without a shared-transition scope, without a destination scope, or with "Remove animations" on
 * (plain navigation instead of a morph).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.emberSharedBounds(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (Ember.motion.reduced) return this
    return with(shared) {
        this@emberSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = SheetBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
