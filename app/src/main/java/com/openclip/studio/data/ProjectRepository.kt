package com.openclip.studio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.Layer
import com.openclip.studio.core.model.Project
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    fun getAllProjects(): List<Project> {
        val list = mutableListOf<Project>()
        val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        for (dir in dirs) {
            val jsonFile = File(dir, "project.json")
            if (jsonFile.exists()) {
                try {
                    val p = readProjectJson(jsonFile)
                    list.add(p)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun getProject(id: String): Project? {
        val dir = File(projectsDir, id)
        val jsonFile = File(dir, "project.json")
        if (!jsonFile.exists()) return null
        return try {
            readProjectJson(jsonFile)
        } catch (e: Exception) {
            null
        }
    }

    fun saveProject(project: Project): Boolean {
        val dir = File(projectsDir, project.id).apply { if (!exists()) mkdirs() }
        val jsonFile = File(dir, "project.json")
        val tmpFile = File(dir, "project.json.tmp")
        return try {
            val json = writeProjectJson(project)
            FileOutputStream(tmpFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            tmpFile.renameTo(jsonFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createProject(
        title: String,
        width: Int,
        height: Int,
        fps: Int,
        backgroundColor: Int,
        backgroundType: BackgroundType
    ): Project {
        val id = UUID.randomUUID().toString()
        val project = Project(
            id = id,
            title = title,
            canvasWidth = width,
            canvasHeight = height,
            fps = fps,
            frameCount = 1,
            backgroundColor = backgroundColor,
            backgroundType = backgroundType,
            layers = listOf(Layer(id = 0, name = "Layer 1", visible = true, locked = false, opacity = 1.0f)),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveProject(project)

        // Initialize frame 0 layer 0 as blank transparent bitmap
        val emptyBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        saveLayerBitmap(id, 0, 0, emptyBitmap)
        updateFrameThumbnail(project, 0)
        return project
    }

    fun duplicateProject(sourceId: String): Project? {
        val source = getProject(sourceId) ?: return null
        val newId = UUID.randomUUID().toString()
        val newProject = source.copy(
            id = newId,
            title = "${source.title} Copy",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val sourceDir = File(projectsDir, sourceId)
        val newDir = File(projectsDir, newId)
        sourceDir.copyRecursively(newDir, overwrite = true)
        saveProject(newProject)
        return newProject
    }

    fun deleteProject(id: String): Boolean {
        val dir = File(projectsDir, id)
        return if (dir.exists()) dir.deleteRecursively() else false
    }

    // Frame layer bitmap storage
    private fun getLayerFile(projectId: String, frameIndex: Int, layerId: Int): File {
        val frameDir = File(File(projectsDir, projectId), "frames/frame_$frameIndex").apply {
            if (!exists()) mkdirs()
        }
        return File(frameDir, "layer_$layerId.png")
    }

    fun getThumbnailFile(projectId: String, frameIndex: Int): File {
        val frameDir = File(File(projectsDir, projectId), "frames/frame_$frameIndex").apply {
            if (!exists()) mkdirs()
        }
        return File(frameDir, "thumb.png")
    }

    fun getProjectCoverThumbnailFile(projectId: String): File {
        return File(File(projectsDir, projectId), "thumbnail.png")
    }

    fun loadLayerBitmap(projectId: String, frameIndex: Int, layerId: Int, width: Int, height: Int): Bitmap {
        val file = getLayerFile(projectId, frameIndex, layerId)
        if (file.exists() && file.length() > 0) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) {
                return bmp.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun saveLayerBitmap(projectId: String, frameIndex: Int, layerId: Int, bitmap: Bitmap) {
        val file = getLayerFile(projectId, frameIndex, layerId)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            tmp.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCompositedFrame(project: Project, frameIndex: Int): Bitmap {
        val out = Bitmap.createBitmap(project.canvasWidth, project.canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Draw Background
        when (project.backgroundType) {
            BackgroundType.SOLID -> canvas.drawColor(project.backgroundColor)
            BackgroundType.TRANSPARENT -> canvas.drawColor(Color.TRANSPARENT)
            BackgroundType.PAPER_WHITE -> {
                canvas.drawColor(0xFFFAFAFA.toInt())
                drawPaperTexture(canvas, project.canvasWidth, project.canvasHeight)
            }
            BackgroundType.PAPER_VINTAGE -> {
                canvas.drawColor(0xFFF4ECD8.toInt())
                drawPaperTexture(canvas, project.canvasWidth, project.canvasHeight)
            }
            BackgroundType.PAPER_GRID -> {
                canvas.drawColor(Color.WHITE)
                drawGrid(canvas, project.canvasWidth, project.canvasHeight)
            }
        }

        // 2. Draw Layers in order
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (layer in project.layers) {
            if (layer.visible && layer.opacity > 0.001f) {
                val layerBmp = loadLayerBitmap(project.id, frameIndex, layer.id, project.canvasWidth, project.canvasHeight)
                paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(layerBmp, 0f, 0f, paint)
            }
        }
        return out
    }

    private fun drawGrid(canvas: Canvas, w: Int, h: Int) {
        val gridPaint = Paint().apply {
            color = 0xFFE0E0E0.toInt()
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val step = 40f
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h.toFloat(), gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawPaperTexture(canvas: Canvas, w: Int, h: Int) {
        val borderPaint = Paint().apply {
            color = 0x1A000000
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), borderPaint)
    }

    fun updateFrameThumbnail(project: Project, frameIndex: Int) {
        try {
            val composited = getCompositedFrame(project, frameIndex)
            val thumbWidth = 160
            val thumbHeight = (thumbWidth * (project.canvasHeight.toFloat() / project.canvasWidth)).toInt().coerceAtLeast(90)
            val thumb = Bitmap.createScaledBitmap(composited, thumbWidth, thumbHeight, true)

            val thumbFile = getThumbnailFile(project.id, frameIndex)
            FileOutputStream(thumbFile).use { out ->
                thumb.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            // If this is frame 0, also update project cover thumbnail
            if (frameIndex == 0) {
                val coverFile = getProjectCoverThumbnailFile(project.id)
                FileOutputStream(coverFile).use { out ->
                    thumb.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun insertFrame(project: Project, atIndex: Int): Project {
        val framesDir = File(File(projectsDir, project.id), "frames")
        // Shift frames atIndex .. (frameCount - 1) up by 1
        for (i in (project.frameCount - 1) downTo atIndex) {
            val src = File(framesDir, "frame_$i")
            val dst = File(framesDir, "frame_${i + 1}")
            if (src.exists()) {
                src.renameTo(dst)
            }
        }
        // Initialize new frame directory
        val newFrameDir = File(framesDir, "frame_$atIndex").apply { mkdirs() }
        val updated = project.copy(
            frameCount = project.frameCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        saveProject(updated)
        updateFrameThumbnail(updated, atIndex)
        return updated
    }

    fun duplicateFrame(project: Project, sourceIndex: Int): Project {
        val framesDir = File(File(projectsDir, project.id), "frames")
        val insertAt = sourceIndex + 1
        // Shift frames up
        for (i in (project.frameCount - 1) downTo insertAt) {
            val src = File(framesDir, "frame_$i")
            val dst = File(framesDir, "frame_${i + 1}")
            if (src.exists()) {
                src.renameTo(dst)
            }
        }
        // Copy source frame contents into insertAt
        val srcDir = File(framesDir, "frame_$sourceIndex")
        val dstDir = File(framesDir, "frame_$insertAt").apply { mkdirs() }
        if (srcDir.exists()) {
            srcDir.copyRecursively(dstDir, overwrite = true)
        }
        val updated = project.copy(
            frameCount = project.frameCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        saveProject(updated)
        updateFrameThumbnail(updated, insertAt)
        return updated
    }

    fun deleteFrame(project: Project, atIndex: Int): Project {
        if (project.frameCount <= 1) return project // Keep at least 1 frame
        val framesDir = File(File(projectsDir, project.id), "frames")
        val targetDir = File(framesDir, "frame_$atIndex")
        if (targetDir.exists()) targetDir.deleteRecursively()

        // Shift frames down
        for (i in (atIndex + 1) until project.frameCount) {
            val src = File(framesDir, "frame_$i")
            val dst = File(framesDir, "frame_${i - 1}")
            if (src.exists()) {
                src.renameTo(dst)
            }
        }
        val updated = project.copy(
            frameCount = project.frameCount - 1,
            updatedAt = System.currentTimeMillis()
        )
        saveProject(updated)
        return updated
    }

    // JSON serialization
    private fun readProjectJson(file: File): Project {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val id = json.getString("id")
        val title = json.getString("title")
        val canvasWidth = json.optInt("canvasWidth", 1280)
        val canvasHeight = json.optInt("canvasHeight", 720)
        val fps = json.optInt("fps", 12)
        val frameCount = json.optInt("frameCount", 1)
        val backgroundColor = json.optInt("backgroundColor", Color.WHITE)
        val bgTypeStr = json.optString("backgroundType", BackgroundType.SOLID.name)
        val backgroundType = try { BackgroundType.valueOf(bgTypeStr) } catch (e: Exception) { BackgroundType.SOLID }
        val createdAt = json.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())

        val layers = mutableListOf<Layer>()
        val layersArr = json.optJSONArray("layers")
        if (layersArr != null) {
            for (i in 0 until layersArr.length()) {
                val lJson = layersArr.getJSONObject(i)
                layers.add(
                    Layer(
                        id = lJson.getInt("id"),
                        name = lJson.optString("name", "Layer ${i + 1}"),
                        visible = lJson.optBoolean("visible", true),
                        locked = lJson.optBoolean("locked", false),
                        opacity = lJson.optDouble("opacity", 1.0).toFloat()
                    )
                )
            }
        }
        if (layers.isEmpty()) {
            layers.add(Layer(0, "Layer 1"))
        }

        return Project(
            id = id,
            title = title,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            fps = fps,
            frameCount = frameCount,
            backgroundColor = backgroundColor,
            backgroundType = backgroundType,
            layers = layers,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun writeProjectJson(p: Project): String {
        val json = JSONObject()
        json.put("id", p.id)
        json.put("title", p.title)
        json.put("canvasWidth", p.canvasWidth)
        json.put("canvasHeight", p.canvasHeight)
        json.put("fps", p.fps)
        json.put("frameCount", p.frameCount)
        json.put("backgroundColor", p.backgroundColor)
        json.put("backgroundType", p.backgroundType.name)
        json.put("createdAt", p.createdAt)
        json.put("updatedAt", p.updatedAt)

        val layersArr = JSONArray()
        for (l in p.layers) {
            val lObj = JSONObject()
            lObj.put("id", l.id)
            lObj.put("name", l.name)
            lObj.put("visible", l.visible)
            lObj.put("locked", l.locked)
            lObj.put("opacity", l.opacity.toDouble())
            layersArr.put(lObj)
        }
        json.put("layers", layersArr)
        return json.toString(2)
    }
}
