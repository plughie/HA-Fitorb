package io.github.ichwars.fitorb.relay.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ichwars.fitorb.relay.ble.FitorbBleCollector
import io.github.ichwars.fitorb.relay.ble.RingCollectedSample
import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelayDatabase
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RelaySyncRunnerTest {
    private lateinit var database: RelayDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RelayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun runCollectsQueuesUploadsAndAppliesAck() = runTest {
        var uploadedBatch: RelayBatchDto? = null
        val collector = FitorbBleCollector { ringId ->
            listOf(
                RingCollectedSample(
                    metric = "heart_rate",
                    timestamp = Instant.parse("2026-07-03T09:55:00Z"),
                    value = RelaySampleValue.IntValue(72),
                    unit = "bpm",
                    localDate = "2026-07-03",
                ).toRelaySampleDto(ringId, Instant.parse("2026-07-03T09:56:00Z")),
                RingCollectedSample(
                    metric = "battery",
                    timestamp = Instant.parse("2026-07-03T09:56:00Z"),
                    value = RelaySampleValue.IntValue(88),
                    unit = "%",
                ).toRelaySampleDto(ringId, Instant.parse("2026-07-03T09:56:00Z")),
            )
        }
        val uploader = RelayUploader { batch, _token ->
            uploadedBatch = batch
            RelayAckDto(
                accepted = listOf(batch.samples[0].sampleId),
                duplicates = listOf(batch.samples[1].sampleId),
                rejected = emptyList(),
                serverTime = "2026-07-03T09:57:00Z",
            )
        }
        val runner = RelaySyncRunner(
            dao = database.relaySampleDao(),
            collector = collector,
            uploader = uploader,
            clock = { Instant.parse("2026-07-03T09:57:00Z") },
            appVersion = "0.1.0",
        )

        val result = runner.run(settings())

        assertEquals(1, result.ack.accepted.size)
        assertEquals(1, result.ack.duplicates.size)
        assertEquals(2, result.capturedSamples.size)
        assertEquals(2, result.uploadedSamples.size)
        assertEquals("pixel-8", uploadedBatch?.relayId)
        assertEquals("AA:BB:CC:DD:EE:FF", uploadedBatch?.ringId)
        assertEquals(2, uploadedBatch?.samples?.size)
        assertEquals(2, uploadedBatch?.backlog)
        assertTrue(database.relaySampleDao().pendingBatch(limit = 10).isEmpty())
    }

    private fun settings() = RelaySettings(
        homeAssistantUrl = "https://ha.example.net",
        relayToken = "relay-token",
        relayId = "pixel-8",
        ringId = "AA:BB:CC:DD:EE:FF",
        syncIntervalMinutes = 10,
    )
}
