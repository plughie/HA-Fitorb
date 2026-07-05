package io.github.ichwars.fitorb.relay.ble

import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object FitorbHistoryProtocol {
    const val NORDIC_UART_SERVICE_UUID = "6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E"
    const val NORDIC_UART_WRITE_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    const val NORDIC_UART_NOTIFY_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
    const val COLMI_BIG_DATA_SERVICE_UUID = "DE5BF728-D711-4E47-AF26-65E3012A5DC7"
    const val COLMI_BIG_DATA_NOTIFY_UUID = "DE5BF729-D711-4E47-AF26-65E3012A5DC7"
    const val COLMI_BIG_DATA_WRITE_UUID = "DE5BF72A-D711-4E47-AF26-65E3012A5DC7"

    const val COMMAND_BATTERY = 0x03
    const val COMMAND_HEART_RATE_HISTORY = 0x15
    const val COMMAND_ACTIVITY_HISTORY = 0x43
    const val COMMAND_STRESS_HISTORY = 0x37
    const val COMMAND_HRV_HISTORY = 0x39
    const val BIG_DATA_MAGIC = 0xBC
    const val BIG_DATA_SLEEP_ID = 0x27
    const val BIG_DATA_SPO2_ID = 0x2A

    fun buildHeartRateHistoryCommand(
        targetDay: LocalDate,
        zoneId: ZoneId = ZoneOffset.UTC,
    ): ByteArray {
        val epochSeconds = targetDay
            .atStartOfDay(zoneId)
            .toEpochSecond()
            .toInt()
        return packet(
            byteArrayOf(COMMAND_HEART_RATE_HISTORY.toByte()) +
                epochSeconds.toLittleEndianBytes(),
        )
    }

    fun buildActivityHistoryCommand(dayOffset: Int): ByteArray {
        require(dayOffset in 0..0xFF) { "dayOffset must fit in one byte" }
        return packet(
            byteArrayOf(
                COMMAND_ACTIVITY_HISTORY.toByte(),
                dayOffset.toByte(),
                0x0F,
                0x00,
                0x5F,
                0x01,
            ),
        )
    }

    fun buildSplitSeriesHistoryCommand(command: Int, dayOffset: Int): ByteArray {
        require(command in 0..0xFF) { "command must fit in one byte" }
        require(dayOffset in 0..0xFF) { "dayOffset must fit in one byte" }
        return packet(byteArrayOf(command.toByte(), dayOffset.toByte()))
    }

    fun buildBigDataRequest(dataId: Int): ByteArray {
        require(dataId in 0..0xFF) { "dataId must fit in one byte" }
        return byteArrayOf(BIG_DATA_MAGIC.toByte(), dataId.toByte(), 0, 0, 0xFF.toByte(), 0xFF.toByte())
    }

    fun parseSleepPayload(
        payload: ByteArray,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): SleepHistoryParseResult {
        if (payload.isEmpty()) return SleepHistoryParseResult(emptyList())

        val sleepDays = payload.uByteAt(0)
        var offset = 1
        val samples = mutableListOf<RingCollectedSample>()
        repeat(sleepDays) {
            if (offset + 2 > payload.size) return@repeat

            val daysAgo = payload.uByteAt(offset)
            val dayPayloadLen = payload.uByteAt(offset + 1)
            val dayPayloadStart = offset + 2
            val dayPayloadEnd = dayPayloadStart + dayPayloadLen
            if (dayPayloadLen < 4 || dayPayloadEnd > payload.size) return@repeat

            val sourceDay = today.minusDays(daysAgo.toLong())
            val sleepStart = payload.sInt16At(offset + 2)
            val sleepEnd = payload.sInt16At(offset + 4)
            val durationMinutes = sleepDurationMinutes(sleepStart, sleepEnd)
            val start = sourceDay.atStartOfDay(ZoneOffset.UTC)
                .plusMinutes(sleepStart.toLong())
                .toInstant()
            val localDate = sourceDay.toString()

            var cursor = 0
            var awakeMinutes = 0
            var lightMinutes = 0
            var deepMinutes = 0
            var remMinutes = 0
            var periodOffset = dayPayloadStart + 4
            while (periodOffset + 2 <= dayPayloadEnd) {
                val stageRaw = payload.uByteAt(periodOffset)
                val minutes = payload.uByteAt(periodOffset + 1)
                periodOffset += 2
                val stage = sleepStage(stageRaw)
                if (stage != null) {
                    when (stage) {
                        "awake" -> awakeMinutes += minutes
                        "light" -> lightMinutes += minutes
                        "deep" -> deepMinutes += minutes
                        "rem" -> remMinutes += minutes
                    }
                    samples += RingCollectedSample(
                        metric = "sleep_stage",
                        timestamp = start.plus(cursor.toLong(), ChronoUnit.MINUTES),
                        value = RelaySampleValue.StringValue(stage),
                        unit = null,
                        localDate = localDate,
                        rawHex = byteArrayOf(stageRaw.toByte(), minutes.toByte()).toHex(),
                    )
                }
                cursor += minutes
            }

            samples += RingCollectedSample(
                metric = "sleep_summary",
                timestamp = start,
                value = RelaySampleValue.IntValue(durationMinutes),
                unit = "min",
                localDate = localDate,
                rawHex = payload.copyOfRange(offset, dayPayloadEnd).toHex(),
            )
            val asleepMinutes = (durationMinutes - awakeMinutes).coerceAtLeast(0)
            samples += RingCollectedSample("sleep_asleep", start, RelaySampleValue.IntValue(asleepMinutes), "min", localDate)
            samples += RingCollectedSample("sleep_awake", start, RelaySampleValue.IntValue(awakeMinutes), "min", localDate)
            samples += RingCollectedSample("sleep_light", start, RelaySampleValue.IntValue(lightMinutes), "min", localDate)
            samples += RingCollectedSample("sleep_deep", start, RelaySampleValue.IntValue(deepMinutes), "min", localDate)
            samples += RingCollectedSample("sleep_rem", start, RelaySampleValue.IntValue(remMinutes), "min", localDate)
            offset = dayPayloadEnd
        }
        return SleepHistoryParseResult(samples.filter { it.metric in RelayMetricNames.accepted })
    }

    private fun packet(payload: ByteArray): ByteArray {
        require(payload.size <= 15) { "payload must fit in one packet" }
        val command = ByteArray(16)
        payload.copyInto(command)
        command[15] = command.take(15).sumOf { it.toInt() and 0xFF }.and(0xFF).toByte()
        return command
    }
}

