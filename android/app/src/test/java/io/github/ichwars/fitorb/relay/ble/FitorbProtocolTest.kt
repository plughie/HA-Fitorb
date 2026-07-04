package io.github.ichwars.fitorb.relay.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FitorbProtocolTest {
    @Test
    fun buildCommandPadsToSixteenBytesAndAddsChecksum() {
        assertContentEquals(
            byteArrayOf(0x0A, 0x02, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0C),
            FitorbProtocol.buildCommand("0a0200"),
        )
    }

    @Test
    fun parseBatteryPacket() {
        val parsed = FitorbProtocol.parseNotification(
            byteArrayOf(0x03, 71, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 75),
        )

        assertEquals(ParsedRingPacket.Battery(71, true), parsed)
    }
}
