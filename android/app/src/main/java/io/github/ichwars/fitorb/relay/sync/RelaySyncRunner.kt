package io.github.ichwars.fitorb.relay.sync

import io.github.ichwars.fitorb.relay.ble.FitorbBleCollector
import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDao
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.toDto
import io.github.ichwars.fitorb.relay.data.toEntity
import io.github.ichwars.fitorb.relay.network.FitorbRelayApi
import io.github.ichwars.fitorb.relay.settings.RelaySettings
import java.time.Instant

private const val PROTOCOL_VERSION = 1
private const val MAX_UPLOAD_BATCH = 200

fun interface RelayUploader {
    suspend fun upload(batch: RelayBatchDto, token: String): RelayAckDto
}

data class RelaySyncResult(
    val ack: RelayAckDto,
    val capturedSamples: List<RelaySampleDto>,
    val uploadedSamples: List<RelaySampleDto>,
)

class RelaySyncRunner(
    private val dao: RelaySampleDao,
    private val collector: FitorbBleCollector,
    private val uploader: RelayUploader,
    private val clock: () -> Instant = Instant::now,
    private val appVersion: String,
) {
    suspend fun run(settings: RelaySettings): RelaySyncResult {
        val ringId = settings.ringId.trim()
        require(ringId.isNotEmpty()) { "Ring ID required" }
        val relayId = settings.relayId.trim().ifBlank { "android-relay" }

        val capturedSamples = collector.collectOnce(ringId)
        capturedSamples.forEach { dao.insertQueued(it.toEntity()) }

        val pending = dao.pendingBatchForRing(ringId, MAX_UPLOAD_BATCH)
        val uploadedSamples = pending.map { it.toDto() }
        val batch = RelayBatchDto(
            relayId = relayId,
            ringId = ringId,
            appVersion = appVersion,
            protocolVersion = PROTOCOL_VERSION,
            sentAt = clock().toString(),
            samples = uploadedSamples,
            backlog = dao.pendingCountForRing(ringId),
        )
        val ack = uploader.upload(batch, settings.relayToken)
        val delivered = ack.accepted + ack.duplicates
        if (delivered.isNotEmpty()) {
            dao.markDelivered(delivered)
        }
        ack.rejected.forEach { rejected ->
            dao.markRejected(rejected.sampleId, rejected.reason)
        }
        return RelaySyncResult(
            ack = ack,
            capturedSamples = capturedSamples,
            uploadedSamples = uploadedSamples,
        )
    }
}

fun fitorbRelayUploader(baseUrl: String): RelayUploader =
    RelayUploader { batch, token ->
        FitorbRelayApi(baseUrl).upload(batch, token)
    }
