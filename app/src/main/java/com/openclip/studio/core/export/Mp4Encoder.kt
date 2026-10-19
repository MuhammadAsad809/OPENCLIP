package com.openclip.studio.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File

object Mp4Encoder {

    fun encodeAnimation(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        frameCount: Int,
        frameProvider: (frameIndex: Int) -> Bitmap,
        progressCallback: ((progress: Float) -> Unit)? = null
    ): Boolean {
        // Enforce even dimensions for H.264 codec
        val encWidth = if (width % 2 != 0) width - 1 else width
        val encHeight = if (height % 2 != 0) height - 1 else height

        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val bitRate = (encWidth * encHeight * 4).coerceIn(2_000_000, 14_000_000)

        val format = MediaFormat.createVideoFormat(mime, encWidth, encHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        return try {
            codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = (1_000_000L / fps)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dstRect = Rect(0, 0, encWidth, encHeight)

            for (i in 0 until frameCount) {
                val bmp = frameProvider(i)
                val canvas = inputSurface.lockHardwareCanvas()
                canvas.drawBitmap(bmp, null, dstRect, paint)
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain output from codec
                drainEncoder(codec, muxer, bufferInfo, false) { idx ->
                    trackIndex = idx
                    muxerStarted = true
                }

                progressCallback?.invoke((i + 1).toFloat() / frameCount)
            }

            // Signal End of Stream
            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, true) { idx ->
                trackIndex = idx
                muxerStarted = true
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { inputSurface?.release() } catch (ignored: Exception) {}
            try { codec?.stop(); codec?.release() } catch (ignored: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (ignored: Exception) {}
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onTrackAdded: (Int) -> Unit
    ) {
        var trackIndex = -1
        var muxerStarted = false

        while (true) {
            val status = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
                onTrackAdded(trackIndex)
            } else if (status >= 0) {
                val encodedData = codec.getOutputBuffer(status) ?: continue
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(status, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }
}