data class BigDataFrame(
    val dataId: Int,
    val dataLen: Int,
    val crc16: Int,
    val payload: ByteArray,
)

class BigDataFrameParser {
    private val buffer = mutableListOf<Byte>()

    fun consume(chunk: ByteArray): List<BigDataFrame> {
        buffer.addAll(chunk.asIterable())
        val frames = mutableListOf<BigDataFrame>()
        val headerLen = 6

        while (buffer.size >= headerLen) {
            if (buffer[0].toInt() and 0xFF != FitorbHistoryProtocol.BIG_DATA_MAGIC) {
                buffer.removeAt(0)
                continue
            }
            val dataId = buffer[1].toInt() and 0xFF
            val dataLen = (buffer[2].toInt() and 0xFF) or ((buffer[3].toInt() and 0xFF) shl 8)
            val packetLen = headerLen + dataLen
            if (buffer.size < packetLen) break

            val raw = buffer.take(packetLen).toByteArray()
            repeat(packetLen) { buffer.removeAt(0) }
            frames += BigDataFrame(
                dataId = dataId,
                dataLen = dataLen,
                crc16 = (raw[4].toInt() and 0xFF) or ((raw[5].toInt() and 0xFF) shl 8),
                payload = raw.copyOfRange(headerLen, raw.size),
            )
        }

        return frames
    }
}

data class SleepHistoryParseResult(
    val samples: List<RingCollectedSample>,
)

data class RingCollectedSample(
    val metric: String,
    val timestamp: Instant,
    val value: RelaySampleValue,
    val unit: String? = null,
    val localDate: String? = null,
    val rawHex: String? = null,
) {
    fun toRelaySampleDto(
        ringId: String,
        capturedAt: Instant,
    ): RelaySampleDto {
        val normalizedRingId = ringId.trim()
        return RelaySampleDto(
            sampleId = stableSampleId(normalizedRingId, metric, timestamp, value),
            ringId = normalizedRingId,
            metric = metric,
            timestamp = timestamp.toString(),
            value = value,
            unit = unit,
            capturedAt = capturedAt.toString(),
            localDate = localDate,
            rawHex = rawHex,
        )
    }
}

class HeartRateHistoryParser {
    private var expectedPackets = 0
    private var rangeMinutes = 5
    private var timestamp: Instant? = null
    private val raw = mutableListOf<Int>()

