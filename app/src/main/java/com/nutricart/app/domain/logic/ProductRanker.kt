package com.nutricart.app.domain.logic

/**
 * Orders search results so the products the user logs most often come first.
 * Pure Kotlin, generic over the item type (Room entities can't enter
 * domain/logic), covered by plain JUnit tests.
 */
object ProductRanker {

    /**
     * Sorts by use count, most-used first. sortedByDescending is STABLE:
     * products the user never logged (count 0) keep their incoming order,
     * which for online results is the API's own relevance order.
     */
    fun <T> rank(items: List<T>, idOf: (T) -> String, useCounts: Map<String, Int>): List<T> =
        items.sortedByDescending { useCounts[idOf(it)] ?: 0 }
}
