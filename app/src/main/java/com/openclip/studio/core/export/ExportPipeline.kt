package com.openclip.studio.core.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.openclip.studio.core.model.Project
import com.openclip.studio.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat {
    MP4_VIDEO,
    ANIMATED_GIF,
    PNG_SEQUENCE,
    SINGLE_FRAME_PNG
}

object ExportPipeline {

    suspend fun exportProject(
        context: Context,
        project: Project,
        repository: ProjectRepository,
        format: ExportFormat,
        selectedFrameIndex: Int = 0,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val sanitizedTitle = project.title.replace(Regex("[^a-zA-Z0-9_]"), "_")

        when (format) {
            ExportFormat.MP4_VIDEO -> {
                val outFile = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.mp4")
                val success = Mp4Encoder.encodeAnimation(
                    outputFile = outFile,
                    width = project.canvasWidth,
                    height = project.canvasHeight,
                    fps = project.fps,
                    frameCount = project.frameCount,
                    frameProvider = { idx -> repository.getCompositedFrame(project, idx) },
                    progressCallback = onProgress
                )
                if (success && outFile.exists() && outFile.length() > 0) {
                    saveToMediaStore(context, outFile, "video/mp4", Environment.DIRECTORY_MOVIES)
                    outFile
                } else null
            }

            ExportFormat.ANIMATED_GIF -> {
                val outFile = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.gif")
                try {
                    FileOutputStream(outFile).use { fos ->
                        val gifEncoder = GifEncoder()
                        gifEncoder.start(fos, project.canvasWidth, project.canvasHeight, project.fps)
                        for (i in 0 until project.frameCount) {
                            val bmp = repository.getCompositedFrame(project, i)
                            gifEncoder.addFrame(bmp)
                            onProgress((i + 1).toFloat() / project.frameCount)
                        }
                        gifEncoder.finish()
                    }
                    if (outFile.exists() && outFile.length() > 0) {
                        saveToMediaStore(context, outFile, "image/gif", Environment.DIRECTORY_PICTURES)
                        outFile
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            ExportFormat.PNG_SEQUENCE -> {
                val zipFile = File(exportDir, "${sanitizedTitle}_png_seq.zip")
                try {
                    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                        for (i in 0 until project.frameCount) {
                            val bmp = repository.getCompositedFrame(project, i)
                            val entryName = String.format("frame_%04d.png", i + 1)
                            zos.putNextEntry(ZipEntry(entryName))
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, zos)
                            zos.closeEntry()
                            onProgress((i + 1).toFloat() / project.frameCount)
                        }
                    }
                    zipFile
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            ExportFormat.SINGLE_FRAME_PNG -> {
                val outFile = File(exportDir, "${sanitizedTitle}_frame_${selectedFrameIndex + 1}.png")
                val bmp = repository.getCompositedFrame(project, selectedFrameIndex)
                FileOutputStream(outFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                onProgress(1.0f)
                saveToMediaStore(context, outFile, "image/png", Environment.DIRECTORY_PICTURES)
                outFile
            }
        }
    }

    private fun saveToMediaStore(context: Context, file: File, mimeType: String, relativeDir: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/OpenClip")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (mimeType.startsWith("video")) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
