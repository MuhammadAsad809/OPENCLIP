package com.openclip.studio.core.engine

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayDeque
import kotlin.math.abs

object FloodFillEngine {

    /**
     * Efficient 4-way scanline queue flood fill.
     */
    fun floodFill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        targetColor: Int,
        fillColor: Int,
        tolerance: Int = 32
    ): Boolean {
        if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return false
        if (colorMatch(targetColor, fillColor, 0)) return false

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val srcColor = pixels[startY * width + startX]
        if (colorMatch(srcColor, fillColor, 0)) return false

        val queue = ArrayDeque<Int>()
        queue.add(startX)
        queue.add(startY)

        val visited = BooleanArray(width * height)
        visited[startY * width + startX] = true

        while (!queue.isEmpty()) {
            val x = queue.poll()
            val y = queue.poll()
            val idx = y * width + x
            pixels[idx] = fillColor

            // Check 4 neighbors
            if (x > 0 && !visited[idx - 1] && colorMatch(pixels[idx - 1], srcColor, tolerance)) {
                visited[idx - 1] = true
                queue.add(x - 1)
                queue.add(y)
            }
            if (x < width - 1 && !visited[idx + 1] && colorMatch(pixels[idx + 1], srcColor, tolerance)) {
                visited[idx + 1] = true
                queue.add(x + 1)
                queue.add(y)
            }
            if (y > 0 && !visited[idx - width] && colorMatch(pixels[idx - width], srcColor, tolerance)) {
                visited[idx - width] = true
                queue.add(x)
                queue.add(y - 1)
            }
            if (y < height - 1 && !visited[idx + width] && colorMatch(pixels[idx + width], srcColor, tolerance)) {
                visited[idx + width] = true
                queue.add(x)
                queue.add(y + 1)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return true
    }

    private fun colorMatch(c1: Int, c2: Int, tolerance: Int): Boolean {
        if (c1 == c2) return true
        val rDiff = abs(Color.red(c1) - Color.red(c2))
        val gDiff = abs(Color.green(c1) - Color.green(c2))
        val bDiff = abs(Color.blue(c1) - Color.blue(c2))
        val aDiff = abs(Color.alpha(c1) - Color.alpha(c2))
        return (rDiff + gDiff + bDiff + aDiff) / 4 <= tolerance
    }
}
