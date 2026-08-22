package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.MealSlot
import java.time.LocalTime

/**
 * Which meal is the user most likely logging right now?
 *
 * Used by the quick-add sheet to PRESELECT a slot. The guess is always shown
 * as chips the user can retap, so being wrong (shift work, a late dinner)
 * costs one tap and never silently files food under the wrong meal.
 */
object MealSlotGuess {

    /** Breakfast 05-10, lunch 11-15, dinner 16-21, anything else a snack. */
    fun forTime(time: LocalTime): MealSlot = when (time.hour) {
        in 5..10 -> MealSlot.BREAKFAST
        in 11..15 -> MealSlot.LUNCH
        in 16..21 -> MealSlot.DINNER
        else -> MealSlot.SNACK
    }
}
