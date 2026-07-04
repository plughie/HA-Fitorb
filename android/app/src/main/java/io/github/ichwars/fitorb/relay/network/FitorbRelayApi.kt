package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class FitorbRelayApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun upload(batch: RelayBatchDto, token: String): RelayAckDto =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(RelayBatchDto.serializer(), batch)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/fitorb/relay/v1/samples")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RelayUploadException("HTTP ${response.code}")
                }
                val responseBody = response.body?.string()
                    ?: throw RelayUploadException("empty response")
                if (responseBody.isEmpty()) {
                    throw RelayUploadException("empty response")
                }
                json.decodeFromString(RelayAckDto.serializer(), responseBody)
            }
        }
}

class RelayUploadException(message: String) : RuntimeException(message)
