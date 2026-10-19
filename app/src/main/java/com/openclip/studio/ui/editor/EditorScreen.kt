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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openclip.studio.core.engine.DrawingCanvasView
import com.openclip.studio.core.export.ExportFormat
import com.openclip.studio.core.export.ExportPipeline
import com.openclip.studio.core.model.BrushConfig
import com.openclip.studio.core.model.OnionConfig
import com.openclip.studio.core.model.Project
import com.openclip.studio.core.model.RulerType
import com.openclip.studio.core.model.ToolType
import com.openclip.studio.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

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

    // Dialog state
    var showBrushDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showLayerSheet by remember { mutableStateOf(false) }
    var showOnionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Canvas reference
    var canvasViewRef by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var frameRevision by remember { mutableStateOf(0) } // triggers layer reload

    // Load active & neighboring bitmaps when frame, layer, or revision changes
    val activeBmp = remember(project.id, currentFrameIndex, activeLayerId, frameRevision) {
        viewModel.repository.loadLayerBitmap(project.id, currentFrameIndex, activeLayerId, project.canvasWidth, project.canvasHeight)
    }

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

    // Update canvas whenever bitmaps or configs change
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
            EditorTopBar(
                title = project.title,
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = {
                    viewModel.stopPlayback()
                    onNavigateBack()
                },
                onUndo = {
                    val cur = canvasViewRef?.activeLayerBitmap ?: return@EditorTopBar
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
                    val cur = canvasViewRef?.activeLayerBitmap ?: return@EditorTopBar
                    val next = viewModel.historyManager.redo(cur)
                    if (next != null) {
                        canvasViewRef?.activeLayerBitmap = next
                        canvasViewRef?.invalidate()
                        viewModel.repository.saveLayerBitmap(project.id, currentFrameIndex, activeLayerId, next)
                        viewModel.repository.updateFrameThumbnail(project, currentFrameIndex)
                        frameRevision++
                    }
                },
                onResetZoom = { canvasViewRef?.resetViewport() },
                onMenuClick = { showOverflowMenu = true },
                isMenuExpanded = showOverflowMenu,
                onDismissMenu = { showOverflowMenu = false },
                onOpenOnion = { showOverflowMenu = false; showOnionDialog = true },
                onOpenExport = { showOverflowMenu = false; showExportDialog = true }
            )
        },
        bottomBar = {
            EditorBottomBar(
                project = project,
                currentFrameIndex = currentFrameIndex,
                isPlaying = isPlaying,
                isLooping = isLooping,
                activeLayerCount = project.layers.size,
                activeLayerIndex = project.layers.indexOfFirst { it.id == activeLayerId } + 1,
                repository = viewModel.repository,
                onSelectFrame = { viewModel.selectFrame(it) },
                onAddFrame = {
                    viewModel.addFrame()
                    frameRevision++
                },
                onDuplicateFrame = {
                    viewModel.duplicateCurrentFrame()
                    frameRevision++
                },
                onDeleteFrame = {
                    viewModel.deleteCurrentFrame()
                    frameRevision++
                },
                onTogglePlay = { viewModel.togglePlayback() },
                onToggleLoop = { viewModel.toggleLoop() },
                onOpenLayers = { showLayerSheet = true }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Central Canvas
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

            // Left Drawing Tools Rail
            DrawingToolsRail(
                currentTool = brushConfig.toolType,
                brushColor = brushConfig.color,
                rulerType = brushConfig.rulerType,
                hasLassoSelection = canvasViewRef?.currentLassoSelection != null,
                onSelectTool = { tool ->
                    if (tool == brushConfig.toolType && (tool == ToolType.PEN || tool == ToolType.PENCIL || tool == ToolType.BRUSH || tool == ToolType.AIRBRUSH || tool == ToolType.ERASER)) {
                        showBrushDialog = true
                    } else {
                        viewModel.setToolType(tool)
                    }
                },
                onOpenColorPicker = { showColorDialog = true },
                onToggleRuler = {
                    val nextRuler = when (brushConfig.rulerType) {
                        RulerType.NONE -> RulerType.LINE
                        RulerType.LINE -> RulerType.CIRCLE
                        RulerType.CIRCLE -> RulerType.RECTANGLE
                        RulerType.RECTANGLE -> RulerType.NONE
                    }
                    viewModel.setRulerType(nextRuler)
                    val label = if (nextRuler == RulerType.NONE) "Ruler Off" else "Ruler: ${nextRuler.name}"
                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                },
                onStampLasso = { canvasViewRef?.stampLassoSelection(); frameRevision++ },
                onDiscardLasso = { canvasViewRef?.discardLassoSelection(); frameRevision++ }
            )
        }
    }

    // Dialogs
    if (showBrushDialog) {
        BrushSettingsDialog(
            config = brushConfig,
            onDismiss = { showBrushDialog = false },
            onUpdateSize = { viewModel.setBrushSize(it) },
            onUpdateOpacity = { viewModel.setBrushOpacity(it) },
            onUpdateEraserSize = { viewModel.setEraserSize(it) },
            onSelectToolType = { viewModel.setToolType(it) }
        )
    }

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

    if (showLayerSheet) {
        LayerBottomSheet(
            layers = project.layers,
            activeLayerId = activeLayerId,
            onDismiss = { showLayerSheet = false },
            onSelectLayer = { viewModel.selectLayer(it) },
            onAddLayer = { viewModel.addLayer(); frameRevision++ },
            onToggleVisibility = { viewModel.toggleLayerVisibility(it); frameRevision++ },
            onSetOpacity = { id, op -> viewModel.setLayerOpacity(id, op); frameRevision++ },
            onDeleteLayer = { viewModel.deleteLayer(it); frameRevision++ }
        )
    }

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

    if (showExportDialog) {
        ExportDialog(
            project = project,
            repository = viewModel.repository,
            selectedFrameIndex = currentFrameIndex,
            onDismiss = { showExportDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onMenuClick: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onOpenOnion: () -> Unit,
    onOpenExport: () -> Unit
) {
    TopAppBar(
        title = {
            Text(title, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) TextPrimary else Color.DarkGray)
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) TextPrimary else Color.DarkGray)
            }
            IconButton(onClick = onResetZoom) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit Viewport", tint = TextPrimary)
            }
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextPrimary)
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.background(DarkSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Onion Skin Settings", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = AccentPrimary) },
                        onClick = onOpenOnion
                    )
                    DropdownMenuItem(
                        text = { Text("Make Movie / Export", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, tint = AccentPrimary) },
                        onClick = onOpenExport
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
    )
}

