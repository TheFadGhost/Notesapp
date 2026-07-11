package com.fadghost.notesapp.data.ai.net

import io.ktor.http.ContentDisposition
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.core.ByteReadPacket
import kotlinx.serialization.Serializable

/**
 * Wire types + multipart builder for the OpenRouter STT endpoint
 * (`POST /audio/transcriptions`, PLAN.md §5). Kept beside the chat DTOs and free of
 * Android/UI types so the multipart form (fields, filename, content-type) can be
 * asserted against Ktor MockEngine in a plain unit test.
 */
@Serializable
data class TranscriptionResponse(
    val text: String = "",
    /** Some STT models echo usage/cost; optional. */
    val usage: Usage? = null
)

/** Parsed transcription result surfaced to the pipeline. */
data class TranscriptionResult(val text: String, val usage: Usage?)

object TranscriptionForm {

    const val CONTENT_TYPE = "audio/m4a"

    /**
     * Build the `multipart/form-data` parts for a transcription request: the `model`
     * and `language` text fields plus the audio `file` part carrying [filename] and an
     * `audio/m4a` content type. Built as explicit [PartData] (rather than the `formData`
     * DSL) so the file part gets a single Content-Disposition with both `name` and
     * `filename` — the DSL would emit a second Content-Disposition and drop the
     * filename. Extracted so tests can assert fields/filename/content-type with no network.
     */
    fun parts(
        model: String,
        audioBytes: ByteArray,
        filename: String,
        language: String,
        contentType: String = CONTENT_TYPE
    ): List<PartData> {
        fun formField(name: String, value: String): PartData.FormItem = PartData.FormItem(
            value = value,
            dispose = {},
            partHeaders = Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.Name, name).toString()
                )
            }
        )
        val filePart = PartData.BinaryItem(
            provider = { ByteReadPacket(audioBytes) },
            dispose = {},
            partHeaders = Headers.build {
                append(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.File
                        .withParameter(ContentDisposition.Parameters.Name, "file")
                        .withParameter(ContentDisposition.Parameters.FileName, filename)
                        .toString()
                )
                append(HttpHeaders.ContentType, contentType)
            }
        )
        return listOf(formField("model", model), formField("language", language), filePart)
    }
}
