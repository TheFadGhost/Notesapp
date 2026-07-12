package com.fadghost.notesapp

import com.fadghost.notesapp.data.ai.net.OpenRouterClient
import com.fadghost.notesapp.data.ai.net.OpenRouterError
import com.fadghost.notesapp.data.ai.net.SilentAudioProbe
import com.fadghost.notesapp.data.ai.net.TranscriptionForm
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STT multipart request construction + endpoint behaviour (PLAN.md §5). No network:
 * the field set / filename / content-type is asserted on the built [PartData] list,
 * and the endpoint round-trip runs against Ktor MockEngine.
 */
class OpenRouterTranscriptionTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): OpenRouterClient {
        val engine = MockEngine { req -> handler(req) }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }
        return OpenRouterClient(http, json, OpenRouterClient.Config(maxRetries = 2, baseBackoffMs = 1))
    }

    @Test fun partsCarryModelLanguageAndAudioFile() {
        val parts = TranscriptionForm.parts(
            model = "qwen/qwen3-asr-flash-2026-02-10",
            audioBytes = ByteArray(64) { 1 },
            filename = "segment_000.m4a",
            language = "en"
        )
        val forms = parts.filterIsInstance<PartData.FormItem>().associate { it.name to it.value }
        assertEquals("qwen/qwen3-asr-flash-2026-02-10", forms["model"])
        assertEquals("en", forms["language"])

        val filePart = parts.first { it.name == "file" }
        assertEquals("audio/m4a", filePart.contentType.toString())
        val filename = filePart.contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
        assertEquals("segment_000.m4a", filename)
    }

    @Test fun transcribePostsMultipartToTranscriptionsEndpoint() = runTest {
        var seenPath: String? = null
        var seenContentType: String? = null
        val c = client { req ->
            seenPath = req.url.encodedPath
            seenContentType = req.body.contentType?.toString()
            respond("""{"text":"hello world"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val res = c.transcribe("key", ByteArray(8), "seg.m4a", "qwen/asr")
        assertEquals("hello world", res.text)
        assertTrue(seenPath!!.endsWith("/audio/transcriptions"))
        assertNotNull(seenContentType)
        assertTrue(seenContentType!!.startsWith("multipart/form-data"))
    }

    @Test fun transcribeMapsInvalidKeyFrom401() = runTest {
        val c = client { respond("nope", HttpStatusCode.Unauthorized, jsonHeaders) }
        val err = runCatching { c.transcribe("bad", ByteArray(4), "seg.m4a", "m") }.exceptionOrNull()
        assertTrue(err is OpenRouterError.InvalidKey)
    }

    @Test fun transcribeParsesEmptyTextGracefully() = runTest {
        val c = client { respond("""{"text":""}""", HttpStatusCode.OK, jsonHeaders) }
        val res = c.transcribe("k", ByteArray(4), "seg.m4a", "m")
        assertEquals("", res.text)
    }

    @Test fun transcribeMapsDeadModelFrom400WithMessage() = runTest {
        // Live-verified OpenRouter error shape for a retired/renamed model id (item 9):
        // {"error":{"message":"Model X does not exist","code":400}}.
        val body = """{"error":{"message":"Model ghost/model does not exist","code":400}}"""
        val c = client { respond(body, HttpStatusCode.BadRequest, jsonHeaders) }
        val err = runCatching { c.transcribe("k", ByteArray(4), "seg.m4a", "ghost/model") }.exceptionOrNull()
        assertTrue(err is OpenRouterError.ModelUnavailable)
        assertEquals("ghost/model", (err as OpenRouterError.ModelUnavailable).model)
    }

    // --- contentType override (Settings STT "Test" probe, item 9) ---------------

    @Test fun transcribeSendsOverriddenContentTypeForTestProbe() = runTest {
        var seenContentType: String? = null
        val c = client { req ->
            // The multipart body's own file-part content-type isn't inspectable via
            // req.body.contentType() (that reports the outer multipart boundary type),
            // so assert via TranscriptionForm directly below; this call just proves the
            // override plumbs through without throwing / changing the endpoint hit.
            seenContentType = req.body.contentType?.toString()
            respond("""{"text":""}""", HttpStatusCode.OK, jsonHeaders)
        }
        val res = c.transcribe(
            "k", SilentAudioProbe.bytes(), SilentAudioProbe.FILENAME, "openai/whisper-1",
            contentType = SilentAudioProbe.CONTENT_TYPE
        )
        assertEquals("", res.text)
        assertTrue(seenContentType!!.startsWith("multipart/form-data"))
    }

    @Test fun transcriptionFormPartsCarryOverriddenContentType() {
        val parts = TranscriptionForm.parts(
            model = "openai/whisper-1",
            audioBytes = SilentAudioProbe.bytes(),
            filename = SilentAudioProbe.FILENAME,
            language = "en",
            contentType = SilentAudioProbe.CONTENT_TYPE
        )
        val filePart = parts.first { it.name == "file" }
        assertEquals("audio/wav", filePart.contentType.toString())
    }

    // --- SilentAudioProbe (Settings STT "Test" probe bytes, item 9) -------------

    @Test fun silentAudioProbeBytesFormWellFormedWavHeader() {
        val bytes = SilentAudioProbe.bytes()
        assertTrue(bytes.size > 44) // header + at least some sample data
        fun ascii(range: IntRange) = String(bytes, range.first, range.last - range.first + 1, Charsets.US_ASCII)
        assertEquals("RIFF", ascii(0..3))
        assertEquals("WAVE", ascii(8..11))
        assertEquals("fmt ", ascii(12..15))
        assertEquals("data", ascii(36..39))
    }

    @Test fun silentAudioProbeBytesAreDeterministic() {
        assertEquals(SilentAudioProbe.bytes().size, SilentAudioProbe.bytes().size)
        assertTrue(SilentAudioProbe.bytes().contentEquals(SilentAudioProbe.bytes()))
    }
}
