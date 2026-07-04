package io.github.ichwars.fitorb.relay.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPolicyTest {
    @Test
    fun backoffCapsAtSixtyMinutes() {
        assertEquals(10, SyncPolicy.nextDelayMinutes(0, 10))
        assertEquals(20, SyncPolicy.nextDelayMinutes(1, 10))
        assertEquals(40, SyncPolicy.nextDelayMinutes(2, 10))
        assertEquals(60, SyncPolicy.nextDelayMinutes(3, 10))
        assertEquals(60, SyncPolicy.nextDelayMinutes(9, 10))
    }

    @Test
    fun negativeFailuresReturnClampedBaseInterval() {
        assertEquals(10, SyncPolicy.nextDelayMinutes(-1, 10))
        assertEquals(1, SyncPolicy.nextDelayMinutes(-2, 0))
        assertEquals(60, SyncPolicy.nextDelayMinutes(-3, 90))
    }

    @Test
    fun configuredIntervalClampsToOneMinuteMinimum() {
        assertEquals(1, SyncPolicy.nextDelayMinutes(0, 0))
        assertEquals(1, SyncPolicy.nextDelayMinutes(0, -5))
    }

    @Test
    fun configuredIntervalClampsToSixtyMinuteMaximum() {
        assertEquals(60, SyncPolicy.nextDelayMinutes(0, 61))
        assertEquals(60, SyncPolicy.nextDelayMinutes(1, 61))
    }

    @Test
    fun tinyConfiguredIntervalBacksOffFromClampedBase() {
        assertEquals(1, SyncPolicy.nextDelayMinutes(0, 1))
        assertEquals(2, SyncPolicy.nextDelayMinutes(1, 1))
        assertEquals(4, SyncPolicy.nextDelayMinutes(2, 1))
        assertEquals(8, SyncPolicy.nextDelayMinutes(3, 1))
    }

    @Test
    fun lowRingBatteryStretchesSync() {
        assertTrue(SyncPolicy.shouldStretchForLowRingBattery(19))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(20))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(null))
    }
}
