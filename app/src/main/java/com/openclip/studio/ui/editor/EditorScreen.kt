package com.openclip.studio.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.openclip.studio.R
import com.openclip.studio.core.engine.DrawingCanvasView
import com.openclip.studio.core.export.ExportFormat
import com.openclip.studio.core.export.ExportPipeline
import com.openclip.studio.core.model.*
import com.openclip.studio.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

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
    var showGrid by remember { mutableStateOf(false) }

    // Canvas view reference & revision counter
    var canvasViewRef by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var frameRevision by remember { mutableStateOf(0) }

    // Copied frame buffer for copy/paste
    var copiedFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

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

    // Onion skin bitmaps
    val onionBeforeBmps = remember(project.id, currentFrameIndex, onionConfig, frameRevision) {
        val list = mutableListOf<Bitmap>()
        if (onionConfig.enabled && currentFrameIndex > 0) {
            val count = onionConfig.framesBefore.coerceAtMost(currentFrameIndex)
            for (offset in 1..count) {
                list.add(viewModel.repository.getCompositedFrame(project, currentFrameIndex - offset))
            }
        }
        list
    }

    val onionAfterBmps = remember(project.id, currentFrameIndex, onionConfig, frameRevision) {
        val list = mutableListOf<Bitmap>()
        if (onionConfig.enabled && currentFrameIndex < project.frameCount - 1) {
            val count = onionConfig.framesAfter.coerceAtMost(project.frameCount - 1 - currentFrameIndex)
            for (offset in 1..count) {
                list.add(viewModel.repository.getCompositedFrame(project, currentFrameIndex + offset))
            }
        }
        list
    }

    // Sync state with Canvas view
    LaunchedEffect(activeBmp, bgBmps, fgBmps, onionBeforeBmps, onionAfterBmps, brushConfig, onionConfig, project) {
        canvasViewRef?.let { cv ->
            cv.currentProject = project
            cv.currentFrameIndex = currentFrameIndex
            cv.activeLayerId = activeLayerId
            cv.brushConfig = brushConfig
            cv.onionConfig = onionConfig
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
                    Toast.makeText(context, "Import Image/Video to Frame", Toast.LENGTH_SHORT).show()
                },
                onAddAudio = {
                    Toast.makeText(context, "Audio Studio: Add audio tracks & music", Toast.LENGTH_SHORT).show()
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

            // FLOATING TOOL CAPSULE (Near Top)
            FloatingToolCapsule(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                currentTool = brushConfig.toolType,
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
                onToggleMoreTools = {
                    showSecondaryTools = !showSecondaryTools
                }
            )

            // SECONDARY TOOL POPUP CARD (Smudge & Blur)
            if (showSecondaryTools) {
                SecondaryToolPopupCard(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 74.dp),
                    onSelectSmudge = {
                        viewModel.setToolType(ToolType.SMUDGE)
                        showSecondaryTools = false
                        Toast.makeText(context, "Smudge Tool selected", Toast.LENGTH_SHORT).show()
                    },
                    onSelectBlur = {
                        viewModel.setToolType(ToolType.BLUR)
                        showSecondaryTools = false
                        Toast.makeText(context, "Blur Tool selected", Toast.LENGTH_SHORT).show()
                    }
                )
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
                onAddFrame = {
                    viewModel.addFrame()
                    frameRevision++
                },
                onTogglePlay = { viewModel.togglePlayback() },
                onOpenLayers = { showLayerSheet = true }
            )
        }
    }

    // STAGE SETTINGS BOTTOM SHEET (Matches media_1792380169131.png)
    if (showStageSettingsSheet) {
        StageSettingsBottomSheet(
            onionEnabled = onionConfig.enabled,
            gridEnabled = showGrid,
            onDismiss = { showStageSettingsSheet = false },
            onProjectSettings = {
                showStageSettingsSheet = false
                showProjectSettingsDialog = true
            },
            onToggleOnion = { enabled ->
                viewModel.setOnionEnabled(enabled)
            },
            onEditOnion = {
                showStageSettingsSheet = false
                showOnionDialog = true
            },
            onToggleGrid = { enabled ->
                showGrid = enabled
                Toast.makeText(context, if (enabled) "Grid Enabled" else "Grid Disabled", Toast.LENGTH_SHORT).show()
            },
            onMagicCut = {
                showStageSettingsSheet = false
                viewModel.setToolType(ToolType.LASSO)
                Toast.makeText(context, "Magic Cut: Draw lasso around object to cut", Toast.LENGTH_LONG).show()
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
            onUpdateSize = { viewModel.setBrushSize(it) },
            onUpdateOpacity = { viewModel.setBrushOpacity(it) },
            onOpenColor = {
                showBrushDialog = false
                showColorDialog = true
            }
        )
    }

    // ERASER SETTINGS DIALOG
    if (showEraserDialog) {
        EraserSettingsDialog(
            eraserSize = brushConfig.eraserSize,
            onDismiss = { showEraserDialog = false },
            onUpdateSize = { viewModel.setEraserSize(it) }
        )
    }

    // COLOR PICKER DIALOG
    if (showColorDialog) {
        ColorPickerDialog(
            initialColor = brushConfig.color,
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                viewModel.setBrushColor(it)
                showColorDialog = false
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
            }
        )
    }

    // ONION SKIN SETTINGS DIALOG
    if (showOnionDialog) {
        OnionSkinDialog(
            config = onionConfig,
            onDismiss = { showOnionDialog = false },
            onSave = {
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

    // PROJECT SETTINGS DIALOG
    if (showProjectSettingsDialog) {
        ProjectSettingsDialog(
            project = project,
            onDismiss = { showProjectSettingsDialog = false },
            onSave = { updated ->
                viewModel.repository.saveProject(updated)
                showProjectSettingsDialog = false
            }
        )
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

            Spacer(modifier = Modifier.width(4.dp))

            // Coral Diamond
            IconButton(onClick = onDiamondClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_diamond),
                    contentDescription = "Features",
                    tint = AccentPrimary,
                    modifier = Modifier.size(24.dp)
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

            // Coral Share
            IconButton(onClick = onShareClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stage_share),
                    contentDescription = "Share",
                    tint = AccentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
    onSelectTool: (ToolType) -> Unit,
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
            )

            // Brush Button
            val isBrushActive = currentTool == ToolType.PEN || currentTool == ToolType.BRUSH || currentTool == ToolType.PENCIL || currentTool == ToolType.AIRBRUSH
            ToolCapsuleButton(
                iconRes = R.drawable.ic_stage_v2_brush,
                contentDescription = "Brush",
                isActive = isBrushActive,
                onClick = { onSelectTool(ToolType.BRUSH) }
            )

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
 * Reference: media_1792380169377.png
 * Contains Smudge and Blur tools
 */
@Composable
fun SecondaryToolPopupCard(
    modifier: Modifier = Modifier,
    onSelectSmudge: () -> Unit,
    onSelectBlur: () -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
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
                    modifier = Modifier.size(28.dp)
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
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Blur", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
    onAddFrame: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenLayers: () -> Unit
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
                                .clickable { onSelectFrame(idx) }
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

                Spacer(modifier = Modifier.width(10.dp))

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
    onToggleOnion: (Boolean) -> Unit,
    onEditOnion: () -> Unit,
    onToggleGrid: (Boolean) -> Unit,
    onMagicCut: () -> Unit,
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
                    .padding(horizontal = 24.dp, vertical = 14.dp),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.8.dp)

            // Row 2: Onion skin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Edit",
                    color = Color.White,
                    fontSize = 15.sp,
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

            // Row 3: Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Edit",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

            // Row 4: Magic cut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMagicCut)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
 * Brush Settings Dialog (Brush size, opacity, color)
 */
@Composable
fun BrushSettingsDialog(
    config: BrushConfig,
    onDismiss: () -> Unit,
    onUpdateSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onOpenColor: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Brush Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Size", color = TextSecondary, fontSize = 13.sp)
                        Text("${config.size.toInt()} px", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = config.size,
                        onValueChange = onUpdateSize,
                        valueRange = 1f..100f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Opacity
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", color = TextSecondary, fontSize = 13.sp)
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
                        text = "Change Brush Color",
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
 * Eraser Settings Dialog
 */
@Composable
fun EraserSettingsDialog(
    eraserSize: Float,
    onDismiss: () -> Unit,
    onUpdateSize: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Eraser Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Eraser Size", color = TextSecondary, fontSize = 13.sp)
                    Text("${eraserSize.toInt()} px", color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = eraserSize,
                    onValueChange = onUpdateSize,
                    valueRange = 5f..150f,
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
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
    onDeleteLayer: (Int) -> Unit
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
                            Text(layer.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Opacity: ${(layer.opacity * 100).toInt()}%", color = TextTertiary, fontSize = 12.sp)
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
 * Onion Skin Configuration Dialog
 */
@Composable
fun OnionSkinDialog(
    config: OnionConfig,
    onDismiss: () -> Unit,
    onSave: (OnionConfig) -> Unit
) {
    var framesBefore by remember { mutableStateOf(config.framesBefore.toFloat()) }
    var framesAfter by remember { mutableStateOf(config.framesAfter.toFloat()) }
    var startOpacity by remember { mutableStateOf(config.startOpacity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Onion Skin Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frames Before (Past)", color = TextSecondary)
                        Text("${framesBefore.toInt()}", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = framesBefore,
                        onValueChange = { framesBefore = it },
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frames After (Future)", color = TextSecondary)
                        Text("${framesAfter.toInt()}", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = framesAfter,
                        onValueChange = { framesAfter = it },
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", color = TextSecondary)
                        Text("${(startOpacity * 100).toInt()}%", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = startOpacity,
                        onValueChange = { startOpacity = it },
                        valueRange = 0.1f..0.9f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            framesBefore = framesBefore.toInt(),
                            framesAfter = framesAfter.toInt(),
                            startOpacity = startOpacity
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Save", color = Color.White)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            Text("GIF Animation", color = Color.White)
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
 * Project Settings Dialog (Title, FPS)
 */
@Composable
fun ProjectSettingsDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSave: (Project) -> Unit
) {
    var title by remember { mutableStateOf(project.title) }
    var fps by remember { mutableStateOf(project.fps.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Project Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentPrimary
                    )
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Rate", color = TextSecondary)
                        Text("${fps.toInt()} FPS", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fps,
                        onValueChange = { fps = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(project.copy(title = title, fps = fps.toInt())) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
