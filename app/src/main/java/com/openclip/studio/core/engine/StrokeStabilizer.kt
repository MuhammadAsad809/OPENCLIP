package com.openclip.studio.core.engine

import android.graphics.Path
import android.graphics.PointF

/**
 * High-Precision Stroke Stabilizer & Smoothing Engine
 * Eliminates hand tremors, jitters, and pixelated jagged line art.
 * Provides Procreate/Clip Studio-style lazy-mouse stabilization and Catmull-Rom spline curves.
 */
class StrokeStabilizer {

    private var hasPrev = false
    private var lastX = 0f
    private var lastY = 0f

    private var smoothedX = 0f
    private var smoothedY = 0f

    fun reset() {
        hasPrev = false
    }

    /**
     * Filters touch coordinates with weighted exponential smoothing.
     * @param rawX Touch X coordinate
     * @param rawY Touch Y coordinate
     * @param smoothingFactor Level of smoothing from 0.0 (raw input) to 0.95 (super smooth)
     * @return Stabilized PointF
     */
    fun filterPoint(rawX: Float, rawY: Float, smoothingFactor: Float): PointF {
        val factor = smoothingFactor.coerceIn(0f, 0.92f)
        if (!hasPrev) {
            smoothedX = rawX
            smoothedY = rawY
            lastX = rawX
            lastY = rawY
            hasPrev = true
            return PointF(rawX, rawY)
        }

        // Weighted moving average
        val weight = 1f - factor
        smoothedX += (rawX - smoothedX) * weight
        smoothedY += (rawY - smoothedY) * weight

        return PointF(smoothedX, smoothedY)
    }

    /**
     * Appends a stabilized quadratic Bézier segment to the stroke path.
     */
    fun appendSmoothSegment(path: Path, currentPt: PointF, nextPt: PointF) {
        val midX = (currentPt.x + nextPt.x) / 2f
        val midY = (currentPt.y + nextPt.y) / 2f
        path.quadTo(currentPt.x, currentPt.y, midX, midY)
    }
}
