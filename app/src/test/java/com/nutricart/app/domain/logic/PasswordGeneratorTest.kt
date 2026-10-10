package com.nutricart.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `generated passwords have the length and alphabet, and differ`() {
        val a = PasswordGenerator.generate()
        val b = PasswordGenerator.generate()
        assertEquals(PasswordGenerator.LENGTH, a.length)
        assertTrue(a.all { it in PasswordGenerator.ALPHABET })
        assertTrue(a != b)
        assertTrue(PasswordGenerator.isAcceptable(a))
    }

    @Test
    fun `short or blank passwords are refused`() {
        assertFalse(PasswordGenerator.isAcceptable("abc"))
        assertFalse(PasswordGenerator.isAcceptable("        "))
        assertTrue(PasswordGenerator.isAcceptable("correct horse"))
    }
}
