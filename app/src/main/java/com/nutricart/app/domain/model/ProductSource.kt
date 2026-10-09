package com.nutricart.app.domain.model

/**
 * Where a cached food product came from. Stored by name in Room, so a new
 * value needs no migration (but a value must never be renamed).
 *
 * ZAKAZ: a Ukrainian shop's catalogue (the zakaz.ua stores API), saved by
 * the scanner when Open Food Facts had nothing usable. Like an Open Food
 * Facts row it is read-only cached data: not editable, not deletable, never
 * shown with Open Food Facts' attribution.
 */
enum class ProductSource { OPEN_FOOD_FACTS, LOCAL, ZAKAZ }
