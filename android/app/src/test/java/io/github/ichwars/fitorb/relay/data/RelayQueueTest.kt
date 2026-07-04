package io.github.ichwars.fitorb.relay.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class RelayQueueTest {
    private lateinit var database: RelayDatabase
    private lateinit var dao: RelaySampleDao

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RelayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.relaySampleDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun queueIgnoresDuplicatesAndOnlyReturnsPendingSamples() = runTest {
        val first = sample(
            sampleId = "sample-heart-1",
            metric = "heart_rate",
            timestamp = "2026-07-03T09:55:00Z",
        )
        val second = sample(
            sampleId = "sample-temp-1",
            metric = "skin_temperature",
            timestamp = "2026-07-03T09:56:00Z",
        )
        val duplicateFirst = first.copy(
            metric = "duplicate_metric",
            timestamp = "2026-07-03T09:00:00Z",
        )

        dao.insertQueued(second)
        dao.insertQueued(first)
        dao.insertQueued(duplicateFirst)

        assertEquals(listOf("sample-heart-1"), dao.pendingBatch(limit = 1).map { it.sampleId })

        val pending = dao.pendingBatch(limit = 10)
        assertEquals(listOf("sample-heart-1", "sample-temp-1"), pending.map { it.sampleId })
        assertEquals("heart_rate", pending.first().metric)

        dao.markDelivered(listOf("sample-heart-1"))

        assertEquals(listOf("sample-temp-1"), dao.pendingBatch(limit = 10).map { it.sampleId })

        dao.markRejected("sample-temp-1", "invalid_metric")

        assertTrue(dao.pendingBatch(limit = 10).isEmpty())
    }

    private fun sample(
        sampleId: String,
        metric: String,
        timestamp: String,
    ) = RelaySampleEntity(
        sampleId = sampleId,
        ringId = "AA:BB:CC:DD:EE:FF",
        metric = metric,
        timestamp = timestamp,
        valueJson = "72",
        unit = "bpm",
        capturedAt = "2026-07-03T09:55:05Z",
    )
}
