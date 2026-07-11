package com.fadghost.notesapp.util

/**
 * Bounded snapshot undo/redo stack for the editor (PLAN.md §6). Stores whole
 * text snapshots (cheap for note-sized text; avoids per-keystroke diff bugs).
 *
 * Coalescing: consecutive [record] calls sharing the same [CoalesceKey] replace
 * the current top instead of piling up, so a burst of typing collapses into one
 * undo step. Passing a different key (or [CoalesceKey.BOUNDARY]) starts a new
 * step. Pure, deterministic, unit-tested — no clock dependency.
 */
class UndoStack<T>(private val maxSize: Int = 100) {

    /** Groups rapid same-kind edits into a single undo step. */
    enum class CoalesceKey { TYPING, DELETING, FORMATTING, BOUNDARY }

    private data class Frame<T>(val value: T, val key: CoalesceKey)

    private val stack = ArrayDeque<Frame<T>>()
    private var cursor = -1 // index of the current value within [stack]
    private var lastKey: CoalesceKey? = null

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor in 0 until stack.lastIndex
    val current: T? get() = stack.getOrNull(cursor)?.value

    /** Seed the stack with the initial value (no coalescing, clears history). */
    fun reset(value: T) {
        stack.clear()
        stack.addLast(Frame(value, CoalesceKey.BOUNDARY))
        cursor = 0
        lastKey = null
    }

    /**
     * Record a new snapshot. If [key] matches the previous non-boundary key and
     * we are at the tip, the top frame is replaced (coalesced).
     */
    fun record(value: T, key: CoalesceKey = CoalesceKey.BOUNDARY) {
        if (stack.isEmpty()) {
            reset(value)
            lastKey = key
            return
        }
        if (value == current) return // no-op change

        val atTip = cursor == stack.lastIndex
        val coalesce = atTip &&
            key != CoalesceKey.BOUNDARY &&
            key == lastKey

        if (coalesce) {
            stack[cursor] = Frame(value, key)
        } else {
            // Truncate any redo branch, then push.
            while (stack.lastIndex > cursor) stack.removeLast()
            stack.addLast(Frame(value, key))
            cursor = stack.lastIndex
            trim()
        }
        lastKey = key
    }

    fun undo(): T? {
        if (!canUndo) return null
        cursor--
        lastKey = CoalesceKey.BOUNDARY // an undo breaks the coalescing run
        return current
    }

    fun redo(): T? {
        if (!canRedo) return null
        cursor++
        lastKey = CoalesceKey.BOUNDARY
        return current
    }

    private fun trim() {
        while (stack.size > maxSize) {
            stack.removeFirst()
            cursor--
        }
        if (cursor < 0) cursor = 0
    }
}
