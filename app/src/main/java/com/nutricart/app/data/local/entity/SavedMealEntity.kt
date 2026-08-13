package com.nutricart.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named combo the user eats often ("My breakfast" = coffee + eggs + bread),
 * loggable into the diary with one tap. The items live in saved_meal_item.
 */
@Entity(tableName = "saved_meal")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMillis: Long,
)
