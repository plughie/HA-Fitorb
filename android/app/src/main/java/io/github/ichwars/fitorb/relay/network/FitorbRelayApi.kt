package io.github.ichwars.fitorb.relay.network

import io.github.ichwars.fitorb.relay.data.RelayAckDto
import io.github.ichwars.fitorb.relay.data.RelayBatchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

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
                .url(relaySamplesUrl())
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (exception: IOException) {
                throw RelayUploadException("upload failed", exception)
            }
            response.use {
                if (!response.isSuccessful) {
                    throw RelayUploadException("HTTP ${response.code}")
                }
                val responseBody = try {
                    response.body?.string()
                } catch (exception: IOException) {
                    throw RelayUploadException("upload failed", exception)
                } ?: throw RelayUploadException("empty response")
                if (responseBody.isEmpty()) {
                    throw RelayUploadException("empty response")
                }
                try {
                    json.decodeFromString(RelayAckDto.serializer(), responseBody)
                } catch (exception: SerializationException) {
                    throw RelayUploadException("invalid response", exception)
                }
            }
        }

    private fun relaySamplesUrl() = baseUrl.toHttpUrl()
        .newBuilder()
        .addPathSegments("api/fitorb/relay/v1/samples")
        .build()
}

class RelayUploadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause.unwrapRelayUploadCause())

private fun Throwable?.unwrapRelayUploadCause(): Throwable? =
    if (this is RelayUploadException && this.cause != null) {
        this.cause
    } else {
        this
    }
