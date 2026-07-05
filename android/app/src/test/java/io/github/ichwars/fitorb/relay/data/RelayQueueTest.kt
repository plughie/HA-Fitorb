package io.github.ichwars.fitorb.relay.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
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

    @Test
    fun terminalStatesCannotBeCrossed() = runTest {
        dao.insertQueued(
            sample(
                sampleId = "sample-delivered",
                metric = "heart_rate",
                timestamp = "2026-07-03T09:55:00Z",
            )
        )
        dao.insertQueued(
            sample(
                sampleId = "sample-rejected",
                metric = "skin_temperature",
                timestamp = "2026-07-03T09:56:00Z",
            )
        )

        dao.markDelivered(listOf("sample-delivered"))
        dao.markRejected("sample-delivered", "late_reject")

        assertEquals(TerminalState(delivered = true), terminalState("sample-delivered"))

        dao.markRejected("sample-rejected", "invalid_metric")
        dao.markDelivered(listOf("sample-rejected"))

        assertEquals(
            TerminalState(delivered = false, rejectedReason = "invalid_metric"),
            terminalState("sample-rejected"),
        )
    }

    @Test
    fun latestSamplesForRingIncludesDeliveredAndExcludesRejectedOrOtherRings() = runTest {
        dao.insertQueued(
            sample(
                sampleId = "sample-old-heart",
                metric = "heart_rate",
                timestamp = "2026-07-03T09:55:00Z",
            )
        )
        dao.insertQueued(
            sample(
                sampleId = "sample-new-battery",
                metric = "battery",
                timestamp = "2026-07-03T10:00:00Z",
            )
        )
        dao.insertQueued(
            sample(
                sampleId = "sample-rejected",
                metric = "stress",
                timestamp = "2026-07-03T10:05:00Z",
            )
        )
        dao.insertQueued(
            sample(
                sampleId = "sample-other-ring",
                metric = "steps",
                timestamp = "2026-07-03T10:10:00Z",
                ringId = "11:22:33:44:55:66",
            )
        )

        dao.markDelivered(listOf("sample-old-heart", "sample-new-battery"))
        dao.markRejected("sample-rejected", "invalid_metric")

        assertEquals(
            listOf("sample-new-battery", "sample-old-heart"),
            dao.latestSamplesForRing("AA:BB:CC:DD:EE:FF", limit = 10).map { it.sampleId },
        )
    }

    private fun sample(
        sampleId: String,
        metric: String,
        timestamp: String,
        ringId: String = "AA:BB:CC:DD:EE:FF",
    ) = RelaySampleEntity(
        sampleId = sampleId,
        ringId = ringId,
        metric = metric,
        timestamp = timestamp,
        valueJson = "72",
        unit = "bpm",
        capturedAt = "2026-07-03T09:55:05Z",
    )

    private fun terminalState(sampleId: String): TerminalState {
        val query = SimpleSQLiteQuery(
            "SELECT delivered, rejectedReason FROM relay_samples WHERE sampleId = ?",
            arrayOf(sampleId),
        )
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return TerminalState(
                delivered = cursor.getInt(0) != 0,
                rejectedReason = if (cursor.isNull(1)) null else cursor.getString(1),
            )
        }
    }

    private data class TerminalState(
        val delivered: Boolean,
        val rejectedReason: String? = null,
    )
}
