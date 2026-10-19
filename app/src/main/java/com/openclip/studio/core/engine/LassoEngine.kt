package com.openclip.studio.core.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

class LassoSelection(
    val originalPath: Path,
    val bounds: RectF,
    var selectedBitmap: Bitmap?,
    var transformMatrix: Matrix = Matrix(),
    var isFloating: Boolean = false
)

object LassoEngine {

    fun extractSelection(sourceBitmap: Bitmap, lassoPath: Path): LassoSelection? {
        val bounds = RectF()
        lassoPath.computeBounds(bounds, true)
        if (bounds.width() < 4f || bounds.height() < 4f) return null

        val w = bounds.width().toInt().coerceIn(1, sourceBitmap.width)
        val h = bounds.height().toInt().coerceIn(1, sourceBitmap.height)

        // 1. Create masked bitmap
        val selectionBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val selectionCanvas = Canvas(selectionBitmap)

        // Shift path relative to bounds
        val shiftedPath = Path(lassoPath)
        shiftedPath.offset(-bounds.left, -bounds.top)

        // Draw selection mask
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        selectionCanvas.drawPath(shiftedPath, maskPaint)

        // Composite original source onto mask
        val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        selectionCanvas.drawBitmap(sourceBitmap, -bounds.left, -bounds.top, srcPaint)

        // 2. Erase selected area from sourceBitmap
        val eraseCanvas = Canvas(sourceBitmap)
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        eraseCanvas.drawPath(lassoPath, erasePaint)

        val matrix = Matrix().apply {
            postTranslate(bounds.left, bounds.top)
        }

        return LassoSelection(
            originalPath = lassoPath,
            bounds = bounds,
            selectedBitmap = selectionBitmap,
            transformMatrix = matrix,
            isFloating = true
        )
    }

    fun stampSelection(targetBitmap: Bitmap, selection: LassoSelection) {
        val bmp = selection.selectedBitmap ?: return
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, selection.transformMatrix, paint)
        selection.isFloating = false
    }

    fun flipHorizontal(selection: LassoSelection) {
        val bmp = selection.selectedBitmap ?: return
        selection.transformMatrix.preScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
    }

    fun flipVertical(selection: LassoSelection) {
        val bmp = selection.selectedBitmap ?: return
        selection.transformMatrix.preScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
    }

    fun duplicateSelection(targetBitmap: Bitmap, selection: LassoSelection) {
        val bmp = selection.selectedBitmap ?: return
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, selection.transformMatrix, paint)
        // Keep selection floating so user can move the duplicated copy again
    }

    fun deleteSelection(selection: LassoSelection) {
        selection.selectedBitmap = null
        selection.isFloating = false
    }

    fun applyPerspective(selection: LassoSelection, srcCorners: FloatArray, dstCorners: FloatArray) {
        val matrix = Matrix()
        if (matrix.setPolyToPoly(srcCorners, 0, dstCorners, 0, 4)) {
            selection.transformMatrix.postConcat(matrix)
        }
    }
}
