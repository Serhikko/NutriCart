package com.nutricart.app.domain.model

/** The daily nutrition goal shown to the user and used by the meal-plan generator. */
data class DailyTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)
