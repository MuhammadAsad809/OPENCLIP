package com.openclip.studio.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclip.studio.R
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.CanvasPreset
import com.openclip.studio.core.model.Project
import com.openclip.studio.data.ProjectRepository
import com.openclip.studio.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    repository: ProjectRepository,
    onOpenProject: (String) -> Unit
) {
    var projects by remember { mutableStateOf(repository.getAllProjects()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stage_studio),
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("OpenClip Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            Text("100% Free Animation", fontSize = 11.sp, color = AccentPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AccentPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stage_studio),
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No projects yet", fontSize = 18.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Tap + to create your first animation", fontSize = 14.sp, color = TextTertiary)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            repository = repository,
                            onClick = { onOpenProject(project.id) },
                            onDuplicate = {
                                repository.duplicateProject(project.id)
                                projects = repository.getAllProjects()
                            },
                            onDelete = {
                                repository.deleteProject(project.id)
                                projects = repository.getAllProjects()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, preset, fps, bgType, bgColor ->
                val newProject = repository.createProject(
                    title = title,
                    width = preset.width,
                    height = preset.height,
                    fps = fps,
                    backgroundColor = bgColor,
                    backgroundType = bgType
                )
                showCreateDialog = false
                projects = repository.getAllProjects()
                onOpenProject(newProject.id)
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    repository: ProjectRepository,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val thumbBmp = remember(project.id, project.updatedAt) {
        val f = repository.getThumbnailFile(project.id, 0)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.White)
            ) {
                if (thumbBmp != null) {
                    Image(
                        bitmap = thumbBmp.asImageBitmap(),
                        contentDescription = project.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }

                // FPS Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC1E1E22), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${project.fps} FPS", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.frameCount} frames • ${project.canvasWidth}x${project.canvasHeight}",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextTertiary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = TextPrimary) },
                            onClick = { menuExpanded = false; onDuplicate() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF453A)) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pixel-perfect recreation of FlipaClip 5.0.2 "New project" creation screen
 * Reference: media_1792380169223.png
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, preset: CanvasPreset, fps: Int, bgType: BackgroundType, bgColor: Int) -> Unit
) {
    var title by remember { mutableStateOf("Name your animation") }
    var selectedPreset by remember { mutableStateOf(CanvasPreset.DEFAULT) } // YouTube 720p
    var fps by remember { mutableStateOf(12) }
    var bgType by remember { mutableStateOf(BackgroundType.SOLID) }
    var bgColor by remember { mutableStateOf(android.graphics.Color.WHITE) }

    var showPresetSheet by remember { mutableStateOf(false) }
    var showFpsSheet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF202022)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Top Close button (X)
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stage_close),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: Project name
                Text(
                    text = "Project name",
                    color = AccentPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Underlined project name input
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(AccentPrimary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Coral Underline
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(AccentPrimary)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // SECTION 2: Choose background
                Text(
                    text = "Choose background",
                    color = AccentPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Background Preview Card with floating bottom 4-button pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            when (bgType) {
                                BackgroundType.SOLID -> Color(bgColor)
                                BackgroundType.PAPER_WHITE -> Color(0xFFFAF9F6)
                                BackgroundType.PAPER_VINTAGE -> Color(0xFFF4EAD4)
                                BackgroundType.PAPER_GRID -> Color(0xFFF5F5F7)
                                BackgroundType.TRANSPARENT -> Color.White
                            }
                        )
                ) {
                    // Floating 4-button pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E1E22),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    bgType = when (bgType) {
                                        BackgroundType.SOLID -> BackgroundType.PAPER_WHITE
                                        BackgroundType.PAPER_WHITE -> BackgroundType.PAPER_GRID
                                        BackgroundType.PAPER_GRID -> BackgroundType.PAPER_VINTAGE
                                        BackgroundType.PAPER_VINTAGE -> BackgroundType.SOLID
                                        BackgroundType.TRANSPARENT -> BackgroundType.SOLID
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bg_preset),
                                    contentDescription = "Presets",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    bgColor = if (bgColor == android.graphics.Color.WHITE) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                    bgType = BackgroundType.SOLID
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bg_color),
                                    contentDescription = "Color",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { /* Gallery import stub */ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bg_import),
                                    contentDescription = "Gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { /* Camera background stub */ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bg_camera),
                                    contentDescription = "Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // SECTION 3: Format
                Text(
                    text = "Format",
                    color = AccentPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Row: Choose canvas size -> YouTube (720p)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPresetSheet = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose canvas size",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = selectedPreset.name,
                        color = AccentPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row: Choose frames per second -> 12 FPS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFpsSheet = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose frames per second",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$fps FPS",
                        color = AccentPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // BIG PILL: CREATE PROJECT
                val isValid = title.trim().isNotEmpty()
                Button(
                    onClick = {
                        if (isValid) {
                            onCreate(title.trim(), selectedPreset, fps, bgType, bgColor)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isValid) AccentPrimary else AccentMutedRed,
                        contentColor = if (isValid) Color.White else TextTertiary
                    ),
                    enabled = isValid
                ) {
                    Text(
                        text = "CREATE PROJECT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Canvas Size Selection Sheet
    if (showPresetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetSheet = false },
            containerColor = Color(0xFF28282C),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Select Canvas Size", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                CanvasPreset.ALL.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPreset = preset
                                showPresetSheet = false
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(preset.name, color = Color.White, fontSize = 16.sp)
                        Text("${preset.width}x${preset.height} (${preset.aspectRatioLabel})", color = AccentPrimary, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Color(0xFF383840))
                }
            }
        }
    }

    // FPS Selection Sheet
    if (showFpsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFpsSheet = false },
            containerColor = Color(0xFF28282C),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Frame Rate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("$fps FPS", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = Color(0xFF383840)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showFpsSheet = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
