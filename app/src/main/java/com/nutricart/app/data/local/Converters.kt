package com.nutricart.app.data.local

import androidx.room.TypeConverter
import com.nutricart.app.domain.model.Allergen

/**
 * Room stores single enums as their name automatically; only the allergen LIST
 * needs a converter. Enum names never contain commas, so CSV is safe here.
 */
class Converters {

    @TypeConverter
    fun allergensToCsv(value: List<Allergen>): String =
        value.joinToString(separator = ",") { it.name }

    @TypeConverter
    fun csvToAllergens(value: String): List<Allergen> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { Allergen.valueOf(it) }
}