@Composable
fun DrawingToolsRail(
    currentTool: ToolType,
    brushColor: Int,
    rulerType: RulerType,
    hasLassoSelection: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleRuler: () -> Unit,
    onStampLasso: () -> Unit,
    onDiscardLasso: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp)
            .background(Color(0xDD24242A), RoundedCornerShape(12.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brush
        ToolIconButton(
            icon = Icons.Default.Brush,
            isSelected = currentTool == ToolType.PEN || currentTool == ToolType.PENCIL || currentTool == ToolType.BRUSH || currentTool == ToolType.AIRBRUSH,
            onClick = { onSelectTool(ToolType.PEN) }
        )

        // Eraser
        ToolIconButton(
            icon = Icons.Default.AutoFixHigh,
            isSelected = currentTool == ToolType.ERASER,
            onClick = { onSelectTool(ToolType.ERASER) }
        )

        // Ruler
        ToolIconButton(
            icon = Icons.Default.Straighten,
            isSelected = rulerType != RulerType.NONE,
            onClick = onToggleRuler
        )

        // Lasso
        ToolIconButton(
            icon = Icons.Default.HighlightAlt,
            isSelected = currentTool == ToolType.LASSO,
            onClick = { onSelectTool(ToolType.LASSO) }
        )

        // Fill Bucket
        ToolIconButton(
            icon = Icons.Default.FormatColorFill,
            isSelected = currentTool == ToolType.FILL,
            onClick = { onSelectTool(ToolType.FILL) }
        )

        // Eyedropper
        ToolIconButton(
            icon = Icons.Default.Colorize,
            isSelected = currentTool == ToolType.EYEDROPPER,
            onClick = { onSelectTool(ToolType.EYEDROPPER) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Color Swatch
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(brushColor))
                .border(2.dp, Color.White, CircleShape)
                .clickable { onOpenColorPicker() }
        )

        // Lasso Action Buttons if selection is active
        if (hasLassoSelection) {
            Spacer(modifier = Modifier.height(4.dp))
            IconButton(onClick = onStampLasso, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Check, contentDescription = "Stamp", tint = Color.Green)
            }
            IconButton(onClick = onDiscardLasso, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Discard", tint = Color.Red)
            }
        }
    }
}

@Composable
fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(if (isSelected) AccentPrimary else Color.Transparent, RoundedCornerShape(8.dp))
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) Color.White else TextPrimary)
    }
}

