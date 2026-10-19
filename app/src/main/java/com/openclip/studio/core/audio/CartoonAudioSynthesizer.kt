package com.openclip.studio.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

/**
 * Procedural Cartoon & Anime Sound Effects Synthesizer
 * Generates 100% offline, free, pristine 44.1kHz 16-bit PCM WAV audio effects.
 */
object CartoonAudioSynthesizer {

    data class SfxPreset(
        val id: String,
        val name: String,
        val category: String,
        val description: String,
        val durationMs: Int
    )

    val PRESETS = listOf(
        SfxPreset("whoosh_fast", "Fast Whoosh", "Action", "Quick aerodynamic swish for rapid movements", 250),
        SfxPreset("whoosh_heavy", "Heavy Swing", "Action", "Deep whoosh for heavy weapon or character turn", 400),
        SfxPreset("punch_impact", "Punch Impact", "Combat", "Meaty comic punch hit with bass thump", 300),
        SfxPreset("comic_slap", "Comic Slap", "Comedy", "Crisp slap sound with sharp high frequency snap", 200),
        SfxPreset("cartoon_boing", "Spring Boing", "Comedy", "Classic comedy jumping spring boing resonance", 600),
        SfxPreset("bubble_pop", "Bubble Pop", "Cartoon", "Short cute pop sound for bubbles or buttons", 120),
        SfxPreset("comic_zip", "Speed Zip", "Motion", "High pitch cartoon dash upward slide", 350),
        SfxPreset("laser_zap", "Laser Zap", "Sci-Fi", "Futuristic energy beam pitch drop zap", 280),
        SfxPreset("footstep_tap", "Toon Footstep", "Foley", "Light cartoon character footstep tap", 150),
        SfxPreset("impact_thud", "Heavy Thud", "Impact", "Dull body fall or block collision impact", 350)
    )

    fun generateSfxSamples(id: String, sampleRate: Int = 44100): ShortArray {
        val preset = PRESETS.firstOrNull { it.id == id } ?: PRESETS[0]
        val totalSamples = (sampleRate * (preset.durationMs / 1000f)).toInt().coerceAtLeast(100)
        val samples = ShortArray(totalSamples)

        when (id) {
            "whoosh_fast" -> {
                // Band-limited noise with envelope and pitch modulation
                var filterState = 0.0
                for (i in 0 until totalSamples) {
                    val progress = i.toFloat() / totalSamples
                    val env = sin(progress * Math.PI).toFloat().pow(1.5f)
                    val noise = (Random.nextFloat() * 2f - 1f)
                    val cutoff = 0.05 + 0.35 * sin(progress * Math.PI)
                    filterState += cutoff * (noise - filterState)
                    val s = (filterState * env * 28000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "whoosh_heavy" -> {
                var filterState = 0.0
                for (i in 0 until totalSamples) {
                    val progress = i.toFloat() / totalSamples
                    val env = sin(progress * Math.PI).toFloat().pow(2f)
                    val noise = (Random.nextFloat() * 2f - 1f)
                    val cutoff = 0.02 + 0.15 * sin(progress * Math.PI)
                    filterState += cutoff * (noise - filterState)
                    val s = (filterState * env * 30000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "punch_impact" -> {
                for (i in 0 until totalSamples) {
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 12.0).toFloat()
                    val bassFreq = 90.0 * (1.0 - progress * 0.5)
                    val tone = sin(2 * Math.PI * bassFreq * (i.toDouble() / sampleRate))
                    val noise = if (progress < 0.15f) (Random.nextFloat() * 2f - 1f) * (1f - progress / 0.15f) else 0f
                    val combined = (tone * 0.75 + noise * 0.45) * env
                    val s = (combined * 31000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "comic_slap" -> {
                for (i in 0 until totalSamples) {
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 22.0).toFloat()
                    val noise = (Random.nextFloat() * 2f - 1f)
                    val snapFreq = 600.0 * (1.0 - progress * 0.8)
                    val tone = sin(2 * Math.PI * snapFreq * (i.toDouble() / sampleRate))
                    val combined = (noise * 0.7 + tone * 0.3) * env
                    val s = (combined * 30000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "cartoon_boing" -> {
                // Classic frequency modulated spring boing
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 4.5).toFloat()
                    val modFreq = 14.0
                    val baseFreq = 260.0 + 80.0 * sin(2 * Math.PI * modFreq * t)
                    val tone = sin(2 * Math.PI * baseFreq * t)
                    val s = (tone * env * 28000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "bubble_pop" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 25.0).toFloat()
                    val freq = 450.0 + 900.0 * progress
                    val tone = sin(2 * Math.PI * freq * t)
                    val s = (tone * env * 30000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "comic_zip" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = sin(progress * Math.PI).toFloat()
                    val freq = 300.0 + 2400.0 * (progress * progress)
                    val tone = sin(2 * Math.PI * freq * t)
                    val s = (tone * env * 27000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "laser_zap" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 8.0).toFloat()
                    val freq = 1800.0 * exp(-progress * 6.0)
                    val tone = sin(2 * Math.PI * freq * t)
                    val s = (tone * env * 28000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            "footstep_tap" -> {
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 28.0).toFloat()
                    val freq = 180.0 + 80.0 * (1f - progress)
                    val tone = sin(2 * Math.PI * freq * t)
                    val click = if (progress < 0.05f) (Random.nextFloat() * 2f - 1f) else 0f
                    val combined = (tone * 0.7 + click * 0.3) * env
                    val s = (combined * 29000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
            else -> { // impact_thud
                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toFloat() / totalSamples
                    val env = exp(-progress * 10.0).toFloat()
                    val freq = 110.0 * (1f - progress * 0.6f)
                    val tone = sin(2 * Math.PI * freq * t)
                    val s = (tone * env * 31000).toInt().coerceIn(-32767, 32767)
                    samples[i] = s.toShort()
                }
            }
        }
        return samples
    }

    /**
     * Plays the synthesized sound effect in real time
     */
    fun playPreview(id: String) {
        Thread {
            try {
                val sampleRate = 44100
                val samples = generateSfxSamples(id, sampleRate)
                val bufferSize = samples.size * 2

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                Thread.sleep(presetDuration(id) + 100L)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun presetDuration(id: String): Long {
        return (PRESETS.firstOrNull { it.id == id }?.durationMs ?: 300).toLong()
    }

    /**
     * Saves the sound effect as a standard .wav file
     */
    fun exportToWavFile(id: String, destFile: File): Boolean {
        return try {
            val sampleRate = 44100
            val samples = generateSfxSamples(id, sampleRate)
            val byteCount = samples.size * 2

            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { out ->
                // Write WAV Header
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(byteCount + 36)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16) // Subchunk1Size
                header.putShort(1.toShort()) // AudioFormat (1 = PCM)
                header.putShort(1.toShort()) // NumChannels (1 = Mono)
                header.putInt(sampleRate) // SampleRate
                header.putInt(sampleRate * 2) // ByteRate
                header.putShort(2.toShort()) // BlockAlign
                header.putShort(16.toShort()) // BitsPerSample
                header.put("data".toByteArray())
                header.putInt(byteCount)
                out.write(header.array())

                // Write PCM audio data
                val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
                for (sample in samples) {
                    buffer.putShort(sample)
                }
                out.write(buffer.array())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
