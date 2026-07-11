package com.fadghost.notesapp.ui.voice

import android.media.MediaPlayer
import java.io.File

/**
 * Minimal sequential player for a voice note's segment files (PLAN.md §2.3 popover
 * player). Plays segments back-to-back on a single [MediaPlayer]; the scrubber maps
 * to the currently-playing segment. Kept tiny and lifecycle-driven by the popover
 * (created on open, [release]d on close). Not thread-safe — call from the UI thread.
 */
class AudioPlayerController(private val paths: List<String>) {

    private var player: MediaPlayer? = null
    private var index = 0
    var isPlaying = false
        private set
    var finished = false
        private set

    /** Position within the current segment (ms). */
    fun positionMs(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    /** Duration of the current segment (ms), or 0 if not prepared. */
    fun currentDurationMs(): Int = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    fun toggle() {
        if (isPlaying) pause() else play()
    }

    private fun play() {
        if (paths.isEmpty()) return
        if (finished) { index = 0; finished = false }
        val p = player ?: prepareAt(index) ?: return
        runCatching { p.start() }
        isPlaying = true
    }

    fun pause() {
        runCatching { if (player?.isPlaying == true) player?.pause() }
        isPlaying = false
    }

    fun seekTo(ms: Int) {
        runCatching { player?.seekTo(ms.coerceAtLeast(0)) }
    }

    private fun prepareAt(i: Int): MediaPlayer? {
        val file = paths.getOrNull(i)?.let { File(it) } ?: return null
        if (!file.exists()) return null
        return runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { advance() }
            }
        }.getOrNull()?.also { player = it; index = i }
    }

    private fun advance() {
        val next = index + 1
        release()
        if (next < paths.size) {
            prepareAt(next)?.let { it.start(); isPlaying = true }
        } else {
            isPlaying = false
            finished = true
        }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }
}
