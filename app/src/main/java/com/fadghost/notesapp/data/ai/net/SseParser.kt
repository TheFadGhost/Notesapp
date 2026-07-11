package com.fadghost.notesapp.data.ai.net

/**
 * Pure, incremental Server-Sent-Events line parser (PLAN.md §5 — "parse `data:`
 * lines, incremental deltas"). Feed it the response body one line at a time with
 * [accept]; it buffers `data:` fields until a blank dispatch line, then returns
 * the assembled payload. Call [flush] once the stream ends to drain a final
 * event that had no trailing blank line (common with OpenRouter).
 *
 * No coroutines, no I/O, no clock — trivially unit-testable. Comment lines
 * (starting with `:`) and non-`data` fields (`event:`, `id:`, `retry:`) are
 * ignored, which is all OpenRouter's chat stream uses. The sentinel `[DONE]`
 * payload is surfaced verbatim so the caller decides when to stop.
 */
class SseParser {

    private val data = StringBuilder()

    /**
     * Consume one raw line (without its terminating newline). Returns the
     * assembled `data` payload when [line] is the blank dispatch line, otherwise
     * null. Multiple `data:` fields within one event are joined with `\n` per the
     * SSE spec.
     */
    fun accept(line: String): String? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null // comment / heartbeat

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        if (field != "data") return null // we only care about data fields
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1) // strip one leading space
        if (data.isNotEmpty()) data.append('\n')
        data.append(value)
        return null
    }

    /** Emit any buffered event that never saw a trailing blank line. */
    fun flush(): String? = dispatch()

    private fun dispatch(): String? {
        if (data.isEmpty()) return null
        val out = data.toString()
        data.setLength(0)
        return out
    }

    companion object {
        const val DONE = "[DONE]"

        /**
         * Convenience for tests / batch input: run [accept] across every line of a
         * full SSE chunk (splitting on \n or \r\n) plus a final [flush], returning
         * every non-empty `data` payload in order. `[DONE]` is included if present.
         */
        fun parseAll(raw: String): List<String> {
            val parser = SseParser()
            val out = ArrayList<String>()
            for (line in raw.split("\r\n", "\n")) {
                parser.accept(line)?.let { out += it }
            }
            parser.flush()?.let { out += it }
            return out
        }
    }
}
