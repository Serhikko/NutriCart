package com.nutricart.app.domain.model

/**
 * Shared limits and choices for the profile forms.
 * Used by BOTH onboarding and settings, so the rules never drift apart.
 */
object ProfileOptions {
    val HEIGHT_CM_RANGE = 100..250
    val WEIGHT_KG_RANGE = 30.0..300.0
    const val DEFAULT_RATE_KG_PER_WEEK = 0.5
    val RATE_OPTIONS = listOf(0.25, 0.5, 0.75, 1.0)
    val SNACK_OPTIONS = listOf(0, 1, 2)

    /**
     * How many times per week the user cooks. Fewer sessions = the meal plan
     * repeats each day-menu across several days (batch cooking).
     */
    const val DEFAULT_COOKING_SESSIONS = 7
    val COOKING_OPTIONS = listOf(3, 4, 7)
}
