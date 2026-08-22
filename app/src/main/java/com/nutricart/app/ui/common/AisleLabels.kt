package com.nutricart.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutricart.app.R
import com.nutricart.app.domain.model.Aisle

/**
 * Maps each aisle enum value to its translated label. Shared by the shopping
 * list and the fridge, which group by the very same aisles.
 */
@Composable
fun aisleLabel(aisle: Aisle): String = stringResource(
    when (aisle) {
        Aisle.PRODUCE -> R.string.aisle_produce
        Aisle.MEAT_FISH -> R.string.aisle_meat_fish
        Aisle.DAIRY_EGGS -> R.string.aisle_dairy_eggs
        Aisle.BAKERY -> R.string.aisle_bakery
        Aisle.GRAINS_PASTA -> R.string.aisle_grains
        Aisle.CANNED -> R.string.aisle_canned
        Aisle.FROZEN -> R.string.aisle_frozen
        Aisle.SPICES_OILS -> R.string.aisle_spices_oils
        Aisle.SNACKS -> R.string.aisle_snacks
        Aisle.BEVERAGES -> R.string.aisle_beverages
        Aisle.OTHER -> R.string.aisle_other
    }
)
