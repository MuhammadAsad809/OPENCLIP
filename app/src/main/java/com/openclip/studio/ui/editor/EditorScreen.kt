package com.openclip.studio.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.openclip.studio.R
import com.openclip.studio.core.engine.DrawingCanvasView
import com.openclip.studio.core.export.ExportFormat
import com.openclip.studio.core.export.ExportPipeline
import com.openclip.studio.core.model.*
import com.openclip.studio.ui.theme.*
import com.openclip.studio.ui.toolkit.AnimationToolkitDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

val CaveatFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal)
)

/**
 * Pixel-Perfect FlipaClip 5.0.2 Stage / Editor Interface
 * References:
 * - media_1792380169250.png: Main Studio Editor layout
 * - media_1792380169377.png: Secondary Tool Card (Smudge & Blur)
 * - media_1792380169131.png: Stage Settings Sheet (Onion, Grid, Make Movie)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val project by viewModel.project.collectAsState()
    val currentFrameIndex by viewModel.currentFrameIndex.collectAsState()
    val activeLayerId by viewModel.activeLayerId.collectAsState()
    val brushConfig by viewModel.brushConfig.collectAsState()
    val onionConfig by viewModel.onionConfig.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    // Dialog & Sheet States
    var showBrushDialog by remember { mutableStateOf(false) }
    var showEraserDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showSecondaryTools by remember { mutableStateOf(false) }
    var showStageSettingsSheet by remember { mutableStateOf(false) }
    var showLayerSheet by remember { mutableStateOf(false) }
    var showOnionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showProjectSettingsDialog by remember { mutableStateOf(false) }
    var showFramesViewerDialog by remember { mutableStateOf(false) }
    var showGridSettingsDialog by remember { mutableStateOf(false) }
    var showLayerSettingsDialog by remember { mutableStateOf(false) }
    var selectedLayerForSettings by remember { mutableStateOf<Layer?>(null) }
    var showMagicCutDialog by remember { mutableStateOf(false) }
    var showImportVideoDialog by remember { mutableStateOf(false) }
    var showFrameContextMenu by remember { mutableStateOf(false) }
    var contextMenuFrameIndex by remember { mutableStateOf(0) }
    var showGrid by remember { mutableStateOf(false) }
    var showAudioEditor by remember { mutableStateOf(false) }
    var showImageVideoSheet by remember { mutableStateOf(false) }
    var isImportingVideo by remember { mutableStateOf(false) }
    var importVideoProgress by remember { mutableStateOf(0f) }
    var showRulerMirrorDialog by remember { mutableStateOf(false) }
    var showToolkitDialog by remember { mutableStateOf(false) }
    var showFrameHoldDialog by remember { mutableStateOf(false) }
    var targetHoldFrameIndex by remember { mutableStateOf(0) }
    var capsuleOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var hudFeedbackText by remember { mutableStateOf<String?>(null) }

    // Canvas view reference & revision counter
    var canvasViewRef by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var frameRevision by remember { mutableStateOf(0) }
    var copiedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Media pickers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val originalBitmap = BitmapFactory.decodeStream(stream)
                    if (originalBitmap != null) {
                        val active = canvasViewRef?.activeLayerBitmap ?: viewModel.repository.loadLayerBitmap(
                            project.id, currentFrameIndex, activeLayerId, project.canvasWidth, project.canvasHeight
                        )
                        val canvas = android.graphics.Canvas(active)

                        // Scale image to fit within canvas bounds preserving aspect ratio
                        val srcW = originalBitmap.width.toFloat()
                        val srcH = originalBitmap.height.toFloat()
                        val scale = (project.canvasWidth.toFloat() / srcW).coerceAtMost(project.canvasHeight.toFloat() / srcH)
                        val destW = (srcW * scale).toInt().coerceAtLeast(1)
                        val destH = (srcH * scale).toInt().coerceAtLeast(1)
                        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, destW, destH, true)

                        val left = (project.canvasWidth - destW) / 2f
                        val top = (project.canvasHeight - destH) / 2f
                        canvas.drawBitmap(scaledBitmap, left, top, null)

                        viewModel.historyManager.pushState(active)
                        viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, active)
                        viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                        canvasViewRef?.invalidate()
                        frameRevision++
                        Toast.makeText(context, "Image added to current frame", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isImportingVideo = true
                importVideoProgress = 0f
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toLongOrNull() ?: 3000L
                    val fps = project.fps.coerceIn(1, 30)
                    val frameIntervalMs = 1000L / fps
                    val maxFrames = 60
                    val framesToExtract = ((durationMs / frameIntervalMs).toInt()).coerceIn(1, maxFrames)

                    var currentProj = project
                    for (i in 0 until framesToExtract) {
                        val timeUs = (i * frameIntervalMs) * 1000L
                        val frameBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frameBmp != null) {
                            val scaled = Bitmap.createScaledBitmap(frameBmp, project.canvasWidth, project.canvasHeight, true)
                            val targetFrame = currentFrameIndex + i
                            if (targetFrame >= currentProj.frameCount) {
                                currentProj = viewModel.repository.insertFrame(currentProj, targetFrame)
                            }
                            viewModel.repository.saveLayerBitmap(currentProj.id, targetFrame, activeLayerId, scaled)
                            viewModel.repository.updateFrameThumbnail(currentProj, targetFrame)
                        }
                        importVideoProgress = (i + 1).toFloat() / framesToExtract
                    }
                    retriever.release()
                    viewModel.setProject(currentProj)
                    frameRevision++
                    Toast.makeText(context, "Imported $framesToExtract frames from video", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import video: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isImportingVideo = false
                }
            }
        }
    }

    val projectImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val tempFile = File(context.cacheDir, "import_temp.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    val imported = viewModel.repository.importProjectPackage(tempFile)
                    if (imported != null) {
                        viewModel.setProject(imported)
                        frameRevision++
                        Toast.makeText(context, "Project '${imported.title}' imported!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import project package", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showAudioEditor) {
        AudioEditorScreen(
            project = project,
            currentFrameIndex = currentFrameIndex,
            repository = viewModel.repository,
            onDismiss = {
                showAudioEditor = false
                viewModel.reloadProject()
                frameRevision++
            },
            onFrameChanged = { newIdx ->
                viewModel.selectFrame(newIdx)
            },
            onProjectUpdated = { updatedProj ->
                viewModel.setProject(updatedProj)
            }
        )
        return
    }



    // Active layer bitmap
    val activeBmp = remember(project.id, currentFrameIndex, activeLayerId, frameRevision) {
        viewModel.repository.loadLayerBitmap(project.id, currentFrameIndex, activeLayerId, project.canvasWidth, project.canvasHeight)
    }

    // Background layers bitmaps
    val bgBmps = remember(project.id, currentFrameIndex, activeLayerId, project.layers, frameRevision) {
        val activeIndex = project.layers.indexOfFirst { it.id == activeLayerId }
        val list = mutableListOf<Pair<Bitmap, Float>>()
        if (activeIndex > 0) {
            for (i in 0 until activeIndex) {
                val l = project.layers[i]
                if (l.visible) {
                    val b = viewModel.repository.loadLayerBitmap(project.id, currentFrameIndex, l.id, project.canvasWidth, project.canvasHeight)
                    list.add(Pair(b, l.opacity))
                }
            }
        }
        list
    }

    // Foreground layers bitmaps
    val fgBmps = remember(project.id, currentFrameIndex, activeLayerId, project.layers, frameRevision) {
        val activeIndex = project.layers.indexOfFirst { it.id == activeLayerId }
        val list = mutableListOf<Pair<Bitmap, Float>>()
        if (activeIndex in 0 until project.layers.size - 1) {
            for (i in (activeIndex + 1) until project.layers.size) {
                val l = project.layers[i]
                if (l.visible) {
                    val b = viewModel.repository.loadLayerBitmap(project.id, currentFrameIndex, l.id, project.canvasWidth, project.canvasHeight)
                    list.add(Pair(b, l.opacity))
                }
            }
        }
        list
    }

    // Onion skin bitmaps (only when not playing, and using getCompositedLayers to avoid pink tinted backgrounds!)
    val onionBeforeBmps = remember(project.id, currentFrameIndex, onionConfig, frameRevision, isPlaying) {
        val list = mutableListOf<Bitmap>()
        if (!isPlaying && onionConfig.enabled && currentFrameIndex > 0) {
            val count = onionConfig.framesBefore.coerceAtMost(currentFrameIndex)
            for (offset in 1..count) {
                list.add(viewModel.repository.getCompositedLayers(project, currentFrameIndex - offset))
            }
        }
        list
    }

    val onionAfterBmps = remember(project.id, currentFrameIndex, onionConfig, frameRevision, isPlaying) {
        val list = mutableListOf<Bitmap>()
        if (!isPlaying && onionConfig.enabled && currentFrameIndex < project.frameCount - 1) {
            val count = onionConfig.framesAfter.coerceAtMost(project.frameCount - 1 - currentFrameIndex)
            for (offset in 1..count) {
                list.add(viewModel.repository.getCompositedLayers(project, currentFrameIndex + offset))
            }
        }
        list
    }

    // Sync state with Canvas view
    LaunchedEffect(activeBmp, bgBmps, fgBmps, onionBeforeBmps, onionAfterBmps, brushConfig, onionConfig, project, isPlaying) {
        canvasViewRef?.let { cv ->
            cv.currentProject = project
            cv.currentFrameIndex = currentFrameIndex
            cv.activeLayerId = activeLayerId
            cv.brushConfig = brushConfig
            cv.onionConfig = onionConfig
            cv.isPlaying = isPlaying
            cv.activeLayerBitmap = activeBmp
            cv.backgroundLayerBitmaps = bgBmps
            cv.foregroundLayerBitmaps = fgBmps
            cv.onionBeforeBitmaps = onionBeforeBmps
            cv.onionAfterBitmaps = onionAfterBmps
            cv.invalidate()
        }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                onBack = {
                    viewModel.stopPlayback()
                    onNavigateBack()
                },
                onDiamondClick = {
                    Toast.makeText(context, "OpenClip: 100% Free Animation Studio (All Tools Unlocked)", Toast.LENGTH_SHORT).show()
                },
                onRulerClick = {
                    showRulerMirrorDialog = true
                },
                onToolkitClick = {
                    showToolkitDialog = true
                },
                onShareClick = {
                    showExportDialog = true
                },
                onOverflowClick = {
                    showStageSettingsSheet = true
                }
            )
        },
        bottomBar = {
            StudioBottomActionStrip(
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = {
                    val cur = canvasViewRef?.activeLayerBitmap ?: return@StudioBottomActionStrip
                    val prev = viewModel.historyManager.undo(cur)
                    if (prev != null) {
                        canvasViewRef?.activeLayerBitmap = prev
                        canvasViewRef?.invalidate()
                        viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, prev)
                        viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                        frameRevision++
                    }
                },
                onRedo = {
                    val cur = canvasViewRef?.activeLayerBitmap ?: return@StudioBottomActionStrip
                    val next = viewModel.historyManager.redo(cur)
                    if (next != null) {
                        canvasViewRef?.activeLayerBitmap = next
                        canvasViewRef?.invalidate()
                        viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, next)
                        viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                        frameRevision++
                    }
                },
                onCopy = {
                    val cur = canvasViewRef?.activeLayerBitmap
                    if (cur != null) {
                        copiedFrameBitmap = cur.copy(cur.config ?: Bitmap.Config.ARGB_8888, true)
                        Toast.makeText(context, "Frame copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                },
                onPaste = {
                    val copied = copiedFrameBitmap
                    if (copied != null) {
                        val active = canvasViewRef?.activeLayerBitmap
                        if (active != null) {
                            val c = android.graphics.Canvas(active)
                            c.drawBitmap(copied, 0f, 0f, null)
                            canvasViewRef?.invalidate()
                            viewModel.historyManager.pushState(active)
                            viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, active)
                            viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                            frameRevision++
                            Toast.makeText(context, "Pasted into active layer", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Clipboard empty. Tap Copy first", Toast.LENGTH_SHORT).show()
                    }
                },
                onImageVideo = {
                    showImageVideoSheet = true
                },
                onAddAudio = {
                    showAudioEditor = true
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // CENTRAL DRAWING CANVAS
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    DrawingCanvasView(ctx).apply {
                        setCanvasDimensions(project.canvasWidth, project.canvasHeight)
                        canvasViewRef = this

                        onStrokeFinished = {
                            val bmp = activeLayerBitmap
                            if (bmp != null) {
                                viewModel.historyManager.pushState(bmp)
                                viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, bmp)
                                viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                                frameRevision++
                            }
                        }

                        onColorPicked = { color ->
                            viewModel.setBrushColor(color)
                            Toast.makeText(context, "Color picked", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                update = { cv ->
                    canvasViewRef = cv
                }
            )

            // FLOATING TOOL CAPSULE (Movable across screen)
            FloatingToolCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .offset { capsuleOffset },
                currentTool = brushConfig.toolType,
                brushConfig = brushConfig,
                onDragHandleDelta = { delta ->
                    capsuleOffset += delta
                },
                onSelectTool = { tool ->
                    showSecondaryTools = false
                    when (tool) {
                        ToolType.BRUSH, ToolType.PEN -> {
                            if (brushConfig.toolType == ToolType.BRUSH || brushConfig.toolType == ToolType.PEN) {
                                showBrushDialog = true
                            } else {
                                viewModel.setToolType(ToolType.BRUSH)
                            }
                        }
                        ToolType.ERASER -> {
                            if (brushConfig.toolType == ToolType.ERASER) {
                                showEraserDialog = true
                            } else {
                                viewModel.setToolType(ToolType.ERASER)
                            }
                        }
                        ToolType.FILL -> {
                            if (brushConfig.toolType == ToolType.FILL) {
                                showColorDialog = true
                            } else {
                                viewModel.setToolType(ToolType.FILL)
                            }
                        }
                        ToolType.TEXT -> {
                            viewModel.setToolType(ToolType.TEXT)
                            showTextDialog = true
                        }
                        else -> viewModel.setToolType(tool)
                    }
                },
                onSizeScrubbed = { newSize ->
                    val s = newSize.coerceIn(1f, 150f)
                    viewModel.setBrushSize(s)
                    hudFeedbackText = "Size: ${s.toInt()}px"
                },
                onOpacityScrubbed = { newOp ->
                    val op = newOp.coerceIn(0.01f, 1.0f)
                    viewModel.setBrushOpacity(op)
                    hudFeedbackText = "Opacity: ${(op * 100).toInt()}%"
                },
                onColorClick = {
                    showColorDialog = true
                },
                onToggleMoreTools = {
                    showSecondaryTools = !showSecondaryTools
                }
            )

            // Live HUD feedback badge (Size & Opacity changes)
            LaunchedEffect(hudFeedbackText) {
                if (hudFeedbackText != null) {
                    kotlinx.coroutines.delay(1200L)
                    hudFeedbackText = null
                }
            }
            hudFeedbackText?.let { feedback ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xDD000000)
                ) {
                    Text(
                        text = feedback,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // SECONDARY TOOL POPUP CARD (Smudge, Blur, Ruler, Toolkit)
            if (showSecondaryTools) {
                SecondaryToolPopupCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 74.dp)
                        .offset { capsuleOffset },
                    onSelectSmudge = {
                        viewModel.setToolType(ToolType.SMUDGE)
                        showSecondaryTools = false
                        Toast.makeText(context, "Smudge Tool: Blend and mix colors", Toast.LENGTH_SHORT).show()
                    },
                    onSelectBlur = {
                        viewModel.setToolType(ToolType.BLUR)
                        showSecondaryTools = false
                        Toast.makeText(context, "Blur Tool: Soften edges and details", Toast.LENGTH_SHORT).show()
                    },
                    onSelectRuler = {
                        showSecondaryTools = false
                        showRulerMirrorDialog = true
                    },
                    onSelectToolkit = {
                        showSecondaryTools = false
                        showToolkitDialog = true
                    }
                )
            }

            // FLOATING LASSO ACTION BAR (When Lasso tool is active)
            if (brushConfig.toolType == ToolType.LASSO) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = DarkCapsule
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { canvasViewRef?.flipLassoHorizontal() }) {
                            Text("Flip X", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { canvasViewRef?.flipLassoVertical() }) {
                            Text("Flip Y", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { canvasViewRef?.duplicateLassoSelection() }) {
                            Text("Duplicate", color = AccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { canvasViewRef?.applyLassoPerspective() }) {
                            Text("Perspective", color = Color(0xFF30B0C7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { canvasViewRef?.deleteLassoSelection() }) {
                            Text("Delete", color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            canvasViewRef?.stampLassoSelection()
                            viewModel.setToolType(ToolType.PEN)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Check, contentDescription = "Done", tint = AccentPrimary)
                        }
                    }
                }
            }

            // FLOATING BOTTOM TIMELINE BAR (Above Action Strip)
            FloatingBottomTimelineBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                project = project,
                currentFrameIndex = currentFrameIndex,
                isPlaying = isPlaying,
                repository = viewModel.repository,
                onSelectFrame = { viewModel.selectFrame(it) },
                onFrameHoldClick = { idx ->
                    targetHoldFrameIndex = idx
                    showFrameHoldDialog = true
                },
                onAddFrame = {
                    viewModel.addFrame()
                    frameRevision++
                },
                onTogglePlay = { viewModel.togglePlayback() },
                onOpenLayers = { showLayerSheet = true },
                onOpenFramesViewer = { showFramesViewerDialog = true },
                onFrameLongClick = { idx ->
                    contextMenuFrameIndex = idx
                    showFrameContextMenu = true
                }
            )
        }
    }

    // STAGE SETTINGS BOTTOM SHEET (Matches media_1792380169131.png)
    if (showStageSettingsSheet) {
        StageSettingsBottomSheet(
            onionEnabled = onionConfig.enabled,
            gridEnabled = project.gridConfig.enabled,
            onDismiss = { showStageSettingsSheet = false },
            onProjectSettings = {
                showStageSettingsSheet = false
                showProjectSettingsDialog = true
            },
            onFramesViewer = {
                showStageSettingsSheet = false
                showFramesViewerDialog = true
            },
            onToggleOnion = { enabled ->
                viewModel.setOnionEnabled(enabled)
            },
            onEditOnion = {
                showStageSettingsSheet = false
                showOnionDialog = true
            },
            onToggleGrid = { enabled ->
                viewModel.updateGridConfig(project.gridConfig.copy(enabled = enabled))
                Toast.makeText(context, if (enabled) "Grid Enabled" else "Grid Disabled", Toast.LENGTH_SHORT).show()
            },
            onEditGrid = {
                showStageSettingsSheet = false
                showGridSettingsDialog = true
            },
            onMagicCut = {
                showStageSettingsSheet = false
                showMagicCutDialog = true
            },
            onAddImage = {
                showStageSettingsSheet = false
                imagePickerLauncher.launch("image/*")
            },
            onAddVideo = {
                showStageSettingsSheet = false
                showImportVideoDialog = true
            },
            onExportProject = {
                showStageSettingsSheet = false
                coroutineScope.launch {
                    try {
                        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                        val targetZip = File(exportDir, "${project.title.replace(' ', '_')}.openclip")
                        val success = viewModel.repository.exportProjectPackage(project.id, targetZip)
                        if (success && targetZip.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                targetZip
                            )
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Project Package"))
                        } else {
                            Toast.makeText(context, "Export package failed", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImportProject = {
                showStageSettingsSheet = false
                projectImportLauncher.launch("*/*")
            },
            onMakeMovie = {
                showStageSettingsSheet = false
                showExportDialog = true
            }
        )
    }

    // BRUSH SETTINGS DIALOG
    if (showBrushDialog) {
        BrushSettingsDialog(
            config = brushConfig,
            onDismiss = { showBrushDialog = false },
            onSelectBrushType = { viewModel.setToolType(it) },
            onUpdateSize = { viewModel.setBrushSize(it) },
            onUpdateOpacity = { viewModel.setBrushOpacity(it) },
            onUpdateSmoothing = { viewModel.setStrokeSmoothing(it) },
            onOpenColor = {
                showBrushDialog = false
                showColorDialog = true
            }
        )
    }

    // ERASER SETTINGS DIALOG
    if (showEraserDialog) {
        EraserSettingsDialog(
            config = brushConfig,
            onDismiss = { showEraserDialog = false },
            onUpdateSize = { viewModel.setEraserSize(it) },
            onUpdateHardness = { viewModel.setEraserHardness(it) },
            onUpdateOpacity = { viewModel.setBrushOpacity(it) }
        )
    }

    // PRO COLOR PICKER DIALOG (Wheel, Classic, Harmony, Values, Custom Palettes)
    if (showColorDialog) {
        ProColorPickerDialog(
            initialColor = brushConfig.color,
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                viewModel.setBrushColor(it)
                showColorDialog = false
            }
        )
    }

    // RULER & MIRROR SYMMETRY DIALOG
    if (showRulerMirrorDialog) {
        RulerMirrorDialog(
            config = brushConfig,
            onDismiss = { showRulerMirrorDialog = false },
            onUpdateRulerType = { viewModel.setRulerType(it) },
            onUpdateRulerLock = { viewModel.setRulerLocked(it) },
            onUpdateMirrorMode = { viewModel.setMirrorMode(it) }
        )
    }

    // ANIMATOR'S TOOLKIT DIALOG (Motion Guides, Audio SFX, Backdrops, Props, Palettes)
    if (showToolkitDialog) {
        AnimationToolkitDialog(
            project = project,
            currentFrameIndex = currentFrameIndex,
            repository = viewModel.repository,
            activeLayerBitmap = canvasViewRef?.activeLayerBitmap,
            onDismiss = { showToolkitDialog = false },
            onProjectUpdated = { updatedProj ->
                viewModel.setProject(updatedProj)
                frameRevision++
            },
            onBitmapStamped = {
                val cur = canvasViewRef?.activeLayerBitmap
                if (cur != null) {
                    viewModel.historyManager.pushState(cur)
                    viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, cur)
                    viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                    canvasViewRef?.invalidate()
                    frameRevision++
                }
            },
            onColorSelected = {
                viewModel.setBrushColor(it)
            }
        )
    }

    // FRAME HOLD / EXPOSURE DIALOG
    if (showFrameHoldDialog) {
        FrameHoldDialog(
            frameIndex = targetHoldFrameIndex,
            currentHold = project.frameHolds[targetHoldFrameIndex] ?: 1,
            onDismiss = { showFrameHoldDialog = false },
            onSetHold = { hold ->
                viewModel.setFrameHold(targetHoldFrameIndex, hold)
                frameRevision++
            }
        )
    }

    // TEXT INPUT DIALOG
    if (showTextDialog) {
        TextInputDialog(
            onDismiss = { showTextDialog = false },
            onAddText = { text ->
                showTextDialog = false
                val cur = canvasViewRef?.activeLayerBitmap
                if (cur != null) {
                    val c = android.graphics.Canvas(cur)
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = brushConfig.color
                        textSize = 64f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    c.drawText(text, project.canvasWidth / 4f, project.canvasHeight / 2f, p)
                    canvasViewRef?.invalidate()
                    viewModel.historyManager.pushState(cur)
                    viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, cur)
                    viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                    frameRevision++
                }
            }
        )
    }

    // LAYER MANAGEMENT SHEET
    if (showLayerSheet) {
        LayerManagerBottomSheet(
            project = project,
            activeLayerId = activeLayerId,
            onDismiss = { showLayerSheet = false },
            onSelectLayer = {
                viewModel.selectLayer(it)
                frameRevision++
            },
            onAddLayer = {
                viewModel.addLayer()
                frameRevision++
            },
            onToggleVisibility = {
                viewModel.toggleLayerVisibility(it)
                frameRevision++
            },
            onUpdateOpacity = { id, op ->
                viewModel.setLayerOpacity(id, op)
                frameRevision++
            },
            onDeleteLayer = {
                viewModel.deleteLayer(it)
                frameRevision++
            },
            onOpenLayerSettings = { layer ->
                showLayerSheet = false
                selectedLayerForSettings = layer
                showLayerSettingsDialog = true
            }
        )
    }

    // LAYER SETTINGS & FX DIALOG
    if (showLayerSettingsDialog && selectedLayerForSettings != null) {
        val targetLayer = selectedLayerForSettings!!
        val layerIdx = project.layers.indexOfFirst { it.id == targetLayer.id }
        val canMerge = layerIdx > 0
        LayerSettingsDialog(
            layer = targetLayer,
            canMergeDown = canMerge,
            onDismiss = { showLayerSettingsDialog = false },
            onMergeDown = {
                viewModel.mergeLayerDown(targetLayer.id)
                showLayerSettingsDialog = false
                frameRevision++
                Toast.makeText(context, "Merged down with layer below", Toast.LENGTH_SHORT).show()
            },
            onSaveLayer = { updated ->
                viewModel.updateLayer(updated)
                showLayerSettingsDialog = false
                frameRevision++
            }
        )
    }

    // FLIPACLIP MAGIC CUT DIALOG
    if (showMagicCutDialog) {
        MagicCutDialog(
            canvasWidth = project.canvasWidth,
            canvasHeight = project.canvasHeight,
            onDismiss = { showMagicCutDialog = false },
            onCutoutReady = { cutoutBmp ->
                val active = canvasViewRef?.activeLayerBitmap ?: viewModel.repository.loadLayerBitmap(
                    project.id, currentFrameIndex, activeLayerId, project.canvasWidth, project.canvasHeight
                )
                val canvas = android.graphics.Canvas(active)

                // Scale image to fit within canvas bounds preserving aspect ratio
                val srcW = cutoutBmp.width.toFloat()
                val srcH = cutoutBmp.height.toFloat()
                val scale = (project.canvasWidth.toFloat() / srcW).coerceAtMost(project.canvasHeight.toFloat() / srcH) * 0.8f
                val destW = (srcW * scale).toInt().coerceAtLeast(1)
                val destH = (srcH * scale).toInt().coerceAtLeast(1)
                val scaledBitmap = Bitmap.createScaledBitmap(cutoutBmp, destW, destH, true)

                val left = (project.canvasWidth - destW) / 2f
                val top = (project.canvasHeight - destH) / 2f
                canvas.drawBitmap(scaledBitmap, left, top, null)

                viewModel.historyManager.pushState(active)
                viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, active)
                viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                canvasViewRef?.invalidate()
                frameRevision++
                Toast.makeText(context, "Magic Cut sticker stamped onto frame", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // FULL SCREEN FRAMES VIEWER GRID
    if (showFramesViewerDialog) {
        val thumbs = remember(frameRevision, currentFrameIndex) { viewModel.loadFrameThumbnails() }
        FramesViewerDialog(
            project = project,
            currentFrameIndex = currentFrameIndex,
            frameThumbnails = thumbs,
            onDismiss = { showFramesViewerDialog = false },
            onSelectFrame = { idx ->
                viewModel.selectFrame(idx)
                showFramesViewerDialog = false
            },
            onAddFrameAfter = { idx ->
                viewModel.addFrameAfter(idx)
                frameRevision++
            },
            onAddFrameBefore = { idx ->
                viewModel.addFrameBefore(idx)
                frameRevision++
            },
            onAddBetweenAllFrames = {
                viewModel.addBetweenAllFrames()
                frameRevision++
            },
            onDuplicateFrames = { list ->
                viewModel.duplicateFrames(list)
                frameRevision++
            },
            onDeleteFrames = { list ->
                viewModel.deleteFrames(list)
                frameRevision++
            },
            onMoveFrame = { from, to ->
                viewModel.moveFrame(from, to)
                frameRevision++
            },
            onShareFrame = { frameIdx ->
                try {
                    val bmp = viewModel.repository.getCompositedFrame(project, frameIdx)
                    val cacheFile = File(context.cacheDir, "frame_${frameIdx + 1}.png")
                    FileOutputStream(cacheFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        cacheFile
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Frame ${frameIdx + 1}"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // GRID & GUIDES SETTINGS DIALOG
    if (showGridSettingsDialog) {
        GridSettingsDialog(
            config = project.gridConfig,
            onDismiss = { showGridSettingsDialog = false },
            onSaveConfig = {
                viewModel.updateGridConfig(it)
                showGridSettingsDialog = false
            }
        )
    }

    // ONION SKIN SETTINGS DIALOG
    if (showOnionDialog) {
        OnionSettingsDialog(
            config = onionConfig,
            onDismiss = { showOnionDialog = false },
            onSaveConfig = {
                viewModel.updateOnionConfig(it)
                showOnionDialog = false
            }
        )
    }

    // EXPORT / MAKE MOVIE DIALOG
    if (showExportDialog) {
        ExportMovieDialog(
            project = project,
            repository = viewModel.repository,
            onDismiss = { showExportDialog = false }
        )
    }

    // PROJECT SETTINGS DIALOG (Canvas Presets, FPS Bouncing Ball, Background Texture)
    if (showProjectSettingsDialog) {
        ProjectSettingsDialog(
            project = project,
            onDismiss = { showProjectSettingsDialog = false },
            onSaveSettings = { title, fps, width, height, bgType, bgColor, flipH, rot ->
                viewModel.updateProjectSettings(title, fps, width, height, bgType, bgColor, flipH, rot)
                showProjectSettingsDialog = false
                frameRevision++
            }
        )
    }

    // ROTOSCOPING VIDEO IMPORT DIALOG
    if (showImportVideoDialog) {
        ImportVideoDialog(
            canvasWidth = project.canvasWidth,
            canvasHeight = project.canvasHeight,
            onDismiss = { showImportVideoDialog = false },
            onFramesExtracted = { frames ->
                viewModel.importRotoscopedBitmaps(frames)
                showImportVideoDialog = false
                frameRevision++
                Toast.makeText(context, "Imported ${frames.size} frames from video", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // TIMELINE FRAME CONTEXT MENU
    if (showFrameContextMenu) {
        AlertDialog(
            onDismissRequest = { showFrameContextMenu = false },
            containerColor = Color(0xFF22222A),
            title = {
                Text(
                    text = "Frame ${contextMenuFrameIndex + 1} Options",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            showFrameContextMenu = false
                            viewModel.addFrameBefore(contextMenuFrameIndex)
                            frameRevision++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Frame Left (Before)", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            showFrameContextMenu = false
                            viewModel.addFrameAfter(contextMenuFrameIndex)
                            frameRevision++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Frame Right (After)", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            showFrameContextMenu = false
                            copiedFrameBitmap = viewModel.repository.loadLayerBitmap(
                                project.id, contextMenuFrameIndex, activeLayerId, project.canvasWidth, project.canvasHeight
                            )
                            Toast.makeText(context, "Frame ${contextMenuFrameIndex + 1} copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Frame", color = AccentPrimary)
                    }
                    if (copiedFrameBitmap != null) {
                        TextButton(
                            onClick = {
                                showFrameContextMenu = false
                                viewModel.addFrameBefore(contextMenuFrameIndex)
                                viewModel.repository.saveLayerBitmap(
                                    project.id, contextMenuFrameIndex, activeLayerId, copiedFrameBitmap!!
                                )
                                viewModel.repository.updateFrameThumbnail(project, contextMenuFrameIndex)
                                frameRevision++
                                Toast.makeText(context, "Pasted left", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Paste Left", color = Color(0xFF64D2FF))
                        }
                        TextButton(
                            onClick = {
                                showFrameContextMenu = false
                                viewModel.addFrameAfter(contextMenuFrameIndex)
                                val newIndex = contextMenuFrameIndex + 1
                                viewModel.repository.saveLayerBitmap(
                                    project.id, newIndex, activeLayerId, copiedFrameBitmap!!
                                )
                                viewModel.repository.updateFrameThumbnail(project, newIndex)
                                frameRevision++
                                Toast.makeText(context, "Pasted right", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Paste Right", color = Color(0xFF64D2FF))
                        }
                    }
                    TextButton(
                        onClick = {
                            showFrameContextMenu = false
                            targetHoldFrameIndex = contextMenuFrameIndex
                            showFrameHoldDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Hold Duration...", color = Color(0xFFFFB300))
                    }
                    TextButton(
                        onClick = {
                            showFrameContextMenu = false
                            showFramesViewerDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Frames Viewer Grid", color = Color.White)
                    }
                    if (project.frameCount > 1) {
                        TextButton(
                            onClick = {
                                showFrameContextMenu = false
                                viewModel.deleteFrames(listOf(contextMenuFrameIndex))
                                frameRevision++
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete Frame", color = Color(0xFFFF453A))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFrameContextMenu = false }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }

    // IMAGE & VIDEO IMPORT SHEET
    if (showImageVideoSheet) {
        ImageVideoImportBottomSheet(
            onDismiss = { showImageVideoSheet = false },
            onImportImage = {
                imagePickerLauncher.launch("image/*")
            },
            onImportVideo = {
                videoPickerLauncher.launch("video/*")
            }
        )
    }

    // VIDEO IMPORT PROGRESS DIALOG
    if (isImportingVideo) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing video frames...", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { importVideoProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(importVideoProgress * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Top App Bar matching FlipaClip 5.0.2
 * Left: X (Close), Coral Diamond
 * Center: Studio Logo + Studio text
 * Right: Coral Share, Overflow (3 dots)
 */
@Composable
fun StudioTopBar(
    onBack: () -> Unit,
    onDiamondClick: () -> Unit,
    onRulerClick: () -> Unit = {},
    onToolkitClick: () -> Unit = {},
    onShareClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = DarkBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close X
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_close),
                    contentDescription = "Close Studio",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Coral Diamond
            IconButton(onClick = onDiamondClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_diamond),
                    contentDescription = "Features",
                    tint = AccentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Ruler & Mirror (Scale icon)
            IconButton(onClick = onRulerClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_ruler),
                    contentDescription = "Ruler & Mirror",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center: Studio Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_studio),
                    contentDescription = "Studio",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Studio",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Animator's Toolkit (Resources & SFX)
            IconButton(onClick = onToolkitClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_toolkit),
                    contentDescription = "Animator Toolkit",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Coral Share
            IconButton(onClick = onShareClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_share),
                    contentDescription = "Share",
                    tint = AccentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Overflow 3-dots
            IconButton(onClick = onOverflowClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_overflow),
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Floating Tool Capsule matching FlipaClip 5.0.2
 * Items: Drag handle (::), Brush (pink when active), Eraser, Lasso, Fill, Text (T), More (...)
 */
@Composable
fun FloatingToolCapsule(
    modifier: Modifier = Modifier,
    currentTool: ToolType,
    brushConfig: BrushConfig,
    onDragHandleDelta: (IntOffset) -> Unit,
    onSelectTool: (ToolType) -> Unit,
    onSizeScrubbed: (Float) -> Unit,
    onOpacityScrubbed: (Float) -> Unit,
    onColorClick: () -> Unit,
    onToggleMoreTools: () -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(27.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(27.dp)),
        shape = RoundedCornerShape(27.dp),
        color = DarkCapsule
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Drag handle (6 dots)
            Icon(
                painter = painterResource(id = R.drawable.ic_stage_drag_handle),
                contentDescription = "Drag Handle",
                tint = TextTertiary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(16.dp, 24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragHandleDelta(IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt()))
                        }
                    }
            )

            // Brush Button
            val isBrushActive = currentTool == ToolType.PEN || currentTool == ToolType.BRUSH || currentTool == ToolType.PENCIL || currentTool == ToolType.AIRBRUSH || currentTool == ToolType.CALLIGRAPHY || currentTool == ToolType.PIXEL || currentTool == ToolType.CHARCOAL
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_brush,
                contentDescription = "Brush",
                isActive = isBrushActive,
                onClick = { onSelectTool(ToolType.BRUSH) }
            )

            // Brush Size Quick Scrubber (drag up/down to adjust size)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF333338))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount.y * 0.4f
                            onSizeScrubbed(brushConfig.size + delta)
                        }
                    }
                    .clickable { onSelectTool(ToolType.BRUSH) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${brushConfig.size.toInt()}", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("px", color = TextSecondary, fontSize = 8.sp)
                }
            }

            // Eraser Button
            val isEraserActive = currentTool == ToolType.ERASER
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_eraser,
                contentDescription = "Eraser",
                isActive = isEraserActive,
                onClick = { onSelectTool(ToolType.ERASER) }
            )

            // Lasso Button
            val isLassoActive = currentTool == ToolType.LASSO
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_lasso,
                contentDescription = "Lasso",
                isActive = isLassoActive,
                onClick = { onSelectTool(ToolType.LASSO) }
            )

            // Fill Button
            val isFillActive = currentTool == ToolType.FILL
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_fill,
                contentDescription = "Fill Bucket",
                isActive = isFillActive,
                onClick = { onSelectTool(ToolType.FILL) }
            )

            // Text Button (T)
            val isTextActive = currentTool == ToolType.TEXT
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_text,
                contentDescription = "Text",
                isActive = isTextActive,
                onClick = { onSelectTool(ToolType.TEXT) }
            )

            // Quick Color & Opacity Scrubber (drag up/down for opacity, tap for Pro Color Picker)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF333338))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount.y * 0.005f
                            onOpacityScrubbed(brushConfig.opacity + delta)
                        }
                    }
                    .clickable(onClick = onColorClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(brushConfig.color).copy(alpha = brushConfig.opacity))
                        .border(2.dp, Color.White, CircleShape)
                )
            }

            // More Tools Button (...)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF333338))
                    .clickable(onClick = onToggleMoreTools),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_v2_more_h),
                    contentDescription = "More Tools",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ToolCapsuleButton(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) AccentPrimary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Secondary Tool Card (Revealed when tapping '...')
 * Contains Smudge, Blur, Ruler & Mirror, and Toolkit
 */
@Composable
fun SecondaryToolPopupCard(
    modifier: Modifier = Modifier,
    onSelectSmudge: () -> Unit,
    onSelectBlur: () -> Unit,
    onSelectRuler: () -> Unit,
    onSelectToolkit: () -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Smudge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onSelectSmudge)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_v2_smudge),
                    contentDescription = "Smudge",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Smudge", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // Blur
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onSelectBlur)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_v2_blur),
                    contentDescription = "Blur",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Blur", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // Ruler / Mirror
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onSelectRuler)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_ruler),
                    contentDescription = "Ruler",
                    tint = AccentPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ruler", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // Toolkit
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onSelectToolkit)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_toolkit),
                    contentDescription = "Toolkit",
                    tint = Color(0xFF30B0C7),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Toolkit", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Floating Bottom Timeline Bar
 * Reference: media_1792380169250.png
 * Left: White cursive "Layer" pill
 * Center: Frame thumbnails with pink outline & pink #1 badge, plus dotted add frame button
 * Right: Play button (triangle)
 */
@Composable
fun FloatingBottomTimelineBar(
    modifier: Modifier = Modifier,
    project: Project,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    repository: com.openclip.studio.data.ProjectRepository,
    onSelectFrame: (Int) -> Unit,
    onFrameHoldClick: (Int) -> Unit = {},
    onAddFrame: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenFramesViewer: () -> Unit = {},
    onFrameLongClick: (Int) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(12.dp, RoundedCornerShape(26.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = DarkCapsule
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top tab handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .width(36.dp)
                    .height(3.dp)
                    .background(Color(0xFF55555C), RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Cursive script "Layer" White Pill
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .width(76.dp)
                        .clickable(onClick = onOpenLayers),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Layer",
                            fontFamily = CaveatFont,
                            color = Color.Black,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // CENTER: Frames Carousel
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(project.frameCount) { idx ->
                        val isSelected = idx == currentFrameIndex
                        val thumbBmp = remember(project.id, idx, project.updatedAt) {
                            val f = repository.getThumbnailFile(project.id, idx)
                            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                        }

                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentPrimary else Color(0xFF44444C),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .pointerInput(idx, isSelected) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isSelected) onFrameHoldClick(idx) else onSelectFrame(idx)
                                        },
                                        onLongPress = {
                                            onFrameLongClick(idx)
                                        }
                                    )
                                }
                        ) {
                            if (thumbBmp != null) {
                                Image(
                                    bitmap = thumbBmp.asImageBitmap(),
                                    contentDescription = "Frame ${idx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Active Frame Number Badge (Pink Pill in Top-Right)
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(AccentPrimary, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Frame Hold Duration Badge (Gold Pill in Bottom-Left)
                            val holdDuration = project.frameHolds[idx] ?: 1
                            if (holdDuration > 1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp)
                                        .background(Color(0xFFFFB300), RoundedCornerShape(6.dp))
                                        .clickable { onFrameHoldClick(idx) }
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${holdDuration}x",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Add Frame Button (+)
                    item {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF333338))
                                .border(1.dp, Color(0xFF55555C), RoundedCornerShape(14.dp))
                                .clickable(onClick = onAddFrame),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Frame",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Frames Viewer Grid Button
                IconButton(
                    onClick = onOpenFramesViewer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stage_frames_viewer),
                        contentDescription = "Frames Viewer",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // RIGHT: Play Button
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stage_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bottom Actions Strip matching FlipaClip 5.0.2
 * 6 Actions: Undo, Redo, Copy, Paste, Image/Video, Add audio
 */
@Composable
fun StudioBottomActionStrip(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onImageVideo: () -> Unit,
    onAddAudio: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = DarkActionStrip
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ActionStripItem(
                iconRes = R.drawable.ic_stage_undo,
                label = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )
            ActionStripItem(
                iconRes = R.drawable.ic_stage_redo,
                label = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )
            ActionStripItem(
                iconRes = R.drawable.ic_stage_copy,
                label = "Copy",
                enabled = true,
                onClick = onCopy
            )
            ActionStripItem(
                iconRes = R.drawable.ic_stage_paste,
                label = "Paste",
                enabled = true,
                onClick = onPaste
            )
            ActionStripItem(
                iconRes = R.drawable.ic_stage_image_video,
                label = "Image/Video",
                enabled = true,
                onClick = onImageVideo
            )
            ActionStripItem(
                iconRes = R.drawable.ic_stage_add_audio,
                label = "Add audio",
                enabled = true,
                onClick = onAddAudio
            )
        }
    }
}

@Composable
fun ActionStripItem(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = if (enabled) Color.White else TextTertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (enabled) Color(0xFFDDDDDD) else TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Bottom Settings Sheet matching FlipaClip 5.0.2
 * Reference: media_1792380169131.png
 * Rows: Project settings, Onion skin (Edit + Switch), Grid (Edit + Switch), Magic cut, Make movie button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageSettingsBottomSheet(
    onionEnabled: Boolean,
    gridEnabled: Boolean,
    onDismiss: () -> Unit,
    onProjectSettings: () -> Unit,
    onFramesViewer: () -> Unit,
    onToggleOnion: (Boolean) -> Unit,
    onEditOnion: () -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onEditGrid: () -> Unit,
    onMagicCut: () -> Unit,
    onAddImage: () -> Unit,
    onAddVideo: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    onMakeMovie: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF202022),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF44444A), RoundedCornerShape(2.dp))
            )

            // Row 1: Project settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProjectSettings)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_edit),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Project settings",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 2: Frames viewer (Full screen grid)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFramesViewer)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_frames_viewer),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Frames viewer",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 3: Onion skin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_onion),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Onion skin",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Edit",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onEditOnion)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = onionEnabled,
                    onCheckedChange = onToggleOnion,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = Color(0xFF888890),
                        uncheckedTrackColor = Color(0xFF383840)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 4: Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_grid),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Grid",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Edit",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onEditGrid)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = gridEnabled,
                    onCheckedChange = onToggleGrid,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = Color(0xFF888890),
                        uncheckedTrackColor = Color(0xFF383840)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 5: Magic cut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMagicCut)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_magic_cut),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Magic cut",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(AccentPrimary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 6: Add image
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddImage)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_image_video),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Add image",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 7: Add video (Rotoscoping)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddVideo)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Add video (Rotoscoping)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 8: Export Project Package (.openclip ZIP)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExportProject)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_share),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Export Project Package (.openclip)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 9: Import Project Package (.openclip ZIP)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImportProject)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Import Project Package (.openclip)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Prominent "Make movie" Pink Pill Button
            Button(
                onClick = onMakeMovie,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Make movie",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Brush Settings Dialog (Brush size, opacity, smoothing, color, and types)
 */
@Composable
fun BrushSettingsDialog(
    config: BrushConfig,
    onDismiss: () -> Unit,
    onSelectBrushType: (ToolType) -> Unit,
    onUpdateSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateSmoothing: (Float) -> Unit,
    onOpenColor: () -> Unit
) {
    val brushTypes = listOf(
        Pair("Pen", ToolType.PEN),
        Pair("Pencil", ToolType.PENCIL),
        Pair("Brush", ToolType.BRUSH),
        Pair("Airbrush", ToolType.AIRBRUSH),
        Pair("Marker", ToolType.MARKER),
        Pair("Crayon", ToolType.CRAYON),
        Pair("Pixel", ToolType.PIXEL),
        Pair("Calligraphy", ToolType.CALLIGRAPHY),
        Pair("Leaves", ToolType.LEAF_SCATTER),
        Pair("Charcoal", ToolType.CHARCOAL)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Brush & Line Art Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Brush Types Selection
                Text("BRUSH TYPE", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(brushTypes.size) { i ->
                        val (name, type) = brushTypes[i]
                        val isSel = config.toolType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) AccentPrimary else Color(0xFF222226))
                                .border(1.dp, if (isSel) AccentPrimary else Color(0xFF383840), RoundedCornerShape(10.dp))
                                .clickable { onSelectBrushType(type) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Stroke Smoothing / Stabilizer (Smooth Line Art / Pixelation Fix)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Smooth Line Art (Stabilizer)", color = TextSecondary, fontSize = 13.sp)
                        Text("${(config.strokeSmoothing * 100).toInt()}%", color = Color(0xFF30B0C7), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.strokeSmoothing,
                        onValueChange = onUpdateSmoothing,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF30B0C7), activeTrackColor = Color(0xFF30B0C7))
                    )
                }

                // Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brush Size", color = TextSecondary, fontSize = 13.sp)
                        Text("${config.size.toInt()} px", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.size,
                        onValueChange = onUpdateSize,
                        valueRange = 1f..150f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Opacity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Brush Opacity", color = TextSecondary, fontSize = 13.sp)
                        Text("${(config.opacity * 100).toInt()}%", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.opacity,
                        onValueChange = onUpdateOpacity,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Color Button
                Button(
                    onClick = onOpenColor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(config.color))
                ) {
                    Text(
                        text = "Pro Color Palette & Wheel",
                        color = if (android.graphics.Color.luminance(config.color) > 0.5f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Eraser Settings Dialog (4-in-1 Controls)
 */
@Composable
fun EraserSettingsDialog(
    config: BrushConfig,
    onDismiss: () -> Unit,
    onUpdateSize: (Float) -> Unit,
    onUpdateHardness: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Eraser Settings (4-in-1)", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Eraser Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Eraser Size", color = TextSecondary, fontSize = 13.sp)
                        Text("${config.eraserSize.toInt()} px", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.eraserSize,
                        onValueChange = onUpdateSize,
                        valueRange = 5f..150f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Sharpness / Hardness (Fade)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sharpness / Hardness", color = TextSecondary, fontSize = 13.sp)
                        Text(if (config.eraserHardness < 0.35f) "Soft Fade" else if (config.eraserHardness > 0.8f) "Sharp Crisp" else "Medium", color = Color(0xFF30B0C7), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.eraserHardness,
                        onValueChange = onUpdateHardness,
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF30B0C7), activeTrackColor = Color(0xFF30B0C7))
                    )
                }

                // Opacity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Eraser Opacity", color = TextSecondary, fontSize = 13.sp)
                        Text("${(config.eraserOpacity * 100).toInt()}%", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.eraserOpacity,
                        onValueChange = onUpdateOpacity,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Color Picker Dialog
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val presetColors = listOf(
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
        0xFFFF3B69.toInt(), // Coral
        0xFFFF9500.toInt(), // Orange
        0xFFFFCC00.toInt(), // Yellow
        0xFF34C759.toInt(), // Green
        0xFF00C7BE.toInt(), // Teal
        0xFF30B0C7.toInt(), // Cyan
        0xFF007AFF.toInt(), // Blue
        0xFF5856D6.toInt(), // Purple
        0xFFAF52DE.toInt(), // Violet
        0xFFFF2D55.toInt(), // Pink
        0xFFA2845E.toInt(), // Brown
        0xFF8E8E93.toInt()  // Gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Select Color", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Color Palette", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.take(7).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, Color(0xFF55555C), CircleShape)
                                .clickable { onColorSelected(c) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.drop(7).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, Color(0xFF55555C), CircleShape)
                                .clickable { onColorSelected(c) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentPrimary)
            }
        }
    )
}

/**
 * Text Input Dialog
 */
@Composable
fun TextInputDialog(
    onDismiss: () -> Unit,
    onAddText: (String) -> Unit
) {
    var textValue by remember { mutableStateOf("Animation") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Add Text", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("Text content") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentPrimary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (textValue.isNotBlank()) onAddText(textValue.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Insert Text", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Layer Management Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerManagerBottomSheet(
    project: Project,
    activeLayerId: Int,
    onDismiss: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onUpdateOpacity: (Int, Float) -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onOpenLayerSettings: (Layer) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF242428),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layers (${project.layers.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddLayer) {
                    Icon(Icons.Default.Add, contentDescription = "Add Layer", tint = AccentPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            project.layers.reversed().forEach { layer ->
                val isSelected = layer.id == activeLayerId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectLayer(layer.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF33333C) else Color(0xFF28282E)
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentPrimary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onToggleVisibility(layer.id) }) {
                            Icon(
                                imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Visibility",
                                tint = if (layer.visible) AccentPrimary else TextTertiary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(layer.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (layer.blendMode != BlendMode.NORMAL) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = layer.blendMode.name,
                                        color = AccentPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (layer.alphaLock) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("[Lock]", color = Color(0xFF64D2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                if (layer.glowEnabled) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("[Glow]", color = Color(0xFFFFD60A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                if (layer.pixelatorEnabled) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("[Pixel]", color = Color(0xFF30D158), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Opacity: ${(layer.opacity * 100).toInt()}%", color = TextTertiary, fontSize = 12.sp)
                        }

                        IconButton(onClick = { onOpenLayerSettings(layer) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings_edit),
                                contentDescription = "Layer Settings & FX",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (project.layers.size > 1) {
                            IconButton(onClick = { onDeleteLayer(layer.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF453A))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Export Movie Dialog (Make movie / MP4 / GIF)
 */
@Composable
fun ExportMovieDialog(
    project: Project,
    repository: com.openclip.studio.data.ProjectRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(ExportFormat.MP4_VIDEO) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportResultFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Make Movie", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isExporting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Exporting ${if (format == ExportFormat.MP4_VIDEO) "MP4" else "GIF"}... ${(exportProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                } else if (exportResultFile != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Movie Created Successfully!", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Saved to:", color = TextSecondary, fontSize = 12.sp)
                        Text(exportResultFile?.absolutePath ?: "", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Text("Select format for '${project.title}':", color = TextSecondary, fontSize = 14.sp)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { format = ExportFormat.MP4_VIDEO },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (format == ExportFormat.MP4_VIDEO) AccentPrimary else Color(0xFF33333A)
                                )
                            ) {
                                Text("MP4 Video", color = Color.White)
                            }

                            Button(
                                onClick = { format = ExportFormat.ANIMATED_GIF },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (format == ExportFormat.ANIMATED_GIF) AccentPrimary else Color(0xFF33333A)
                                )
                            ) {
                                Text("GIF Anim", color = Color.White)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { format = ExportFormat.SPRITE_SHEET },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (format == ExportFormat.SPRITE_SHEET) AccentPrimary else Color(0xFF33333A)
                                )
                            ) {
                                Text("Sprite Sheet", color = Color.White)
                            }

                            Button(
                                onClick = { format = ExportFormat.PNG_SEQUENCE },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (format == ExportFormat.PNG_SEQUENCE) AccentPrimary else Color(0xFF33333A)
                                )
                            ) {
                                Text("PNG Seq (ZIP)", color = Color.White)
                            }
                        }
                    }

                    Text("Frames: ${project.frameCount} • FPS: ${project.fps} • ${project.canvasWidth}x${project.canvasHeight}", color = TextTertiary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (!isExporting && exportResultFile == null) {
                Button(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            val outFile = ExportPipeline.exportProject(
                                context = context,
                                project = project,
                                repository = repository,
                                format = format,
                                onProgress = { exportProgress = it }
                            )
                            isExporting = false
                            if (outFile != null) {
                                exportResultFile = outFile
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Start Export", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = AccentPrimary)
                }
            }
        },
        dismissButton = {
            if (!isExporting && exportResultFile == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

/**
 * Image & Video Import Bottom Sheet matching FlipaClip styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageVideoImportBottomSheet(
    onDismiss: () -> Unit,
    onImportImage: () -> Unit,
    onImportVideo: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF222226),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF44444A), RoundedCornerShape(2.dp))
            )

            Text(
                text = "Insert Media",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Add Image
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onImportImage()
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2C2C32)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stage_image_video),
                            contentDescription = "Image",
                            tint = AccentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Add Image",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Insert picture onto current frame & active layer",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Add Video
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onImportVideo()
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2C2C32)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stage_play),
                            contentDescription = "Video",
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Add Video",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Extract video frames directly into your animation timeline",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
