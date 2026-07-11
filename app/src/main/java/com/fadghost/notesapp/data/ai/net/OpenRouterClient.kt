package com.fadghost.notesapp.data.ai.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * OpenRouter API client (PLAN.md §5). Two call styles:
 *  - [streamCleanup] — streaming SSE chat completions, incremental deltas.
 *  - [extractStructured] — non-streaming with `response_format` json_schema.
 * Plus [listModels] / [testConnection] for the Settings picker.
 *
 * Per-request cancellation falls out of coroutine cancellation (cancel the
 * collecting job → the HTTP read unwinds). Retries use exponential backoff on
 * 429/5xx. All failures surface as typed [OpenRouterError]. The API key is only
 * ever passed as a bearer header and is never logged.
 */
class OpenRouterClient(
    private val http: HttpClient,
    private val json: Json,
    private val config: Config = Config()
) {
    data class Config(
        val baseUrl: String = "https://openrouter.ai/api/v1",
        /** PLAN.md §5 — HTTP-Referer / X-Title identify the app to OpenRouter. */
        val referer: String = "https://github.com/fadghost/notesapp",
        val title: String = "Notesapp",
        val maxRetries: Int = 3,
        val baseBackoffMs: Long = 500
    )

    data class StructuredResult(val content: String, val usage: Usage?)

    /** One incremental item from a streamed completion. */
    sealed interface Stream {
        data class Delta(val text: String) : Stream
        data class Completed(val usage: Usage?) : Stream
    }

    // --- Models -----------------------------------------------------------------

    /** GET /models. Used by the Settings picker and the Test-connection button. */
    suspend fun listModels(apiKey: String): List<OpenRouterModel> = withRetry {
        val resp = http.get("${config.baseUrl}/models") { authHeaders(apiKey) }
        if (!resp.status.isSuccess()) throw mapError(resp, model = null)
        runCatching { resp.body<ModelsResponse>().data }
            .getOrElse { throw OpenRouterError.Parse(it.message) }
    }

    /** Test-connection helper: returns the model count on success, typed error otherwise. */
    suspend fun testConnection(apiKey: String): Result<Int> =
        runCatching { listModels(apiKey).size }

    // --- Streaming chat (Clean-up) ----------------------------------------------

    /**
     * Stream a chat completion. Emits [Stream.Delta] per token chunk then a final
     * [Stream.Completed] with usage; throws [OpenRouterError] on failure. The
     * connection attempt is retried on 429/5xx; once bytes flow we do not retry
     * (a partial stream is surfaced to the caller instead).
     */
    fun streamCleanup(apiKey: String, request: ChatRequest): Flow<Stream> = flow {
        val body = request.copy(stream = true, usage = UsageRequest(include = true))
        var attempt = 0
        while (true) {
            try {
                http.preparePost("${config.baseUrl}/chat/completions") {
                    authHeaders(apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.execute { resp ->
                    if (!resp.status.isSuccess()) {
                        val err = mapError(resp, model = request.model)
                        if (isRetryable(resp.status.value) && attempt < config.maxRetries) {
                            throw RetrySignal(err)
                        }
                        throw err
                    }
                    consumeSse(resp.bodyChannel()) { event -> emit(event) }
                }
                return@flow
            } catch (retry: RetrySignal) {
                attempt++
                delay(config.baseBackoffMs * (1L shl (attempt - 1)))
            } catch (c: CancellationException) {
                throw c
            } catch (e: OpenRouterError) {
                throw e
            } catch (e: Exception) {
                throw OpenRouterError.Network(e.message)
            }
        }
    }

    private suspend fun consumeSse(channel: ByteReadChannel, emit: suspend (Stream) -> Unit) {
        val parser = SseParser()
        var usage: Usage? = null
        suspend fun handle(payload: String?): Boolean {
            val data = payload ?: return false
            if (data == SseParser.DONE) return true
            val chunk = runCatching { json.decodeFromString<ChatStreamChunk>(data) }.getOrNull() ?: return false
            chunk.usage?.let { usage = it }
            chunk.firstDelta?.takeIf { it.isNotEmpty() }?.let { emit(Stream.Delta(it)) }
            return false
        }
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (handle(parser.accept(line))) { emit(Stream.Completed(usage)); return }
        }
        handle(parser.flush())
        emit(Stream.Completed(usage))
    }

    // --- Non-streaming completion (Extract + Clean-up reduce) -------------------

    /** Non-streaming completion; returns raw assistant content + usage for parsing. */
    suspend fun complete(apiKey: String, request: ChatRequest): StructuredResult = withRetry {
        val resp = http.preparePost("${config.baseUrl}/chat/completions") {
            authHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = false))
        }.execute { it.toChatResponseOrThrow(request.model) }
        val content = resp.firstContent
            ?: throw OpenRouterError.Parse("empty completion")
        StructuredResult(content, resp.usage)
    }

    // --- Plumbing ---------------------------------------------------------------

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(apiKey: String) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $apiKey")
            append("HTTP-Referer", config.referer)
            append("X-Title", config.title)
        }
        header(HttpHeaders.Accept, "application/json")
    }

    private suspend fun HttpResponse.toChatResponseOrThrow(model: String): ChatResponse {
        if (!status.isSuccess()) throw mapError(this, model)
        return runCatching { body<ChatResponse>() }.getOrElse { throw OpenRouterError.Parse(it.message) }
    }

    /** Retry wrapper for unary suspend calls on 429/5xx with exponential backoff. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (c: CancellationException) {
                throw c
            } catch (e: OpenRouterError) {
                val retryable = when (e) {
                    is OpenRouterError.RateLimited -> true
                    is OpenRouterError.Unknown -> e.status in 500..599
                    is OpenRouterError.Network -> true
                    else -> false
                }
                if (!retryable || attempt >= config.maxRetries) throw e
                attempt++
                delay(config.baseBackoffMs * (1L shl (attempt - 1)))
            } catch (e: Exception) {
                if (attempt >= config.maxRetries) throw OpenRouterError.Network(e.message)
                attempt++
                delay(config.baseBackoffMs * (1L shl (attempt - 1)))
            }
        }
    }

    private fun isRetryable(status: Int): Boolean = status == 429 || status in 500..599

    private suspend fun mapError(resp: HttpResponse, model: String?): OpenRouterError {
        val detail = runCatching { resp.bodyAsText().take(300) }.getOrNull()
        return when (resp.status.value) {
            401, 403 -> OpenRouterError.InvalidKey
            402 -> OpenRouterError.NoCredit
            429 -> OpenRouterError.RateLimited(
                resp.headersSafe("Retry-After")?.toLongOrNull()
            )
            400, 404 -> if (model != null) OpenRouterError.ModelUnavailable(model)
                        else OpenRouterError.Unknown(resp.status.value, detail)
            in 500..599 -> OpenRouterError.Unknown(resp.status.value, detail)
            else -> OpenRouterError.Unknown(resp.status.value, detail)
        }
    }

    private fun HttpResponse.headersSafe(name: String): String? = headers[name]

    private class RetrySignal(val error: OpenRouterError) : Exception()
}

/** Small shim so [OpenRouterClient] can read the streaming body channel. */
internal suspend fun HttpResponse.bodyChannel(): ByteReadChannel = body()