@Composable
fun EditorBottomBar(
    project: Project,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    isLooping: Boolean,
    activeLayerCount: Int,
    activeLayerIndex: Int,
    repository: com.openclip.studio.data.ProjectRepository,
    onSelectFrame: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onOpenLayers: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Timeline Carousel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Frames count label
                Text(
                    text = "${currentFrameIndex + 1} / ${project.frameCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary,
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Scrollable Frame Thumbnails
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed((0 until project.frameCount).toList()) { idx, frame ->
                        val thumbFile = repository.getThumbnailFile(project.id, idx)
                        val thumbBmp = remember(thumbFile, frame) {
                            if (thumbFile.exists()) BitmapFactory.decodeFile(thumbFile.absolutePath) else null
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(
                                    width = if (idx == currentFrameIndex) 2.5.dp else 1.dp,
                                    color = if (idx == currentFrameIndex) AccentPrimary else DarkBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectFrame(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbBmp != null) {
                                Image(
                                    bitmap = thumbBmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = "${idx + 1}",
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color(0x99000000), RoundedCornerShape(topEnd = 4.dp))
                                    .padding(horizontal = 3.dp)
                            )
                        }
                    }

                    // Add Frame Button
                    item {
                        IconButton(
                            onClick = onAddFrame,
                            modifier = Modifier
                                .size(width = 48.dp, height = 48.dp)
                                .background(AccentPrimary, RoundedCornerShape(6.dp))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Frame", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action & Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Frame operations
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onDuplicateFrame,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Clone", fontSize = 11.sp)
                    }
                    if (project.frameCount > 1) {
                        OutlinedButton(
                            onClick = onDeleteFrame,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Del", fontSize = 11.sp)
                        }
                    }
                }

                // Playback controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleLoop,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) AccentPrimary else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(38.dp)
                            .background(AccentPrimary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White
                        )
                    }
                }

                // Layers button
                Button(
                    onClick = onOpenLayers,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("L $activeLayerIndex/$activeLayerCount", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}

// Dialog implementations
@Composable
fun BrushSettingsDialog(
    config: BrushConfig,
    onDismiss: () -> Unit,
    onUpdateSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateEraserSize: (Float) -> Unit,
    onSelectToolType: (ToolType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Brush & Eraser Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Brush Type selector
                Text("Brush Type", fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ToolType.PEN to "Pen",
                        ToolType.PENCIL to "Pencil",
                        ToolType.MARKER to "Marker",
                        ToolType.AIRBRUSH to "Airbrush"
                    ).forEach { (tool, label) ->
                        FilterChip(
                            selected = config.toolType == tool,
                            onClick = { onSelectToolType(tool) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Size Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stroke Width", fontSize = 13.sp, color = TextSecondary)
                        Text("${config.size.toInt()} px", fontSize = 13.sp, color = AccentPrimary)
                    }
                    Slider(
                        value = config.size,
                        onValueChange = onUpdateSize,
                        valueRange = 2f..100f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Opacity Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opacity", fontSize = 13.sp, color = TextSecondary)
                        Text("${(config.opacity * 100).toInt()}%", fontSize = 13.sp, color = AccentPrimary)
                    }
                    Slider(
                        value = config.opacity,
                        onValueChange = onUpdateOpacity,
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }

                // Eraser Size
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Eraser Size", fontSize = 13.sp, color = TextSecondary)
                        Text("${config.eraserSize.toInt()} px", fontSize = 13.sp, color = AccentPrimary)
                    }
                    Slider(
                        value = config.eraserSize,
                        onValueChange = onUpdateEraserSize,
                        valueRange = 5f..200f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)) {
                Text("Done")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val presets = listOf(
        0xFF000000.toInt(), // Black
        0xFFFFFFFF.toInt(), // White
        0xFFFF5722.toInt(), // Orange
        0xFFF44336.toInt(), // Red
        0xFFE91E63.toInt(), // Pink
        0xFF9C27B0.toInt(), // Purple
        0xFF2196F3.toInt(), // Blue
        0xFF00BCD4.toInt(), // Cyan
        0xFF4CAF50.toInt(), // Green
        0xFFFFEB3B.toInt(), // Yellow
        0xFF795548.toInt(), // Brown
        0xFF9E9E9E.toInt()  // Grey
    )

    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Palette", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Color Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(selectedColor))
                        .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                )

                // Swatch Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = presets.chunked(6)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            width = if (c == selectedColor) 3.dp else 1.dp,
                                            color = if (c == selectedColor) AccentPrimary else Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = c }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(selectedColor) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerBottomSheet(
    layers: List<com.openclip.studio.core.model.Layer>,
    activeLayerId: Int,
    onDismiss: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onSetOpacity: (Int, Float) -> Unit,
    onDeleteLayer: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layers (${layers.size}) - 100% Free Unlimited", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                IconButton(onClick = onAddLayer) {
                    Icon(Icons.Default.Add, contentDescription = "Add Layer", tint = AccentPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stacked layers (Top to Bottom)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                layers.reversed().forEach { layer ->
                    val isSelected = layer.id == activeLayerId

                    Surface(
                        color = if (isSelected) DarkSurfaceVariant else Color(0xFF222228),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AccentPrimary else DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectLayer(layer.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleVisibility(layer.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = if (layer.visible) TextPrimary else Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = layer.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )

                            // Opacity display
                            Text("${(layer.opacity * 100).toInt()}%", fontSize = 12.sp, color = TextSecondary)

                            if (layers.size > 1) {
                                IconButton(onClick = { onDeleteLayer(layer.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OnionSkinDialog(
    config: OnionConfig,
    onDismiss: () -> Unit,
    onSave: (OnionConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var beforeFrames by remember { mutableStateOf(config.framesBefore.toFloat()) }
    var afterFrames by remember { mutableStateOf(config.framesAfter.toFloat()) }
    var startOpacity by remember { mutableStateOf(config.startOpacity) }
    var colored by remember { mutableStateOf(config.coloredOnion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Onion Skinning", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Onion Skin", color = TextPrimary)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Frames Before", fontSize = 13.sp, color = TextSecondary)
                            Text("${beforeFrames.toInt()}", fontSize = 13.sp, color = OnionPastRed, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = beforeFrames,
                            onValueChange = { beforeFrames = it },
                            valueRange = 0f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(thumbColor = OnionPastRed, activeTrackColor = OnionPastRed)
                        )
                    }

                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Frames After", fontSize = 13.sp, color = TextSecondary)
                            Text("${afterFrames.toInt()}", fontSize = 13.sp, color = OnionFutureGreen, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = afterFrames,
                            onValueChange = { afterFrames = it },
                            valueRange = 0f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(thumbColor = OnionFutureGreen, activeTrackColor = OnionFutureGreen)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color Coded (Red / Green)", color = TextPrimary)
                        Switch(checked = colored, onCheckedChange = { colored = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            enabled = enabled,
                            framesBefore = beforeFrames.toInt(),
                            framesAfter = afterFrames.toInt(),
                            startOpacity = startOpacity,
                            coloredOnion = colored
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun ExportDialog(
    project: Project,
    repository: com.openclip.studio.data.ProjectRepository,
    selectedFrameIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(ExportFormat.MP4_VIDEO) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Column {
                Text("Make Movie / Export", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("100% Free • No Watermarks Ever", fontSize = 11.sp, color = AccentPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isExporting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { exportProgress },
                            color = AccentPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Rendering Animation...", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${(exportProgress * 100).toInt()}% completed", color = TextSecondary, fontSize = 12.sp)
                    }
                } else if (exportedFile != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Export Complete!", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(exportedFile!!.name, fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Saved to Movies/OpenClip", fontSize = 11.sp, color = AccentPrimary)
                    }
                } else {
                    Text("Select Output Format", fontSize = 13.sp, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ExportFormat.MP4_VIDEO to "MP4 Video (H.264)",
                            ExportFormat.ANIMATED_GIF to "Animated GIF",
                            ExportFormat.PNG_SEQUENCE to "PNG Image Sequence (.zip)",
                            ExportFormat.SINGLE_FRAME_PNG to "Current Frame (PNG)"
                        ).forEach { (format, label) ->
                            FilterChip(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text("Settings: ${project.canvasWidth}x${project.canvasHeight} • ${project.fps} FPS • ${project.frameCount} frames", fontSize = 12.sp, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            if (exportedFile != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Close")
                }
            } else if (!isExporting) {
                Button(
                    onClick = {
                        isExporting = true
                        exportProgress = 0f
                        coroutineScope.launch {
                            val f = ExportPipeline.exportProject(
                                context = context,
                                project = project,
                                repository = repository,
                                format = selectedFormat,
                                selectedFrameIndex = selectedFrameIndex,
                                onProgress = { exportProgress = it }
                            )
                            isExporting = false
                            exportedFile = f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Make Movie")
                }
            }
        },
        dismissButton = {
            if (!isExporting && exportedFile == null) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        },
        containerColor = DarkSurface
    )
}
