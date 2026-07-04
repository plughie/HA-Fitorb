package io.github.ichwars.fitorb.relay.ble

sealed interface ParsedRingPacket {
    data class Battery(val batteryLevel: Int, val isCharging: Boolean) : ParsedRingPacket
    data class Activity(val steps: Int, val calories: Int, val distance: Int) : ParsedRingPacket
    data class HeartRate(val value: Int?) : ParsedRingPacket
    data class Spo2(val value: Int?) : ParsedRingPacket
    data class Stress(val value: Int?) : ParsedRingPacket
}

object FitorbProtocol {
    fun buildCommand(hexPayload: String): ByteArray {
        require(hexPayload.length % 2 == 0) { "hex payload must have even length" }
        require(hexPayload.length <= 30) { "hex payload must fit in one packet" }
        val command = ByteArray(16)
        hexPayload.chunked(2).forEachIndexed { index, chunk ->
            command[index] = chunk.toInt(16).toByte()
        }
        command[15] = command.take(15).sumOf { it.toInt() and 0xff }.and(0xff).toByte()
        return command
    }

    fun parseNotification(payload: ByteArray): ParsedRingPacket? {
        if (payload.size != 16) return null
        val first = payload[0].toInt() and 0xff
        val second = payload[1].toInt() and 0xff
        if (first == 0x03) {
            return ParsedRingPacket.Battery(
                batteryLevel = payload[1].toInt() and 0xff,
                isCharging = payload[2].toInt() == 1,
            )
        }
        if (first == 0x73 && second == 0x12) {
            val steps = ((payload[2].toInt() and 0xff) shl 16) or
                ((payload[3].toInt() and 0xff) shl 8) or
                (payload[4].toInt() and 0xff)
            val calories = (((payload[5].toInt() and 0xff) shl 16) or
                ((payload[6].toInt() and 0xff) shl 8) or
                (payload[7].toInt() and 0xff)) / 1000
            val distance = ((payload[8].toInt() and 0xff) shl 16) or
                ((payload[9].toInt() and 0xff) shl 8) or
                (payload[10].toInt() and 0xff)
            return ParsedRingPacket.Activity(steps, calories, distance)
        }
        if (first == 0x69 && second == 0x01) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.HeartRate(value.takeIf { it > 0 })
        }
        if (first == 0x69 && second == 0x03) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.Spo2(value.takeIf { it > 0 })
        }
        if (first == 0x69 && second == 0x08) {
            val value = payload[3].toInt() and 0xff
            return ParsedRingPacket.Stress(value.takeIf { it > 0 })
        }
        return null
    }
}
