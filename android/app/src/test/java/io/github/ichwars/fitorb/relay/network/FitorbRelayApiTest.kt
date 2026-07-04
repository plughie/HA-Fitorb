package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FitorbRelayApiTest {
    @Test
    fun uploadSendsBearerTokenAndParsesAck() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"accepted":["sample-heart-1"],"duplicates":[],"rejected":[],"server_time":"2026-07-03T10:01:00Z"}"""
                )
        )
        server.start()
        try {
            val api = FitorbRelayApi(server.url("/").toString())
            val ack = api.upload(sampleBatch(), "secret-token")
            val request = server.takeRequest()

            assertEquals("Bearer secret-token", request.getHeader("Authorization"))
            assertEquals("/api/fitorb/relay/v1/samples", request.path)
            assertEquals(listOf("sample-heart-1"), ack.accepted)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadThrowsRelayUploadExceptionForEmptySuccessfulBody() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setBody("")
        )
        server.start()
        try {
            val api = FitorbRelayApi(server.url("/").toString())

            val exception = assertFailsWith<RelayUploadException> {
                api.upload(sampleBatch(), "secret-token")
            }

            assertEquals("empty response", exception.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadThrowsRelayUploadExceptionForHttpError() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error":"temporarily_unavailable"}""")
        )
        server.start()
        try {
            val api = FitorbRelayApi(server.url("/").toString())

            val exception = assertFailsWith<RelayUploadException> {
                api.upload(sampleBatch(), "secret-token")
            }

            assertEquals("HTTP 503", exception.message)
        } finally {
            server.shutdown()
        }
    }

    private fun sampleBatch() = RelayBatchDto(
        relayId = "pixel-8",
        ringId = "AA:BB:CC:DD:EE:FF",
        appVersion = "0.1.0",
        protocolVersion = 1,
        sentAt = "2026-07-03T10:00:00Z",
        samples = listOf(
            RelaySampleDto(
                sampleId = "sample-heart-1",
                ringId = "AA:BB:CC:DD:EE:FF",
                metric = "heart_rate",
                timestamp = "2026-07-03T09:55:00Z",
                value = RelaySampleValue.IntValue(72),
                unit = "bpm",
                capturedAt = "2026-07-03T09:55:05Z",
            )
        ),
    )
}
