package io.github.ichwars.fitorb.relay.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FitorbProtocolTest {
    @Test
    fun buildCommandPadsToSixteenBytesAndAddsChecksum() {
        assertContentEquals(
            byteArrayOf(0x0A, 0x02, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x0C),
            FitorbProtocol.buildCommand("0a0200"),
        )
    }

    @Test
    fun buildCommandRejectsOddLengthHex() {
        assertFailsWith<IllegalArgumentException> {
            FitorbProtocol.buildCommand("0a0")
        }
    }

    @Test
    fun buildCommandRejectsOverlongHex() {
        assertFailsWith<IllegalArgumentException> {
            FitorbProtocol.buildCommand("0102030405060708090a0b0c0d0e0f10")
        }
    }

    @Test
    fun buildCommandRejectsInvalidHex() {
        assertFailsWith<IllegalArgumentException> {
            FitorbProtocol.buildCommand("0g")
        }
    }

    @Test
    fun buildCommandRejectsSignedHexChunks() {
        assertFailsWith<IllegalArgumentException> {
            FitorbProtocol.buildCommand("+f")
        }
        assertFailsWith<IllegalArgumentException> {
            FitorbProtocol.buildCommand("-1")
        }
    }

    @Test
    fun buildCommandAcceptsMaxLengthPayloadAndAddsChecksum() {
        assertContentEquals(
            packet(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 120),
            FitorbProtocol.buildCommand("0102030405060708090a0b0c0d0e0f"),
        )
    }

    @Test
    fun buildCommandChecksumWrapsModuloTwoHundredFiftySix() {
        assertContentEquals(
            packet(255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0xf1),
            FitorbProtocol.buildCommand("ffffffffffffffffffffffffffffff"),
        )
    }

    @Test
    fun parseBatteryPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x03, 71, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 75),
        )

        assertEquals(ParsedRingPacket.Battery(71, true), parsed)
    }

    @Test
    fun parseInvalidNotificationLengthReturnsNull() {
        assertNull(FitorbProtocol.parseNotification(packet(0x03, 71, 1)))
    }

    @Test
    fun parseUnknownPacketReturnsNull() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0xff, 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )

        assertNull(parsed)
    }

    @Test
    fun parseActivityPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x73, 0x12, 0, 11, 239, 2, 34, 9, 0, 7, 207, 0, 0, 0, 0, 130),
        )

        assertEquals(ParsedRingPacket.Activity(steps = 3055, calories = 139, distance = 1999), parsed)
    }

    @Test
    fun parseHeartRateResultPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x01, 0x01, 64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 207),
        )

        assertEquals(ParsedRingPacket.HeartRate(value = 64, running = false), parsed)
    }

    @Test
    fun parseRealtimeHeartRatePacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x01, 0x00, 72, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xb2),
        )

        assertEquals(ParsedRingPacket.HeartRate(value = 72, running = false), parsed)
    }

    @Test
    fun parseRunningHealthPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x01, 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x6b),
        )

        assertEquals(ParsedRingPacket.HeartRate(value = null, running = true), parsed)
    }

    @Test
    fun parseNoValueHealthPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x01, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x6a),
        )

        assertEquals(ParsedRingPacket.HeartRate(value = null, running = false), parsed)
    }

    @Test
    fun parseSpo2Packet() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x03, 0x01, 98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5),
        )

        assertEquals(ParsedRingPacket.Spo2(value = 98, running = false), parsed)
    }

    @Test
    fun parseStressPacket() {
        val parsed = FitorbProtocol.parseNotification(
            packet(0x69, 0x08, 0x01, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 164),
        )

        assertEquals(ParsedRingPacket.Stress(value = 32, running = false), parsed)
    }

    private fun packet(vararg bytes: Int): ByteArray = bytes.map { it.toByte() }.toByteArray()
}
