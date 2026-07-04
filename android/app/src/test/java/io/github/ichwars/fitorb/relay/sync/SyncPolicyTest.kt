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
    fun lowRingBatteryStretchesSync() {
        assertTrue(SyncPolicy.shouldStretchForLowRingBattery(19))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(20))
        assertFalse(SyncPolicy.shouldStretchForLowRingBattery(null))
    }
}
