package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRankerTest {

    private fun rank(items: List<String>, counts: Map<String, Int>): List<String> =
        ProductRanker.rank(items, idOf = { it }, useCounts = counts)

    @Test
    fun `most-used product moves to the top`() {
        val result = rank(listOf("bread", "milk", "eggs"), mapOf("eggs" to 5, "milk" to 2))
        assertEquals(listOf("eggs", "milk", "bread"), result)
    }

    @Test
    fun `never-logged products keep their original order`() {
        // All counts are zero -> the API relevance order must survive untouched.
        val incoming = listOf("first", "second", "third")
        assertEquals(incoming, rank(incoming, emptyMap()))
    }

    @Test
    fun `ties keep the original order too`() {
        val result = rank(listOf("a", "b", "c"), mapOf("a" to 3, "b" to 3, "c" to 3))
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `unknown ids count as zero`() {
        val result = rank(listOf("new", "known"), mapOf("known" to 1))
        assertEquals(listOf("known", "new"), result)
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<String>(), rank(emptyList(), mapOf("x" to 9)))
    }
}
