package com.openclip.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclip.studio.core.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FramesViewerDialog(
    project: Project,
    currentFrameIndex: Int,
    frameThumbnails: Map<Int, Bitmap>,
    onDismiss: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddFrameAfter: (Int) -> Unit,
    onAddFrameBefore: (Int) -> Unit,
    onAddBetweenAllFrames: () -> Unit,
    onDuplicateFrames: (List<Int>) -> Unit,
    onDeleteFrames: (List<Int>) -> Unit,
    onMoveFrame: (fromIndex: Int, toIndex: Int) -> Unit,
    onShareFrame: (Int) -> Unit
) {
    val selectedFrames = remember { mutableStateListOf<Int>() }
    var clipboardFrames by remember { mutableStateOf<List<Int>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF14141E)
        ) {
            Scaffold(
                containerColor = Color(0xFF14141E),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Frames Viewer",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (selectedFrames.isEmpty()) "${project.frameCount} Total Frames" else "${selectedFrames.size} Selected",
                                    fontSize = 12.sp,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        },
                        actions = {
                            if (selectedFrames.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        if (selectedFrames.size == project.frameCount) {
                                            selectedFrames.clear()
                                        } else {
                                            selectedFrames.clear()
                                            selectedFrames.addAll(0 until project.frameCount)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (selectedFrames.size == project.frameCount) "Deselect All" else "Select All",
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF1E1E2C)
                        )
                    )
                },
                bottomBar = {
                    Surface(
                        color = Color(0xFF1E1E2C),
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy
                            IconButton(
                                onClick = {
                                    if (selectedFrames.isNotEmpty()) {
                                        clipboardFrames = selectedFrames.toList()
                                    }
                                },
                                enabled = selectedFrames.isNotEmpty()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = if (selectedFrames.isNotEmpty()) Color.White else Color.Gray)
                                    Text("Copy", fontSize = 9.sp, color = if (selectedFrames.isNotEmpty()) Color.White else Color.Gray)
                                }
                            }

                            // Paste
                            IconButton(
                                onClick = {
                                    if (clipboardFrames.isNotEmpty()) {
                                        onDuplicateFrames(clipboardFrames)
                                    }
                                },
                                enabled = clipboardFrames.isNotEmpty()
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = if (clipboardFrames.isNotEmpty()) Color(0xFF00E5FF) else Color.Gray)
                                    Text("Paste", fontSize = 9.sp, color = if (clipboardFrames.isNotEmpty()) Color(0xFF00E5FF) else Color.Gray)
                                }
                            }

                            // Add Between All
                            IconButton(
                                onClick = onAddBetweenAllFrames
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CallSplit, contentDescription = "Between All", tint = Color.White)
                                    Text("Between All", fontSize = 9.sp, color = Color.White)
                                }
                            }

                            // Add Frame
                            IconButton(
                                onClick = {
                                    val target = selectedFrames.lastOrNull() ?: (project.frameCount - 1)
                                    onAddFrameAfter(target)
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Frame", tint = Color(0xFF00E5FF))
                                    Text("Add", fontSize = 9.sp, color = Color(0xFF00E5FF))
                                }
                            }

                            // Share Frame
                            IconButton(
                                onClick = {
                                    val target = selectedFrames.firstOrNull() ?: currentFrameIndex
                                    onShareFrame(target)
                                }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                                    Text("Share", fontSize = 9.sp, color = Color.White)
                                }
                            }

                            // Delete
                            IconButton(
                                onClick = {
                                    if (selectedFrames.isNotEmpty() && project.frameCount > 1) {
                                        onDeleteFrames(selectedFrames.toList())
                                        selectedFrames.clear()
                                    }
                                },
                                enabled = selectedFrames.isNotEmpty() && project.frameCount > selectedFrames.size
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (selectedFrames.isNotEmpty() && project.frameCount > selectedFrames.size) Color(0xFFFF5252) else Color.Gray)
                                    Text("Delete", fontSize = 9.sp, color = if (selectedFrames.isNotEmpty() && project.frameCount > selectedFrames.size) Color(0xFFFF5252) else Color.Gray)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed((0 until project.frameCount).toList()) { index, frameNum ->
                        val isSelected = selectedFrames.contains(frameNum)
                        val isCurrent = currentFrameIndex == frameNum
                        val thumbnail = frameThumbnails[frameNum]

                        Box(
                            modifier = Modifier
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A2A3C))
                                .border(
                                    width = if (isSelected) 2.5.dp else if (isCurrent) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) Color(0xFF00E5FF) else if (isCurrent) Color(0xFFFFD700) else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (selectedFrames.isEmpty()) {
                                        onSelectFrame(frameNum)
                                        onDismiss()
                                    } else {
                                        if (isSelected) selectedFrames.remove(frameNum) else selectedFrames.add(frameNum)
                                    }
                                }
                        ) {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = "Frame $frameNum",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("F${frameNum + 1}", color = Color.Gray, fontSize = 14.sp)
                                }
                            }

                            // Frame Index Badge
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${frameNum + 1}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Multi-Select Checkbox
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF00E5FF) else Color.Black.copy(alpha = 0.5f))
                                    .clickable {
                                        if (isSelected) selectedFrames.remove(frameNum) else selectedFrames.add(frameNum)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
