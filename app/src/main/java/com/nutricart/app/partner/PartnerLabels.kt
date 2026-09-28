package com.nutricart.app.partner

import android.content.Context
import com.nutricart.app.R
import com.nutricart.app.domain.logic.PartnerDigest
import com.nutricart.app.domain.model.MealSlot

/** The meal name resource for a slot — shared by every message the partner sees. */
fun mealSlotLabelRes(slot: MealSlot): Int = when (slot) {
    MealSlot.BREAKFAST -> R.string.meal_breakfast
    MealSlot.LUNCH -> R.string.meal_lunch
    MealSlot.DINNER -> R.string.meal_dinner
    MealSlot.SNACK -> R.string.meal_snack
}

/**
 * The words of a partner message in the app's language. Built from a Context
 * (workers) or LocalContext (the diary's share button) — PartnerDigest itself
 * knows no resources.
 */
fun partnerLabels(context: Context): PartnerDigest.Labels = PartnerDigest.Labels(
    slotNames = MealSlot.entries.map { context.getString(mealSlotLabelRes(it)) },
    kcalUnit = context.getString(R.string.kcal_unit),
    dayTotalWithTarget = { eaten, target, remaining ->
        if (remaining >= 0) {
            context.getString(R.string.partner_day_total_left, eaten, target, remaining)
        } else {
            context.getString(R.string.partner_day_total_over, eaten, target, -remaining)
        }
    },
    dayTotalNoTarget = { eaten -> context.getString(R.string.partner_day_total, eaten) },
    moreItems = { count -> context.getString(R.string.partner_more_items, count) },
    nothingYet = context.getString(R.string.partner_nothing_yet),
)
