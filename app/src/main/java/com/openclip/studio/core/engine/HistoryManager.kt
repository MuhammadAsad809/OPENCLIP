package com.openclip.studio.core.engine

import android.graphics.Bitmap

class HistoryManager(private val maxHistorySize: Int = 20) {

    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()

    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushState(currentBitmap: Bitmap) {
        val snapshot = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (undoStack.size >= maxHistorySize) {
            val oldest = undoStack.removeFirst()
            if (!oldest.isRecycled) oldest.recycle()
        }
        undoStack.addLast(snapshot)
        clearRedo()
        notifyChanged()
    }

    fun undo(currentBitmap: Bitmap): Bitmap? {
        if (undoStack.isEmpty()) return null
        val previousState = undoStack.removeLast()
        // Save current into redo
        val redoSnapshot = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        redoStack.addLast(redoSnapshot)
        notifyChanged()
        return previousState
    }

    fun redo(currentBitmap: Bitmap): Bitmap? {
        if (redoStack.isEmpty()) return null
        val nextState = redoStack.removeLast()
        // Save current into undo
        val undoSnapshot = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        undoStack.addLast(undoSnapshot)
        notifyChanged()
        return nextState
    }

    fun clear() {
        for (b in undoStack) if (!b.isRecycled) b.recycle()
        undoStack.clear()
        clearRedo()
        notifyChanged()
    }

    private fun clearRedo() {
        for (b in redoStack) if (!b.isRecycled) b.recycle()
        redoStack.clear()
    }

    private fun notifyChanged() {
        onHistoryChanged?.invoke(canUndo, canRedo)
    }
}
