package com.nutricart.app.domain.logic

/**
 * The fridge rules that involve a number, kept out of Android so they can be
 * tested as plain Kotlin: what cooking takes out, and what can be cooked from
 * what is in stock.
 */
object FridgeMath {

    /**
     * Anything at or below half a gram is dust, not food. Cooking exactly what
     * was bought leaves floating-point residue (the portion factor travels
     * through the navigation arguments as a Float), and without this a row
     * would survive at 0.0000095 g and still read as "I have it".
     */
    const val EMPTY_EPSILON_G = 0.5

    /** One ingredient and how many grams of it are involved. */
    data class Use(val name: String, val grams: Double)

    /** A recipe and what one portion of it needs. */
    data class RecipeNeed(val recipeId: Long, val name: String, val needs: List<Use>)

    /** How well the fridge covers a recipe. */
    data class Match(
        val recipeId: Long,
        val name: String,
        val missingCount: Int,
        val missingNames: List<String>,
    )

    /**
     * What cooking takes out of the fridge: every ingredient scaled by the
     * meal's portion factor and by how many portions were cooked at once
     * (batch cooking makes one pot for several days).
     *
     * The factor is snapped to the generator's own 0.05 grid first, so the
     * grams deducted match the grams the shopping list added instead of
     * differing in the twelfth decimal.
     */
    fun uses(needs: List<Use>, portionFactor: Double, portions: Int = 1): List<Use> {
        require(portions >= 1) { "portions must be at least 1" }
        val factor = MealPlanGenerator.roundFactor(portionFactor) * portions
        return needs.map { Use(it.name, it.grams * factor) }
    }

    /**
     * Subtracting [uses] from [stock], with rows that fall to dust removed.
     * The DAO does the same thing in SQL; this is the version the tests can
     * reason about, and the one place the "never store a negative" rule lives.
     */
    fun applyUses(stock: Map<String, Double>, uses: List<Use>): Map<String, Double> {
        val result = stock.toMutableMap()
        uses.forEach { use ->
            val left = (result[use.name] ?: return@forEach) - use.grams
            if (left <= EMPTY_EPSILON_G) result.remove(use.name) else result[use.name] = left
        }
        return result
    }

    /**
     * Recipes ordered by how little is missing, then by name.
     *
     * Counted in GRAMS, not just names: a 3 g onion remnant is not "I have
     * onion". The required amount is the recipe as written (factor 1.0) —
     * the portion factor is only chosen after a recipe is picked, so asking
     * "do I have enough for a portion I have not sized yet" would be circular.
     *
     * [plannedIds] sink to the bottom of their group. The fridge is filled
     * from the shopping list, which is built FROM the plan, so without this
     * the card would mostly read back the week the user already has.
     */
    fun rank(
        recipes: List<RecipeNeed>,
        stock: Map<String, Double>,
        plannedIds: Set<Long> = emptySet(),
    ): List<Match> =
        recipes
            .map { recipe ->
                val missing = recipe.needs
                    .filter { (stock[it.name] ?: 0.0) < it.grams }
                    .map { it.name }
                Match(recipe.recipeId, recipe.name, missing.size, missing)
            }
            .sortedWith(
                compareBy({ it.missingCount }, { it.recipeId in plannedIds }, { it.name }),
            )
}
