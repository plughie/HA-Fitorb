package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import io.github.ichwars.fitorb.relay.data.RelaySampleValue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FitorbRelayApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun uploadRejectsHttpBaseUrlBeforeSendingRequest() = runTest {
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

            val exception = assertFailsWith<RelayUploadException> {
                api.upload(sampleBatch(), "secret-token")
            }

            assertEquals("HTTPS required", exception.message)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

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
            val api = FitorbRelayApi(server.url("/").toString(), requireHttps = false)
            val ack = api.upload(sampleBatch(), "secret-token")
            val request = server.takeRequest()

            assertEquals("Bearer secret-token", request.getHeader("Authorization"))
            assertEquals("/api/fitorb/relay/v1/samples", request.path)
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Content-Type")?.startsWith("application/json") == true)
            val requestJson = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("pixel-8", requestJson["relay_id"]?.jsonPrimitive?.content)
            assertEquals("AA:BB:CC:DD:EE:FF", requestJson["ring_id"]?.jsonPrimitive?.content)
            assertEquals(
                "sample-heart-1",
                requestJson["samples"]?.jsonArray?.first()?.jsonObject?.get("sample_id")?.jsonPrimitive?.content,
            )
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
            val api = FitorbRelayApi(server.url("/").toString(), requireHttps = false)

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
            val api = FitorbRelayApi(server.url("/").toString(), requireHttps = false)

            val exception = assertFailsWith<RelayUploadException> {
                api.upload(sampleBatch(), "secret-token")
            }

            assertEquals("HTTP 503", exception.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadWrapsMalformedAckResponse() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accepted":["sample-heart-1"]}""")
        )
        server.start()
        try {
            val api = FitorbRelayApi(server.url("/").toString(), requireHttps = false)

            val exception = assertFailsWith<RelayUploadException> {
                api.upload(sampleBatch(), "secret-token")
            }

            assertEquals("invalid response", exception.message)
            assertIs<SerializationException>(exception.cause)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadWrapsIoFailure() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor {
                    throw IOException("network unavailable")
                }
            )
            .build()
        val api = FitorbRelayApi("https://relay.example.test/", client)

        val exception = assertFailsWith<RelayUploadException> {
            api.upload(sampleBatch(), "secret-token")
        }

        assertEquals("upload failed", exception.message)
        assertIs<IOException>(exception.cause)
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
