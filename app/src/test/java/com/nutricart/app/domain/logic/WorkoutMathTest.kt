package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutMathTest {

    private val delta = 0.001

    @Test
    fun `20 minutes of treadmill walking at 70 kg is about 100 kcal`() {
        // 4.3 MET * 70 kg * (20 / 60) h = 100.333...
        val kcal = WorkoutMath.kcalForDuration(WorkoutType.TREADMILL_WALK, 70.0, minutes = 20)
        assertEquals(100.333, kcal, delta)
    }

    @Test
    fun `one hour of running at 70 kg is MET times weight`() {
        val kcal = WorkoutMath.kcalForDuration(WorkoutType.RUNNING, 70.0, minutes = 60)
        assertEquals(9.8 * 70.0, kcal, delta)
    }

    @Test
    fun `zero minutes burns zero`() {
        val kcal = WorkoutMath.kcalForDuration(WorkoutType.CYCLING, 80.0, minutes = 0)
        assertEquals(0.0, kcal, delta)
    }

    @Test
    fun `a heavier person burns more in the same time`() {
        val at60 = WorkoutMath.kcalForDuration(WorkoutType.CYCLING, 60.0, minutes = 30)
        val at90 = WorkoutMath.kcalForDuration(WorkoutType.CYCLING, 90.0, minutes = 30)
        assertEquals(at60 * 1.5, at90, delta)
    }

    @Test
    fun `100 push-ups at the reference weight use the constant directly`() {
        val kcal = WorkoutMath.kcalForReps(WorkoutType.PUSH_UPS, 70.0, reps = 100)
        assertEquals(36.0, kcal, delta)
    }

    @Test
    fun `push-ups scale linearly with body weight`() {
        // 105 kg = 1.5x the reference weight -> 1.5x the kcal.
        val kcal = WorkoutMath.kcalForReps(WorkoutType.PUSH_UPS, 105.0, reps = 100)
        assertEquals(54.0, kcal, delta)
    }

    @Test
    fun `zero reps burns zero`() {
        val kcal = WorkoutMath.kcalForReps(WorkoutType.SQUATS, 70.0, reps = 0)
        assertEquals(0.0, kcal, delta)
    }

    @Test
    fun `a reps workout rejects the duration function`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutMath.kcalForDuration(WorkoutType.PUSH_UPS, 70.0, minutes = 10)
        }
    }

    @Test
    fun `a duration workout rejects the reps function`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutMath.kcalForReps(WorkoutType.RUNNING, 70.0, reps = 10)
        }
    }
}
