package com.openclip.studio.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.BrushConfig
import com.openclip.studio.core.model.MirrorMode
import com.openclip.studio.core.model.OnionConfig
import com.openclip.studio.core.model.Project
import com.openclip.studio.core.model.RulerType
import com.openclip.studio.core.model.ToolType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Canvas & Project Settings
    var canvasWidth: Int = 1280
        private set
    var canvasHeight: Int = 720
        private set

    var currentProject: Project? = null
    var currentFrameIndex: Int = 0
    var activeLayerId: Int = 0

    // Configs
    var brushConfig: BrushConfig = BrushConfig()
    var onionConfig: OnionConfig = OnionConfig()
    var isPlaying: Boolean = false

    // Bitmaps
    var activeLayerBitmap: Bitmap? = null
    var backgroundLayerBitmaps: List<Pair<Bitmap, Float>> = emptyList() // (bitmap, opacity)
    var foregroundLayerBitmaps: List<Pair<Bitmap, Float>> = emptyList()
    var onionBeforeBitmaps: List<Bitmap> = emptyList()
    var onionAfterBitmaps: List<Bitmap> = emptyList()

    // Viewport transform (Pan & Zoom)
    var viewportScale = 1.0f
    var viewportOffsetX = 0f
    var viewportOffsetY = 0f

    private val viewMatrix = Matrix()
    private val invertMatrix = Matrix()

    // Touch & Gesture state
    private var isMultiTouch = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Live drawing path
    private val currentStrokePath = Path()
    private val mirrorStrokePath = Path()
    private val strokeStabilizer = StrokeStabilizer()
    private val mirrorAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF3B69.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private var strokeStartX = 0f
    private var strokeStartY = 0f
    private var lastCanvasX = 0f
    private var lastCanvasY = 0f
    private var isDrawing = false

    // Lasso selection
    var currentLassoSelection: LassoSelection? = null
    private val lassoPath = Path()

    // Callbacks
    var onStrokeFinished: (() -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null

    // Scale detector
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val newScale = (viewportScale * factor).coerceIn(0.2f, 8.0f)
            val focusX = detector.focusX
            val focusY = detector.focusY

            viewportOffsetX = focusX - (focusX - viewportOffsetX) * (newScale / viewportScale)
            viewportOffsetY = focusY - (focusY - viewportOffsetY) * (newScale / viewportScale)
            viewportScale = newScale
            invalidate()
            return true
        }
    })

    // Drawing Paints
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val onionBeforePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val onionAfterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val lassoOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF3B69.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    fun setCanvasDimensions(width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        resetViewport()
    }

    fun resetViewport() {
        if (width <= 0 || height <= 0 || canvasWidth <= 0 || canvasHeight <= 0) return
        val padding = 48f
        val availW = width - padding * 2
        val availH = height - padding * 2
        val scaleX = availW / canvasWidth
        val scaleY = availH / canvasHeight
        viewportScale = min(scaleX, scaleY).coerceIn(0.1f, 5.0f)
        viewportOffsetX = (width - canvasWidth * viewportScale) / 2f
        viewportOffsetY = (height - canvasHeight * viewportScale) / 2f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetViewport()
    }

    private fun updateMatrices() {
        viewMatrix.reset()
        viewMatrix.postScale(viewportScale, viewportScale)
        viewMatrix.postTranslate(viewportOffsetX, viewportOffsetY)
        viewMatrix.invert(invertMatrix)
    }

    private fun screenToCanvas(screenX: Float, screenY: Float): PointF {
        val pts = floatArrayOf(screenX, screenY)
        invertMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateMatrices()

        // 1. Draw outer workspace background
        canvas.drawColor(0xFF1E1E22.toInt())

        canvas.save()
        canvas.concat(viewMatrix)

        // 2. Draw canvas paper background
        drawCanvasBackground(canvas)

        // 3. Draw Onion Skin (Past frames in Red tint) - ONLY when not playing
        if (!isPlaying && onionConfig.enabled && onionBeforeBitmaps.isNotEmpty()) {
            val step = if (onionBeforeBitmaps.size > 1) {
                (onionConfig.startOpacity - onionConfig.endOpacity) / (onionBeforeBitmaps.size - 1)
            } else 0f

            for ((i, bmp) in onionBeforeBitmaps.withIndex()) {
                val alpha = (onionConfig.startOpacity - i * step).coerceIn(0.05f, 0.9f)
                onionBeforePaint.alpha = (alpha * 255).toInt()
                if (onionConfig.coloredOnion) {
                    onionBeforePaint.colorFilter = PorterDuffColorFilter(0xFFFF3333.toInt(), PorterDuff.Mode.SRC_IN)
                } else {
                    onionBeforePaint.colorFilter = null
                }
                canvas.drawBitmap(bmp, 0f, 0f, onionBeforePaint)
            }
        }

        // 4. Draw Onion Skin (Future frames in Green tint) - ONLY when not playing
        if (!isPlaying && onionConfig.enabled && onionAfterBitmaps.isNotEmpty()) {
            val step = if (onionAfterBitmaps.size > 1) {
                (onionConfig.startOpacity - onionConfig.endOpacity) / (onionAfterBitmaps.size - 1)
            } else 0f

            for ((i, bmp) in onionAfterBitmaps.withIndex()) {
                val alpha = (onionConfig.startOpacity - i * step).coerceIn(0.05f, 0.9f)
                onionAfterPaint.alpha = (alpha * 255).toInt()
                if (onionConfig.coloredOnion) {
                    onionAfterPaint.colorFilter = PorterDuffColorFilter(0xFF33DD55.toInt(), PorterDuff.Mode.SRC_IN)
                } else {
                    onionAfterPaint.colorFilter = null
                }
                canvas.drawBitmap(bmp, 0f, 0f, onionAfterPaint)
            }
        }

        // 5. Draw background layers (under active layer)
        for ((bmp, opacity) in backgroundLayerBitmaps) {
            bitmapPaint.alpha = (opacity * 255).toInt()
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 6. Draw active layer bitmap
        activeLayerBitmap?.let { bmp ->
            bitmapPaint.alpha = 255
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 7. Draw in-flight active stroke preview
        if (isDrawing) {
            setupStrokePaint(strokePaint, brushConfig)
            if (brushConfig.toolType == ToolType.ERASER) {
                // Visualize eraser as soft circle/line
                val previewEraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x80FFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = brushConfig.eraserSize
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(currentStrokePath, previewEraser)
                if (brushConfig.mirrorMode != MirrorMode.NONE) {
                    canvas.drawPath(mirrorStrokePath, previewEraser)
                }
            } else {
                canvas.drawPath(currentStrokePath, strokePaint)
                if (brushConfig.mirrorMode != MirrorMode.NONE) {
                    canvas.drawPath(mirrorStrokePath, strokePaint)
                }
            }
        }

        // Draw Mirror Symmetry Guideline
        if (brushConfig.mirrorMode != MirrorMode.NONE) {
            drawMirrorGuideline(canvas)
        }

        // 8. Draw active Ruler guide preview if using ruler
        if (isDrawing && brushConfig.toolType == ToolType.RULER) {
            drawRulerPreview(canvas)
        }

        // 9. Draw foreground layers (above active layer)
        for ((bmp, opacity) in foregroundLayerBitmaps) {
            bitmapPaint.alpha = (opacity * 255).toInt()
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        }

        // 10. Draw Lasso selection / preview overlay
        if (isDrawing && brushConfig.toolType == ToolType.LASSO) {
            canvas.drawPath(lassoPath, lassoOverlayPaint)
        }
        currentLassoSelection?.let { selection ->
            selection.selectedBitmap?.let { selBmp ->
                canvas.drawBitmap(selBmp, selection.transformMatrix, bitmapPaint)
                canvas.drawRect(selection.bounds, lassoOverlayPaint)
            }
        }

        // 11. Draw Canvas Border
        val borderPaint = Paint().apply {
            color = 0x40FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2f / viewportScale
        }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), borderPaint)

        canvas.restore()
    }

    private fun drawCanvasBackground(canvas: Canvas) {
        val proj = currentProject
        val bgType = proj?.backgroundType ?: BackgroundType.SOLID
        val bgColor = proj?.backgroundColor ?: Color.WHITE

        when (bgType) {
            BackgroundType.SOLID -> canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), Paint().apply { color = bgColor })
            BackgroundType.TRANSPARENT -> drawCheckerboard(canvas)
            BackgroundType.PAPER_WHITE -> {
                canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), Paint().apply { color = 0xFFF9F9FB.toInt() })
            }
            BackgroundType.PAPER_VINTAGE -> {
                canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), Paint().apply { color = 0xFFF7F1E5.toInt() })
            }
            BackgroundType.PAPER_GRID -> {
                canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), Paint().apply { color = Color.WHITE })
                val gridPaint = Paint().apply {
                    color = 0xFFECECEC.toInt()
                    strokeWidth = 1.5f
                    style = Paint.Style.STROKE
                }
                var x = 40f
                while (x < canvasWidth) {
                    canvas.drawLine(x, 0f, x, canvasHeight.toFloat(), gridPaint)
                    x += 40f
                }
                var y = 40f
                while (y < canvasHeight) {
                    canvas.drawLine(0f, y, canvasWidth.toFloat(), y, gridPaint)
                    y += 40f
                }
            }
        }
    }

    private fun drawCheckerboard(canvas: Canvas) {
        val lightPaint = Paint().apply { color = 0xFFFFFFFF.toInt() }
        val darkPaint = Paint().apply { color = 0xFFE0E0E0.toInt() }
        val size = 32f
        var y = 0f
        var row = 0
        while (y < canvasHeight) {
            var x = 0f
            var col = 0
            while (x < canvasWidth) {
                val p = if ((row + col) % 2 == 0) lightPaint else darkPaint
                canvas.drawRect(x, y, (x + size).coerceAtMost(canvasWidth.toFloat()), (y + size).coerceAtMost(canvasHeight.toFloat()), p)
                x += size
                col++
            }
            y += size
            row++
        }
    }

    private fun drawRulerPreview(canvas: Canvas) {
        when (brushConfig.rulerType) {
            RulerType.LINE -> canvas.drawLine(strokeStartX, strokeStartY, lastCanvasX, lastCanvasY, guidePaint)
            RulerType.CIRCLE -> {
                val radius = hypot(lastCanvasX - strokeStartX, lastCanvasY - strokeStartY)
                canvas.drawCircle(strokeStartX, strokeStartY, radius, guidePaint)
            }
            RulerType.RECTANGLE -> {
                val left = min(strokeStartX, lastCanvasX)
                val top = min(strokeStartY, lastCanvasY)
                val right = max(strokeStartX, lastCanvasX)
                val bottom = max(strokeStartY, lastCanvasY)
                canvas.drawRect(left, top, right, bottom, guidePaint)
            }
            RulerType.NONE -> {}
        }
    }

    private fun setupStrokePaint(paint: Paint, config: BrushConfig) {
        paint.color = config.color
        paint.alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()
        paint.strokeWidth = config.size
        paint.xfermode = null
        paint.maskFilter = null
        paint.pathEffect = null
        paint.isAntiAlias = true

        when (config.toolType) {
            ToolType.PEN, ToolType.BRUSH -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
            ToolType.PENCIL -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.alpha = (config.opacity * 0.75f * 255).toInt()
            }
            ToolType.MARKER -> {
                paint.strokeCap = Paint.Cap.SQUARE
                paint.alpha = (config.opacity * 0.6f * 255).toInt()
            }
            ToolType.AIRBRUSH -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = config.size * 2.2f
                paint.alpha = (config.opacity * 0.2f * 255).toInt()
                val blurR = (config.size * 0.6f).coerceAtLeast(1f)
                paint.maskFilter = BlurMaskFilter(blurR, BlurMaskFilter.Blur.NORMAL)
            }
            ToolType.PIXEL -> {
                paint.isAntiAlias = false
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.MITER
                paint.strokeWidth = (config.size / 3f).coerceAtLeast(1f).toInt().toFloat()
            }
            ToolType.CALLIGRAPHY -> {
                paint.strokeCap = Paint.Cap.BUTT
                paint.strokeJoin = Paint.Join.BEVEL
                paint.strokeWidth = config.size * 1.5f
            }
            ToolType.LEAF_SCATTER, ToolType.CHARCOAL -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = config.size * 1.2f
                paint.pathEffect = DashPathEffect(floatArrayOf(config.size * 0.8f, config.size * 0.4f), 0f)
            }
            ToolType.ERASER -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = config.eraserSize
                paint.alpha = (config.eraserOpacity.coerceIn(0f, 1f) * 255).toInt()
                if (config.eraserHardness < 0.95f) {
                    val blurR = (config.eraserSize * (1f - config.eraserHardness) * 0.4f).coerceAtLeast(1f)
                    paint.maskFilter = BlurMaskFilter(blurR, BlurMaskFilter.Blur.NORMAL)
                }
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
            }
        }
    }

    private fun getMirroredPoint(x: Float, y: Float): PointF {
        val axisX = canvasWidth * brushConfig.mirrorAxisX
        val axisY = canvasHeight * brushConfig.mirrorAxisY
        return when (brushConfig.mirrorMode) {
            MirrorMode.VERTICAL -> PointF(2 * axisX - x, y)
            MirrorMode.HORIZONTAL -> PointF(x, 2 * axisY - y)
            MirrorMode.QUAD -> PointF(2 * axisX - x, 2 * axisY - y)
            MirrorMode.NONE -> PointF(x, y)
        }
    }

    private fun drawMirrorGuideline(canvas: Canvas) {
        val axisX = canvasWidth * brushConfig.mirrorAxisX
        val axisY = canvasHeight * brushConfig.mirrorAxisY
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B69.toInt() }

        when (brushConfig.mirrorMode) {
            MirrorMode.VERTICAL -> {
                canvas.drawLine(axisX, 0f, axisX, canvasHeight.toFloat(), mirrorAxisPaint)
                canvas.drawCircle(axisX, canvasHeight / 2f, 8f, centerPaint)
            }
            MirrorMode.HORIZONTAL -> {
                canvas.drawLine(0f, axisY, canvasWidth.toFloat(), axisY, mirrorAxisPaint)
                canvas.drawCircle(canvasWidth / 2f, axisY, 8f, centerPaint)
            }
            MirrorMode.QUAD -> {
                canvas.drawLine(axisX, 0f, axisX, canvasHeight.toFloat(), mirrorAxisPaint)
                canvas.drawLine(0f, axisY, canvasWidth.toFloat(), axisY, mirrorAxisPaint)
                canvas.drawCircle(axisX, axisY, 8f, centerPaint)
            }
            MirrorMode.NONE -> {}
        }
    }

    private fun applySmudge(ptX: Float, ptY: Float, prevX: Float, prevY: Float) {
        val bmp = activeLayerBitmap ?: return
        val radius = (brushConfig.size * 1.4f).toInt().coerceIn(4, 50)
        val ix = ptX.toInt()
        val iy = ptY.toInt()
        val px = prevX.toInt()
        val py = prevY.toInt()
        if (px in 0 until bmp.width && py in 0 until bmp.height && ix in 0 until bmp.width && iy in 0 until bmp.height) {
            val prevColor = bmp.getPixel(px, py)
            if (Color.alpha(prevColor) > 10) {
                val smudgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = prevColor
                    alpha = (brushConfig.opacity * 130).toInt()
                    maskFilter = BlurMaskFilter(radius.toFloat() / 2f, BlurMaskFilter.Blur.NORMAL)
                }
                val c = Canvas(bmp)
                c.drawCircle(ptX, ptY, radius.toFloat(), smudgePaint)
            }
        }
    }

    private fun applyBlur(ptX: Float, ptY: Float) {
        val bmp = activeLayerBitmap ?: return
        val r = (brushConfig.size * 1.5f).toInt().coerceIn(6, 40)
        val left = (ptX - r).toInt().coerceAtLeast(0)
        val top = (ptY - r).toInt().coerceAtLeast(0)
        val right = (ptX + r).toInt().coerceAtMost(bmp.width - 1)
        val bottom = (ptY + r).toInt().coerceAtMost(bmp.height - 1)
        val w = right - left
        val h = bottom - top
        if (w > 3 && h > 3) {
            val sub = Bitmap.createBitmap(bmp, left, top, w, h)
            val small = Bitmap.createScaledBitmap(sub, (w / 3).coerceAtLeast(1), (h / 3).coerceAtLeast(1), true)
            val blurred = Bitmap.createScaledBitmap(small, w, h, true)
            val c = Canvas(bmp)
            c.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
            sub.recycle()
            small.recycle()
            blurred.recycle()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            isMultiTouch = true
            isDrawing = false
            currentStrokePath.reset()
            invalidate()
            return true
        }

        if (isMultiTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                isMultiTouch = false
            }
            return true
        }

        val canvasPt = screenToCanvas(event.x, event.y)
        val cx = canvasPt.x
        val cy = canvasPt.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                strokeStartX = cx
                strokeStartY = cy
                lastCanvasX = cx
                lastCanvasY = cy

                strokeStabilizer.reset()
                val pt = strokeStabilizer.filterPoint(cx, cy, brushConfig.strokeSmoothing)
                currentStrokePath.reset()
                currentStrokePath.moveTo(pt.x, pt.y)

                if (brushConfig.mirrorMode != MirrorMode.NONE) {
                    val mPt = getMirroredPoint(pt.x, pt.y)
                    mirrorStrokePath.reset()
                    mirrorStrokePath.moveTo(mPt.x, mPt.y)
                }

                if (brushConfig.toolType == ToolType.LASSO) {
                    lassoPath.reset()
                    lassoPath.moveTo(cx, cy)
                } else if (brushConfig.toolType == ToolType.EYEDROPPER) {
                    handleEyedropper(cx, cy)
                } else if (brushConfig.toolType == ToolType.FILL) {
                    handleFloodFill(cx, cy)
                } else if (brushConfig.toolType == ToolType.SMUDGE) {
                    applySmudge(cx, cy, cx, cy)
                } else if (brushConfig.toolType == ToolType.BLUR) {
                    applyBlur(cx, cy)
                }
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return true

                if (brushConfig.toolType == ToolType.LASSO) {
                    lassoPath.lineTo(cx, cy)
                } else if (brushConfig.toolType == ToolType.SMUDGE) {
                    applySmudge(cx, cy, lastCanvasX, lastCanvasY)
                } else if (brushConfig.toolType == ToolType.BLUR) {
                    applyBlur(cx, cy)
                } else if (brushConfig.toolType != ToolType.FILL && brushConfig.toolType != ToolType.EYEDROPPER && brushConfig.toolType != ToolType.RULER) {
                    val pt = strokeStabilizer.filterPoint(cx, cy, brushConfig.strokeSmoothing)
                    strokeStabilizer.appendSmoothSegment(currentStrokePath, PointF(lastCanvasX, lastCanvasY), pt)

                    if (brushConfig.mirrorMode != MirrorMode.NONE) {
                        val prevM = getMirroredPoint(lastCanvasX, lastCanvasY)
                        val curM = getMirroredPoint(pt.x, pt.y)
                        strokeStabilizer.appendSmoothSegment(mirrorStrokePath, prevM, curM)
                    }
                }

                lastCanvasX = cx
                lastCanvasY = cy
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    if (brushConfig.toolType == ToolType.LASSO) {
                        lassoPath.close()
                        activeLayerBitmap?.let { bmp ->
                            currentLassoSelection = LassoEngine.extractSelection(bmp, lassoPath)
                        }
                    } else if (brushConfig.toolType == ToolType.RULER) {
                        commitRulerStroke(strokeStartX, strokeStartY, cx, cy)
                    } else if (brushConfig.toolType != ToolType.FILL && brushConfig.toolType != ToolType.EYEDROPPER && brushConfig.toolType != ToolType.SMUDGE && brushConfig.toolType != ToolType.BLUR) {
                        commitFreehandStroke()
                    }
                    isDrawing = false
                    currentStrokePath.reset()
                    mirrorStrokePath.reset()
                    invalidate()
                    onStrokeFinished?.invoke()
                }
            }
        }
        return true
    }

    private fun commitFreehandStroke() {
        val bmp = activeLayerBitmap ?: return
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        setupStrokePaint(paint, brushConfig)
        canvas.drawPath(currentStrokePath, paint)
        if (brushConfig.mirrorMode != MirrorMode.NONE) {
            canvas.drawPath(mirrorStrokePath, paint)
        }
    }

    private fun commitRulerStroke(x1: Float, y1: Float, x2: Float, y2: Float) {
        val bmp = activeLayerBitmap ?: return
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        setupStrokePaint(paint, brushConfig)

        when (brushConfig.rulerType) {
            RulerType.LINE -> canvas.drawLine(x1, y1, x2, y2, paint)
            RulerType.CIRCLE -> {
                val r = hypot(x2 - x1, y2 - y1)
                canvas.drawCircle(x1, y1, r, paint)
            }
            RulerType.RECTANGLE -> {
                val left = min(x1, x2)
                val top = min(y1, y2)
                val right = max(x1, x2)
                val bottom = max(y1, y2)
                canvas.drawRect(left, top, right, bottom, paint)
            }
            RulerType.NONE -> {}
        }
    }

    private fun handleFloodFill(cx: Float, cy: Float) {
        val bmp = activeLayerBitmap ?: return
        val ix = cx.toInt()
        val iy = cy.toInt()
        if (ix in 0 until bmp.width && iy in 0 until bmp.height) {
            val targetColor = bmp.getPixel(ix, iy)
            val fillColor = brushConfig.color
            FloodFillEngine.floodFill(bmp, ix, iy, targetColor, fillColor, brushConfig.fillTolerance)
            invalidate()
            onStrokeFinished?.invoke()
        }
    }

    private fun handleEyedropper(cx: Float, cy: Float) {
        val bmp = activeLayerBitmap ?: return
        val ix = cx.toInt()
        val iy = cy.toInt()
        if (ix in 0 until bmp.width && iy in 0 until bmp.height) {
            val pixel = bmp.getPixel(ix, iy)
            if (Color.alpha(pixel) > 20) {
                onColorPicked?.invoke(pixel)
            }
        }
    }

    fun stampLassoSelection() {
        val bmp = activeLayerBitmap ?: return
        currentLassoSelection?.let { sel ->
            LassoEngine.stampSelection(bmp, sel)
            currentLassoSelection = null
            invalidate()
            onStrokeFinished?.invoke()
        }
    }

    fun discardLassoSelection() {
        currentLassoSelection = null
        invalidate()
    }

    fun flipLassoHorizontal() {
        currentLassoSelection?.let { LassoEngine.flipHorizontal(it) }
        invalidate()
    }

    fun flipLassoVertical() {
        currentLassoSelection?.let { LassoEngine.flipVertical(it) }
        invalidate()
    }

    fun duplicateLassoSelection() {
        val bmp = activeLayerBitmap ?: return
        currentLassoSelection?.let { sel ->
            LassoEngine.duplicateSelection(bmp, sel)
            sel.transformMatrix.postTranslate(30f, 30f)
            invalidate()
            onStrokeFinished?.invoke()
        }
    }

    fun deleteLassoSelection() {
        currentLassoSelection?.let { sel ->
            LassoEngine.deleteSelection(sel)
            currentLassoSelection = null
            invalidate()
            onStrokeFinished?.invoke()
        }
    }

    fun applyLassoPerspective(skewX: Float = 0.2f, skewY: Float = 0.05f) {
        val sel = currentLassoSelection ?: return
        val bmp = sel.selectedBitmap ?: return
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val dst = floatArrayOf(w * skewX, 0f, w * (1f - skewX), 0f, w, h, 0f, h)
        LassoEngine.applyPerspective(sel, src, dst)
        invalidate()
    }
}
