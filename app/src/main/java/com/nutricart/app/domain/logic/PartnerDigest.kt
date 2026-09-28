package com.nutricart.app.domain.logic

import kotlin.math.roundToInt

/**
 * Formats the diary into the short plain-text messages a partner reads on
 * their phone — one meal as it is logged, or the whole day so far. Pure text
 * building; every word that must follow the app language arrives in [Labels],
 * the same way ManualExportProvider takes its aisle names.
 */
object PartnerDigest {

    /** One diary line: which meal (MealSlot ordinal), what, how much. */
    data class Item(
        val slotOrdinal: Int,
        val name: String,
        val kcal: Double,
    )

    data class Labels(
        /** Meal names indexed by MealSlot ordinal. */
        val slotNames: List<String>,
        /** "kcal" */
        val kcalUnit: String,
        /** "Today: 1230 / 2100 kcal, 870 left" — remaining may be negative (over). */
        val dayTotalWithTarget: (eaten: Int, target: Int, remaining: Int) -> String,
        /** "Today: 1230 kcal" — when no target is known (e.g. the diary share). */
        val dayTotalNoTarget: (eaten: Int) -> String,
        /** "…and 3 more" */
        val moreItems: (count: Int) -> String,
        /** Shown instead of meals when the day has no entries. */
        val nothingYet: String,
    )

    /** A meal longer than this is cut, so a message never scrolls for pages. */
    const val MAX_ITEMS_PER_MEAL = 8

    /**
     * "🍽 Lunch — 620 kcal" + one bullet per item + the day line.
     * [items] should hold only this meal's entries; others are ignored.
     */
    fun mealMessage(
        slotOrdinal: Int,
        items: List<Item>,
        eatenKcal: Double,
        targetKcal: Double?,
        labels: Labels,
    ): String = buildString {
        val mine = items.filter { it.slotOrdinal == slotOrdinal }
        appendLine("🍽 ${mealHeader(slotOrdinal, mine, labels)}")
        appendItems(mine, labels)
        append(dayLine(eatenKcal, targetKcal, labels))
    }

    /** The day so far, meal by meal in slot order, then the day line. */
    fun dayMessage(
        title: String,
        items: List<Item>,
        eatenKcal: Double,
        targetKcal: Double?,
        labels: Labels,
    ): String = buildString {
        appendLine(title)
        if (items.isEmpty()) {
            append(labels.nothingYet)
            return@buildString
        }
        items.groupBy { it.slotOrdinal }.toSortedMap().forEach { (slot, mine) ->
            appendLine(mealHeader(slot, mine, labels))
            appendItems(mine, labels)
        }
        append(dayLine(eatenKcal, targetKcal, labels))
    }

    private fun mealHeader(slotOrdinal: Int, items: List<Item>, labels: Labels): String {
        val name = labels.slotNames.getOrNull(slotOrdinal) ?: slotOrdinal.toString()
        return "$name — ${items.sumOf { it.kcal }.roundToInt()} ${labels.kcalUnit}"
    }

    private fun StringBuilder.appendItems(items: List<Item>, labels: Labels) {
        items.take(MAX_ITEMS_PER_MEAL).forEach { item ->
            appendLine("• ${item.name} — ${item.kcal.roundToInt()} ${labels.kcalUnit}")
        }
        val hidden = items.size - MAX_ITEMS_PER_MEAL
        if (hidden > 0) appendLine(labels.moreItems(hidden))
    }

    private fun dayLine(eatenKcal: Double, targetKcal: Double?, labels: Labels): String {
        val eaten = eatenKcal.roundToInt()
        if (targetKcal == null) return labels.dayTotalNoTarget(eaten)
        val target = targetKcal.roundToInt()
        return labels.dayTotalWithTarget(eaten, target, target - eaten)
    }
}
