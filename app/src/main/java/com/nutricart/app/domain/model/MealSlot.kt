package com.nutricart.app.domain.model

/**
 * The four meal sections of a day, used by the diary and the meal plan.
 * DECLARATION ORDER = order within the day; `ordinal` is used for sorting,
 * so never reorder these entries.
 */
enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }
