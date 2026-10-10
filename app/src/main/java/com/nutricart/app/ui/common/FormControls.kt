package com.nutricart.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutricart.app.R
import com.nutricart.app.domain.model.Allergen

/**
 * Labels shared by the onboarding and settings screens, so both name the same choices with the same
 * words. The controls themselves are in ui/onboarding/ProfileForm.kt.
 */

/** Maps a cooking-sessions-per-week value (3/4/7) to its translated label. */
@Composable
fun cookingSessionsLabel(sessions: Int): String = stringResource(
    when (sessions) {
        7 -> R.string.cooking_daily
        4 -> R.string.cooking_every_other
        else -> R.string.cooking_few
    }
)

/** Maps each allergen enum value to its translated label. */
@Composable
fun allergenLabel(allergen: Allergen): String = stringResource(
    when (allergen) {
        Allergen.GLUTEN -> R.string.allergen_gluten
        Allergen.DAIRY -> R.string.allergen_dairy
        Allergen.EGGS -> R.string.allergen_eggs
        Allergen.NUTS -> R.string.allergen_nuts
        Allergen.PEANUTS -> R.string.allergen_peanuts
        Allergen.FISH -> R.string.allergen_fish
        Allergen.SHELLFISH -> R.string.allergen_shellfish
        Allergen.SOY -> R.string.allergen_soy
    }
)
