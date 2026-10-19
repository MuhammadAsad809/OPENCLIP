package com.openclip.studio.core.export

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * Clean-room, pure Kotlin Animated GIF Encoder.
 * Writes standard GIF89a streams with frame delays, global/local color tables, and LZW encoding.
 */
class GifEncoder {

    private var width = 0
    private var height = 0
    private var delayMs = 100 // 10 FPS default
    private var repeatCount = 0 // 0 = loop indefinitely
    private var isStarted = false
    private var outputStream: OutputStream? = null

    fun start(os: OutputStream, w: Int, h: Int, fps: Int) {
        outputStream = os
        width = w
        height = h
        delayMs = (1000 / fps.coerceIn(1, 60))
        isStarted = true

        // Write GIF89a Header
        writeString(os, "GIF89a")
        // Logical Screen Descriptor
        writeShort(os, width)
        writeShort(os, height)
        os.write(0x70) // No Global Color Table here, color resolution
        os.write(0)    // Background Color Index
        os.write(0)    // Pixel Aspect Ratio

        // Netscape Application Extension for Looping
        os.write(0x21) // Extension Introducer
        os.write(0xFF) // Application Extension Label
        os.write(11)   // Block Size
        writeString(os, "NETSCAPE2.0")
        os.write(3)    // Sub-block size
        os.write(1)    // Loop sub-block ID
        writeShort(os, repeatCount)
        os.write(0)    // Block Terminator
    }

    fun addFrame(bitmap: Bitmap) {
        val os = outputStream ?: return
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        // Simple 256-color palette quantization
        val palette = IntArray(256)
        val colorMap = HashMap<Int, Int>()
        var paletteSize = 0

        val indexedPixels = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i] and 0x00FFFFFF // ignore alpha for standard GIF
            val idx = colorMap.getOrPut(c) {
                if (paletteSize < 256) {
                    palette[paletteSize] = c
                    paletteSize++
                    paletteSize - 1
                } else {
                    findNearestColor(c, palette, paletteSize)
                }
            }
            indexedPixels[i] = idx.toByte()
        }

        // Graphic Control Extension (Frame timing)
        os.write(0x21) // Extension Introducer
        os.write(0xF9) // Graphic Control Label
        os.write(4)    // Block size
        os.write(0)    // Packed fields (no disposal specified)
        writeShort(os, delayMs / 10) // Delay time in hundredths of a second
        os.write(0)    // Transparent color index
        os.write(0)    // Block Terminator

        // Image Descriptor
        os.write(0x2C) // Image Separator
        writeShort(os, 0) // Left
        writeShort(os, 0) // Top
        writeShort(os, width)
        writeShort(os, height)
        os.write(0x87) // Local Color Table flag + 8 bits/pixel (256 colors)

        // Local Color Table
        for (i in 0 until 256) {
            val c = palette[i]
            os.write(Color.red(c))
            os.write(Color.green(c))
            os.write(Color.blue(c))
        }

        // LZW Compressed Raster Data
        writeLzw(os, indexedPixels, 8)
    }

    fun finish() {
        val os = outputStream ?: return
        os.write(0x3B) // GIF Trailer
        os.flush()
        isStarted = false
    }

    private fun findNearestColor(c: Int, palette: IntArray, size: Int): Int {
        var minDiff = Int.MAX_VALUE
        var bestIdx = 0
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        for (i in 0 until size) {
            val pc = palette[i]
            val diff = abs(r - Color.red(pc)) + abs(g - Color.green(pc)) + abs(b - Color.blue(pc))
            if (diff < minDiff) {
                minDiff = diff
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun writeLzw(os: OutputStream, pixels: ByteArray, initCodeSize: Int) {
        val clearCode = 1 shl initCodeSize
        val eoiCode = clearCode + 1
        var nextCode = clearCode + 2
        var codeSize = initCodeSize + 1
        var codeMask = (1 shl codeSize) - 1

        val prefixTable = IntArray(5003) { -1 }
        val suffixTable = IntArray(5003)
        val codeTable = IntArray(5003)

        os.write(initCodeSize)

        val packet = ByteArray(256)
        var packetLen = 0
        var curBits = 0
        var curVal = 0

        fun sendCode(code: Int) {
            curVal = curVal or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                packet[packetLen++] = (curVal and 0xFF).toByte()
                if (packetLen == 254) {
                    os.write(packetLen)
                    os.write(packet, 0, packetLen)
                    packetLen = 0
                }
                curVal = curVal ushr 8
                curBits -= 8
            }
        }

        sendCode(clearCode)

        var ent = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val c = pixels[i].toInt() and 0xFF
            val hash = ((c shl 4) xor ent) % 5003
            var found = -1
            var h = hash
            while (prefixTable[h] != -1) {
                if (prefixTable[h] == ent && suffixTable[h] == c) {
                    found = codeTable[h]
                    break
                }
                h = (h + 1) % 5003
            }

            if (found != -1) {
                ent = found
            } else {
                sendCode(ent)
                if (nextCode < 4096) {
                    prefixTable[h] = ent
                    suffixTable[h] = c
                    codeTable[h] = nextCode++
                    if (nextCode > codeMask && codeSize < 12) {
                        codeSize++
                        codeMask = (1 shl codeSize) - 1
                    }
                } else {
                    sendCode(clearCode)
                    prefixTable.fill(-1)
                    codeSize = initCodeSize + 1
                    codeMask = (1 shl codeSize) - 1
                    nextCode = clearCode + 2
                }
                ent = c
            }
        }
        sendCode(ent)
        sendCode(eoiCode)

        if (curBits > 0) {
            packet[packetLen++] = (curVal and 0xFF).toByte()
        }
        if (packetLen > 0) {
            os.write(packetLen)
            os.write(packet, 0, packetLen)
        }
        os.write(0) // Sub-block terminator
    }

    private fun writeShort(os: OutputStream, value: Int) {
        os.write(value and 0xFF)
        os.write((value ushr 8) and 0xFF)
    }

    private fun writeString(os: OutputStream, str: String) {
        for (ch in str) os.write(ch.code)
    }
}
