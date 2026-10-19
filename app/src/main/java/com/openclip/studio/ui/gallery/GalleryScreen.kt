package com.openclip.studio.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.CanvasPreset
import com.openclip.studio.core.model.Project
import com.openclip.studio.data.ProjectRepository
import com.openclip.studio.ui.theme.AccentPrimary
import com.openclip.studio.ui.theme.DarkBorder
import com.openclip.studio.ui.theme.DarkSurface
import com.openclip.studio.ui.theme.DarkSurfaceVariant
import com.openclip.studio.ui.theme.TextPrimary
import com.openclip.studio.ui.theme.TextSecondary
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
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("OpenClip", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                            Text("100% Free Animation Studio", fontSize = 11.sp, color = AccentPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AccentPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No projects yet", fontSize = 18.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Tap + to start your 2D animation", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
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
    val thumbFile = remember(project.id, project.updatedAt) {
        repository.getProjectCoverThumbnailFile(project.id)
    }
    val thumbnailBitmap = remember(thumbFile) {
        if (thumbFile.exists()) {
            BitmapFactory.decodeFile(thumbFile.absolutePath)
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
                // FPS badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${project.fps} FPS", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        color = TextSecondary
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
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
                            text = { Text("Delete", color = Color.Red) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, preset: CanvasPreset, fps: Int, bgType: BackgroundType, bgColor: Int) -> Unit
) {
    var title by remember { mutableStateOf("Animation #${(100..999).random()}") }
    var selectedPreset by remember { mutableStateOf(CanvasPreset.DEFAULT) }
    var fps by remember { mutableStateOf(12f) }
    var bgType by remember { mutableStateOf(BackgroundType.SOLID) }
    var bgColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var presetMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Animation Project", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary
                    )
                )

                // Canvas Preset selector
                Column {
                    Text("Canvas Size & Ratio", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { presetMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("${selectedPreset.name} (${selectedPreset.aspectRatioLabel})")
                        }
                        DropdownMenu(
                            expanded = presetMenuExpanded,
                            onDismissRequest = { presetMenuExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            CanvasPreset.ALL.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} (${p.width}x${p.height})", color = TextPrimary) },
                                    onClick = {
                                        selectedPreset = p
                                        presetMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // FPS Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Rate", fontSize = 13.sp, color = TextSecondary)
                        Text("${fps.toInt()} FPS", fontSize = 13.sp, color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = fps,
                        onValueChange = { fps = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPrimary,
                            activeTrackColor = AccentPrimary
                        )
                    )
                }

                // Background Type selector
                Column {
                    Text("Background Canvas", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BackgroundOptionChip("White", bgType == BackgroundType.SOLID && bgColor == android.graphics.Color.WHITE) {
                            bgType = BackgroundType.SOLID
                            bgColor = android.graphics.Color.WHITE
                        }
                        BackgroundOptionChip("Dark", bgType == BackgroundType.SOLID && bgColor == android.graphics.Color.BLACK) {
                            bgType = BackgroundType.SOLID
                            bgColor = android.graphics.Color.BLACK
                        }
                        BackgroundOptionChip("Grid", bgType == BackgroundType.PAPER_GRID) {
                            bgType = BackgroundType.PAPER_GRID
                        }
                        BackgroundOptionChip("Vintage", bgType == BackgroundType.PAPER_VINTAGE) {
                            bgType = BackgroundType.PAPER_VINTAGE
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, selectedPreset, fps.toInt(), bgType, bgColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun BackgroundOptionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentPrimary,
            selectedLabelColor = Color.White,
            containerColor = DarkSurfaceVariant,
            labelColor = TextPrimary
        )
    )
}
