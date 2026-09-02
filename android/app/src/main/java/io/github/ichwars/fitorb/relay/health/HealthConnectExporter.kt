package io.github.ichwars.fitorb.relay.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import java.time.Instant
import java.time.ZoneId

class HealthConnectExporter(context: Context) {
    private val appContext = context.applicationContext
    private val client by lazy { HealthConnectClient.getOrCreate(appContext) }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean = isAvailable &&
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun export(samples: List<RelaySampleDto>): Int {
        if (!hasPermissions()) return 0
        val records = samples.mapNotNull(::quantityRecord) + sleepRecords(samples)
        if (records.isNotEmpty()) client.insertRecords(records)
        return records.size
    }

    private fun quantityRecord(sample: RelaySampleDto): Record? {
        val time = runCatching { Instant.parse(sample.timestamp) }.getOrNull() ?: return null
        val value = sample.numericValue() ?: return null
        if (sample.metric in setOf("steps", "distance", "calories") && !sample.isCompletedDay()) return null
        val zone = ZoneId.systemDefault().rules.getOffset(time)
        val intervalEnd = if (sample.metric in setOf("steps", "distance", "calories")) {
            time.plusSeconds(86_400)
        } else {
            time.plusSeconds(1)
        }
        val metadata = metadata(sample.sampleId)
        return when (sample.metric) {
            "heart_rate" -> HeartRateRecord(
                startTime = time,
                startZoneOffset = zone,
                endTime = intervalEnd,
                endZoneOffset = zone,
                samples = listOf(HeartRateRecord.Sample(time, value.toLong())),
                metadata = metadata,
            )
            "spo2" -> OxygenSaturationRecord(time = time, zoneOffset = zone, percentage = Percentage(value), metadata = metadata)
            "steps" -> StepsRecord(startTime = time, startZoneOffset = zone, endTime = intervalEnd, endZoneOffset = zone, count = value.toLong(), metadata = metadata)
            "distance" -> DistanceRecord(startTime = time, startZoneOffset = zone, endTime = intervalEnd, endZoneOffset = zone, distance = Length.meters(value), metadata = metadata)
            "calories" -> ActiveCaloriesBurnedRecord(startTime = time, startZoneOffset = zone, endTime = intervalEnd, endZoneOffset = zone, energy = Energy.kilocalories(value), metadata = metadata)
            else -> null
        }
    }

    private fun sleepRecords(samples: List<RelaySampleDto>): List<SleepSessionRecord> = samples
        .filter { it.metric == "sleep_stage" }
        .groupBy { it.localDate ?: it.timestamp.take(10) }
        .mapNotNull { (day, values) ->
            val stages = values.mapNotNull { sample ->
                val start = runCatching { Instant.parse(sample.timestamp) }.getOrNull() ?: return@mapNotNull null
                val minutes = sample.rawHex?.drop(2)?.take(2)?.toIntOrNull(16) ?: return@mapNotNull null
                val stage = when ((sample.value as? RelaySampleValue.StringValue)?.value) {
                    "awake" -> SleepSessionRecord.STAGE_TYPE_AWAKE
                    "light" -> SleepSessionRecord.STAGE_TYPE_LIGHT
                    "deep" -> SleepSessionRecord.STAGE_TYPE_DEEP
                    "rem" -> SleepSessionRecord.STAGE_TYPE_REM
                    else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
                }
                SleepSessionRecord.Stage(start, start.plusSeconds(minutes * 60L), stage)
            }.sortedBy { it.startTime }
            if (stages.isEmpty()) return@mapNotNull null
            val start = stages.first().startTime
            val end = stages.maxOf { it.endTime }
            val zone = ZoneId.systemDefault().rules.getOffset(start)
            SleepSessionRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = end,
                endZoneOffset = zone,
                title = "COLMI ring sleep",
                notes = null,
                metadata = metadata("fitorb-sleep-$day"),
                stages = stages,
            )
        }

    private fun metadata(id: String) = Metadata.autoRecorded(
        device = Device(type = Device.TYPE_RING, manufacturer = "COLMI", model = "R12"),
        clientRecordId = id,
    )

    companion object {
        val permissions = setOf(
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
        )
    }
}

private fun RelaySampleDto.numericValue(): Double? = when (val item = value) {
    is RelaySampleValue.IntValue -> item.value.toDouble()
    is RelaySampleValue.DoubleValue -> item.value
    is RelaySampleValue.StringValue -> item.value.toDoubleOrNull()
    is RelaySampleValue.BoolValue -> null
}

private fun RelaySampleDto.isCompletedDay(): Boolean {
    val day = localDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
    return day.isBefore(java.time.LocalDate.now())
}