    fun consume(packet: ByteArray): List<RingCollectedSample>? {
        if (packet.size != 16 || packet.uByteAt(0) != FitorbHistoryProtocol.COMMAND_HEART_RATE_HISTORY) {
            return null
        }

        val subtype = packet.uByteAt(1)
        if (subtype == 0xFF) {
            reset()
            return emptyList()
        }
        if (subtype == 0) {
            expectedPackets = packet.uByteAt(2)
            rangeMinutes = packet.uByteAt(3).coerceAtLeast(1)
            raw.clear()
            timestamp = null
            return null
        }
        if (subtype == 1) {
            timestamp = Instant.ofEpochSecond(packet.uInt32At(2))
            appendValues(packet.copyOfRange(6, 15))
            return if (expectedPackets <= 2) samplesAndReset() else null
        }

        appendValues(packet.copyOfRange(2, 15))
        return if (expectedPackets != 0 && subtype >= expectedPackets - 1) {
            samplesAndReset()
        } else {
            null
        }
    }

    fun finishPartial(): List<RingCollectedSample> = samplesAndReset()

    private fun appendValues(values: ByteArray) {
        raw += values.map { it.toInt() and 0xFF }
    }

    private fun samplesAndReset(): List<RingCollectedSample> {
        val start = timestamp ?: run {
            reset()
            return emptyList()
        }
        val localDate = start.atZone(ZoneOffset.UTC).toLocalDate().toString()
        val samples = raw.mapIndexedNotNull { index, value ->
            if (value <= 0) {
                null
            } else {
                RingCollectedSample(
                    metric = "heart_rate",
                    timestamp = start.plus((index * rangeMinutes).toLong(), ChronoUnit.MINUTES),
                    value = RelaySampleValue.IntValue(value),
                    unit = "bpm",
                    localDate = localDate,
                )
            }
        }
        reset()
        return samples
    }

    private fun reset() {
        expectedPackets = 0
        rangeMinutes = 5
        timestamp = null
        raw.clear()
    }
}

class SplitSeriesHistoryParser(
    private val metric: String,
    private val sourceDay: LocalDate,
    private val startOfDay: Instant = sourceDay.atStartOfDay(ZoneOffset.UTC).toInstant(),
) {
    private var rangeMinutes = 30
    private val raw = mutableListOf<Int>()

    fun consume(packet: ByteArray): List<RingCollectedSample>? {
        if (packet.size !in 15..16) return null

        val index = packet.uByteAt(1)
        if (index == 0xFF) {
            raw.clear()
            return emptyList()
        }
        if (index == 0) {
            rangeMinutes = packet.uByteAt(3).coerceAtLeast(1)
            raw.clear()
            return null
        }

        val values = if (index == 1) {
            packet.copyOfRange(3, minOf(packet.size, 15))
        } else {
            packet.copyOfRange(2, minOf(packet.size, 15))
        }
        raw += values.map { it.toInt() and 0xFF }
        return raw.mapIndexedNotNull { sampleIndex, value ->
            if (value <= 0) {
                null
            } else {
                RingCollectedSample(
                    metric = metric,
                    timestamp = startOfDay.plus((sampleIndex * rangeMinutes).toLong(), ChronoUnit.MINUTES),
                    value = RelaySampleValue.IntValue(value),
                    unit = null,
                    localDate = sourceDay.toString(),
                )
            }
        }
    }
}

class ActivityHistoryParser(
    private val sourceDay: LocalDate,
    private val timestamp: Instant = sourceDay.atStartOfDay(ZoneOffset.UTC).toInstant(),
) {
    private var newCalorieProtocol = false
    private var steps = 0
    private var caloriesRaw = 0
    private var distance = 0

    fun consume(packet: ByteArray): List<RingCollectedSample>? {
        if (packet.size != 16 || packet.uByteAt(0) != FitorbHistoryProtocol.COMMAND_ACTIVITY_HISTORY) {
            return null
        }
        when (packet.uByteAt(1)) {
            0xFF -> {
                reset()
                return activitySamples(steps = 0, calories = 0, distance = 0)
            }
            0xF0 -> {
                newCalorieProtocol = packet.uByteAt(3) == 0x01
                return null
            }
        }

        val month = bcdToDecimal(packet.uByteAt(2))
        val day = bcdToDecimal(packet.uByteAt(3))
        if (month !in 1..12 || day !in 1..31) return null

        var calories = packet.uByteAt(7) or (packet.uByteAt(8) shl 8)
        if (newCalorieProtocol) calories *= 10
        caloriesRaw += calories
        steps += packet.uByteAt(9) or (packet.uByteAt(10) shl 8)
        distance += packet.uByteAt(11) or (packet.uByteAt(12) shl 8)

        val isLastPacket = packet.uByteAt(5) == packet.uByteAt(6) - 1
        if (!isLastPacket) return null

        val result = activitySamples(
            steps = steps,
            calories = caloriesRaw / 1000,
            distance = distance,
        )
        reset()
        return result
    }

    private fun activitySamples(
        steps: Int,
        calories: Int,
        distance: Int,
    ): List<RingCollectedSample> =
        listOf(
            RingCollectedSample("steps", timestamp, RelaySampleValue.IntValue(steps), null, sourceDay.toString()),
            RingCollectedSample("calories", timestamp, RelaySampleValue.IntValue(calories), "kcal", sourceDay.toString()),
            RingCollectedSample("distance", timestamp, RelaySampleValue.IntValue(distance), "m", sourceDay.toString()),
        )

    private fun reset() {
        newCalorieProtocol = false
        steps = 0
        caloriesRaw = 0
        distance = 0
    }
}

