package com.openclip.studio

import android.graphics.Bitmap
import com.openclip.studio.core.engine.FloodFillEngine
import com.openclip.studio.core.engine.HistoryManager
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.CanvasPreset
import com.openclip.studio.core.model.Layer
import com.openclip.studio.core.model.Project
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class OpenClipCoreTest {

    @Test
    fun testProjectModelDefaults() {
        val project = Project(
            title = "Test Animation",
            canvasWidth = 1280,
            canvasHeight = 720,
            fps = 24
        )

        assertEquals("Test Animation", project.title)
        assertEquals(1280, project.canvasWidth)
        assertEquals(720, project.canvasHeight)
        assertEquals(24, project.fps)
        assertEquals(1, project.frameCount)
        assertEquals(1, project.layers.size)
        assertEquals("Layer 1", project.layers[0].name)
        assertTrue(project.layers[0].visible)
        assertFalse(project.layers[0].locked)
    }

    @Test
    fun testCanvasPresets() {
        val presets = CanvasPreset.ALL
        assertTrue(presets.isNotEmpty())
        val defaultPreset = CanvasPreset.DEFAULT
        assertEquals(1280, defaultPreset.width)
        assertEquals(720, defaultPreset.height)
        assertEquals("16:9", defaultPreset.aspectRatioLabel)

        val tiktok = presets.find { it.name.contains("TikTok") }
        assertNotNull(tiktok)
        assertEquals(720, tiktok!!.width)
        assertEquals(1280, tiktok.height)
    }

    @Test
    fun testLayerManagement() {
        val initialLayers = listOf(Layer(0, "Layer 1"))
        val addedLayer = Layer(1, "Inking")
        val updated = initialLayers + addedLayer

        assertEquals(2, updated.size)
        assertEquals("Inking", updated[1].name)

        val toggled = updated.map { if (it.id == 1) it.copy(visible = false) else it }
        assertFalse(toggled[1].visible)

        val opacityChanged = toggled.map { if (it.id == 1) it.copy(opacity = 0.5f) else it }
        assertEquals(0.5f, opacityChanged[1].opacity, 0.001f)
    }

    @Test
    fun testHistoryManagerUndoRedo() {
        val history = HistoryManager(maxHistorySize = 5)
        var undoNotified = false
        var redoNotified = false

        history.onHistoryChanged = { canUndo, canRedo ->
            undoNotified = canUndo
            redoNotified = canRedo
        }

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        // Mock bitmap state
        val bmp1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bmp2 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        history.pushState(bmp1)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertTrue(undoNotified)

        val reverted = history.undo(bmp2)
        assertNotNull(reverted)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        val forward = history.redo(bmp1)
        assertNotNull(forward)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }
}
