package io.github.abdullahamr871.neuropad.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRumbleLevelTest {
    @Test
    fun silentMotorsMeanOff() = assertEquals(0, PhoneRumbleLevel.of(0, 0))

    @Test
    fun strongerMotorLeadsAndTheOtherAddsALittle() {
        assertEquals(200, PhoneRumbleLevel.of(200, 0))
        assertEquals(200 + 25, PhoneRumbleLevel.of(100, 200))
        assertEquals(255, PhoneRumbleLevel.of(255, 255))
    }

    @Test
    fun anyRumbleIsAudible() = assertEquals(1, PhoneRumbleLevel.of(1, 0))

    @Test
    fun outOfRangeValuesAreClamped() = assertEquals(255, PhoneRumbleLevel.of(999, -5))

    @Test
    fun startsAndStopsAlwaysGoThrough() {
        assertTrue(PhoneRumbleLevel.shouldUpdate(0, 5))
        assertTrue(PhoneRumbleLevel.shouldUpdate(120, 0))
    }

    @Test
    fun smallChangesDoNotRestartTheMotor() {
        assertFalse(PhoneRumbleLevel.shouldUpdate(120, 120))
        assertFalse(PhoneRumbleLevel.shouldUpdate(120, 125))
        assertTrue(PhoneRumbleLevel.shouldUpdate(120, 140))
    }
}