object RelayMetricNames {
    val accepted = setOf(
        "steps",
        "calories",
        "distance",
        "heart_rate",
        "spo2",
        "stress",
        "sleep_stage",
        "sleep_summary",
        "sleep_asleep",
        "sleep_awake",
        "sleep_light",
        "sleep_deep",
        "sleep_rem",
        "battery",
        "charging",
    )
}

fun ParsedRingPacket.toCollectedSamples(timestamp: Instant): List<RingCollectedSample> =
    when (this) {
        is ParsedRingPacket.Battery -> listOf(
            RingCollectedSample("battery", timestamp, RelaySampleValue.IntValue(batteryLevel), "%"),
            RingCollectedSample("charging", timestamp, RelaySampleValue.BoolValue(isCharging)),
        )
        is ParsedRingPacket.Activity -> listOf(
            RingCollectedSample("steps", timestamp, RelaySampleValue.IntValue(steps)),
            RingCollectedSample("calories", timestamp, RelaySampleValue.IntValue(calories), "kcal"),
            RingCollectedSample("distance", timestamp, RelaySampleValue.IntValue(distance), "m"),
        )
        is ParsedRingPacket.HeartRate -> value?.let {
            listOf(RingCollectedSample("heart_rate", timestamp, RelaySampleValue.IntValue(it), "bpm"))
        } ?: emptyList()
        is ParsedRingPacket.Spo2 -> value?.let {
            listOf(RingCollectedSample("spo2", timestamp, RelaySampleValue.IntValue(it), "%"))
        } ?: emptyList()
        is ParsedRingPacket.Stress -> value?.let {
            listOf(RingCollectedSample("stress", timestamp, RelaySampleValue.IntValue(it)))
        } ?: emptyList()
    }

private fun stableSampleId(
    ringId: String,
    metric: String,
    timestamp: Instant,
    value: RelaySampleValue,
): String {
    val valueText = value.jsonElement.toString()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$ringId|$metric|$timestamp|$valueText".toByteArray())
        .toHex()
        .take(24)
    return "ar-$digest"
}

private fun Int.toLittleEndianBytes(): ByteArray = byteArrayOf(
    (this and 0xFF).toByte(),
    ((this shr 8) and 0xFF).toByte(),
    ((this shr 16) and 0xFF).toByte(),
    ((this shr 24) and 0xFF).toByte(),
)

private fun ByteArray.uByteAt(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.uInt32At(index: Int): Long =
    (uByteAt(index).toLong()) or
        (uByteAt(index + 1).toLong() shl 8) or
        (uByteAt(index + 2).toLong() shl 16) or
        (uByteAt(index + 3).toLong() shl 24)

private fun ByteArray.sInt16At(index: Int): Int {
    val value = uByteAt(index) or (uByteAt(index + 1) shl 8)
    return if (value and 0x8000 != 0) value - 0x10000 else value
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

private fun sleepDurationMinutes(start: Int, end: Int): Int =
    if (end <= start) {
        (24 * 60 - start) + end
    } else {
        end - start
    }

private fun sleepStage(raw: Int): String? =
    when (raw) {
        2 -> "light"
        3 -> "deep"
        4 -> "rem"
        5 -> "awake"
        else -> null
    }

private fun bcdToDecimal(value: Int): Int =
    (((value shr 4) and 0x0F) * 10) + (value and 0x0F)
