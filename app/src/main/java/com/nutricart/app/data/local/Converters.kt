package com.nutricart.app.data.local

import androidx.room.TypeConverter
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.MealSlot
import java.time.DayOfWeek

/**
 * Room stores single enums as their name automatically; only enum LISTS
 * need converters. Enum names never contain commas, so CSV is safe here.
 */
class Converters {

    @TypeConverter
    fun allergensToCsv(value: List<Allergen>): String =
        value.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun csvToAllergens(value: String): List<Allergen> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { Allergen.valueOf(it) }

    @TypeConverter
    fun mealSlotsToCsv(value: List<MealSlot>): String =
        value.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun csvToMealSlots(value: String): List<MealSlot> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { MealSlot.valueOf(it) }

    @TypeConverter
    fun daysOfWeekToCsv(value: List<DayOfWeek>): String =
        value.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun csvToDaysOfWeek(value: String): List<DayOfWeek> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { DayOfWeek.valueOf(it) }
}
