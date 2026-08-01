package com.nutricart.app.domain.model

/**
 * Standard TDEE multipliers: how much a person burns compared to lying in bed all day.
 * Chosen once in onboarding; used as a fallback estimate when there is no watch data.
 */
enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}
