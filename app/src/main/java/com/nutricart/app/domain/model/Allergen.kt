package com.nutricart.app.domain.model

/**
 * Closed list of allergens. Both the user profile and recipes use THIS enum,
 * so the meal-plan filter is an exact set comparison — never fuzzy text matching.
 * (Free-text allergies like "peanut" vs "Peanuts" would silently fail to match,
 * which is a safety bug in a food app.)
 */
enum class Allergen {
    GLUTEN,
    DAIRY,
    EGGS,
    NUTS,
    PEANUTS,
    FISH,
    SHELLFISH,
    SOY,
}
