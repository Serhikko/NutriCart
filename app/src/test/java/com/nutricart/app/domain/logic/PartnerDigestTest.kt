package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PartnerDigestTest {

    private val labels = PartnerDigest.Labels(
        slotNames = listOf("Breakfast", "Lunch", "Dinner", "Snack"),
        kcalUnit = "kcal",
        dayTotalWithTarget = { eaten, target, remaining ->
            if (remaining >= 0) "Today: $eaten / $target kcal, $remaining left"
            else "Today: $eaten / $target kcal, ${-remaining} over"
        },
        dayTotalNoTarget = { eaten -> "Today: $eaten kcal" },
        moreItems = { n -> "…and $n more" },
        nothingYet = "Nothing logged yet today.",
    )

    private val lunch = listOf(
        PartnerDigest.Item(1, "Chicken with rice", 450.4),
        PartnerDigest.Item(1, "Apple", 70.0),
    )

    @Test
    fun `meal message lists the meal, its items and the day line`() {
        val text = PartnerDigest.mealMessage(1, lunch, eatenKcal = 1230.0, targetKcal = 2100.0, labels = labels)
        assertEquals(
            """
            🍽 Lunch — 520 kcal
            • Chicken with rice — 450 kcal
            • Apple — 70 kcal
            Today: 1230 / 2100 kcal, 870 left
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `meal message ignores other meals' items and reports going over`() {
        val items = lunch + PartnerDigest.Item(0, "Oats", 300.0)
        val text = PartnerDigest.mealMessage(1, items, eatenKcal = 2300.0, targetKcal = 2100.0, labels = labels)
        assertEquals(
            """
            🍽 Lunch — 520 kcal
            • Chicken with rice — 450 kcal
            • Apple — 70 kcal
            Today: 2300 / 2100 kcal, 200 over
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `day message groups by meal in slot order, without a target`() {
        val items = listOf(
            PartnerDigest.Item(2, "Soup", 200.0),
            PartnerDigest.Item(0, "Oats", 300.0),
        ) + lunch
        val text = PartnerDigest.dayMessage("NutriCart — 28 Sep", items, eatenKcal = 1020.4, targetKcal = null, labels = labels)
        assertEquals(
            """
            NutriCart — 28 Sep
            Breakfast — 300 kcal
            • Oats — 300 kcal
            Lunch — 520 kcal
            • Chicken with rice — 450 kcal
            • Apple — 70 kcal
            Dinner — 200 kcal
            • Soup — 200 kcal
            Today: 1020 kcal
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `empty day says so instead of an empty list`() {
        val text = PartnerDigest.dayMessage("Title", emptyList(), 0.0, 2000.0, labels)
        assertEquals("Title\nNothing logged yet today.", text)
    }

    @Test
    fun `a long meal is cut with a count of the rest`() {
        val items = (1..11).map { PartnerDigest.Item(3, "Snack $it", 10.0) }
        val text = PartnerDigest.mealMessage(3, items, 110.0, null, labels)
        val lines = text.lines()
        assertEquals("🍽 Snack — 110 kcal", lines.first())
        assertEquals(PartnerDigest.MAX_ITEMS_PER_MEAL, lines.count { it.startsWith("• ") })
        assertEquals("…and 3 more", lines[lines.size - 2])
        assertEquals("Today: 110 kcal", lines.last())
    }
}
