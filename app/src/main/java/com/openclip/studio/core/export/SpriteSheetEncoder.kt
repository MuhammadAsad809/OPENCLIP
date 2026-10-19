package com.openclip.studio.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.sqrt

object SpriteSheetEncoder {

    suspend fun encodeSpriteSheet(
        context: Context,
        frames: List<Bitmap>,
        outputFile: File,
        jsonFile: File? = null,
        columns: Int = -1,
        padding: Int = 2
    ): Boolean = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext false

        val frameWidth = frames[0].width
        val frameHeight = frames[0].height
        val totalFrames = frames.size

        val cols = if (columns > 0) columns else ceil(sqrt(totalFrames.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(totalFrames.toDouble() / cols).toInt().coerceAtLeast(1)

        val sheetWidth = (cols * frameWidth) + ((cols + 1) * padding)
        val sheetHeight = (rows * frameHeight) + ((rows + 1) * padding)

        val spriteSheetBitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(spriteSheetBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val framesJsonArray = JSONArray()

        for (i in frames.indices) {
            val col = i % cols
            val row = i / cols

            val x = padding + col * (frameWidth + padding)
            val y = padding + row * (frameHeight + padding)

            canvas.drawBitmap(frames[i], x.toFloat(), y.toFloat(), paint)

            val frameObj = JSONObject().apply {
                put("frameIndex", i)
                put("x", x)
                put("y", y)
                put("w", frameWidth)
                put("h", frameHeight)
            }
            framesJsonArray.put(frameObj)
        }

        // Save Sprite Sheet PNG
        FileOutputStream(outputFile).use { out ->
            spriteSheetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Save Sprite Sheet JSON Atlas Metadata
        if (jsonFile != null) {
            val atlasMeta = JSONObject().apply {
                put("image", outputFile.name)
                put("size", JSONObject().apply {
                    put("w", sheetWidth)
                    put("h", sheetHeight)
                })
                put("frameSize", JSONObject().apply {
                    put("w", frameWidth)
                    put("h", frameHeight)
                })
                put("totalFrames", totalFrames)
                put("columns", cols)
                put("rows", rows)
                put("frames", framesJsonArray)
            }
            jsonFile.writeText(atlasMeta.toString(2))
        }

        spriteSheetBitmap.recycle()
        true
    }
}
