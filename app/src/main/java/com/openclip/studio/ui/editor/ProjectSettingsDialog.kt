package com.openclip.studio.ui.editor

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openclip.studio.core.model.BackgroundType
import com.openclip.studio.core.model.CanvasPreset
import com.openclip.studio.core.model.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSaveSettings: (
        title: String,
        fps: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        bgType: BackgroundType,
        bgColor: Int,
        flipH: Boolean,
        rotation: Float
    ) -> Unit
) {
    var title by remember { mutableStateOf(project.title) }
    var fps by remember { mutableStateOf(project.fps.toFloat()) }
    var selectedPreset by remember {
        mutableStateOf(
            CanvasPreset.ALL.find { it.width == project.canvasWidth && it.height == project.canvasHeight }
                ?: CanvasPreset.DEFAULT
        )
    }
    var backgroundType by remember { mutableStateOf(project.backgroundType) }
    var backgroundColor by remember { mutableStateOf(project.backgroundColor) }
    var flipHorizontal by remember { mutableStateOf(project.canvasFlipH) }
    var rotationAngle by remember { mutableStateOf(project.canvasRotation) }

    // Live animation preview for bouncing ball at selected FPS
    val infiniteTransition = rememberInfiniteTransition(label = "fpsPreview")
    val bounceAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000 / fps.coerceAtLeast(1f)).toInt() * 4,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Project Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Project Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Canvas Format Presets
                Text(
                    text = "Canvas Preset (${selectedPreset.aspectRatioLabel})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                CanvasPreset.ALL.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF2A2A3C))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedPreset = preset }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(preset.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${preset.width} x ${preset.height}", color = Color.Gray, fontSize = 11.sp)
                        }
                        Text(preset.aspectRatioLabel, color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // FPS Slider with Bouncing Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Frame Rate: ${fps.toInt()} FPS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    
                    // Live Bouncing Ball Preview
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2A3C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = ((bounceAnim - 0.5f) * 16).dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF))
                        )
                    }
                }

                Slider(
                    value = fps,
                    onValueChange = { fps = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.DarkGray
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Background Canvas Mode
                Text("Canvas Background", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "White" to (BackgroundType.SOLID to 0xFFFFFFFF.toInt()),
                        "Dark" to (BackgroundType.SOLID to 0xFF1E1E2C.toInt()),
                        "Paper" to (BackgroundType.PAPER_WHITE to 0xFFF9F6F0.toInt()),
                        "Grid" to (BackgroundType.PAPER_GRID to 0xFFFFFFFF.toInt())
                    ).forEach { (label, config) ->
                        val (type, col) = config
                        val isSelected = backgroundType == type && (type != BackgroundType.SOLID || backgroundColor == col)

                        Button(
                            onClick = {
                                backgroundType = type
                                backgroundColor = col
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2A2A3C),
                                contentColor = if (isSelected) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Canvas Orientation & Mirror Options
                Text("Canvas View Options", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flip Canvas Horizontally (Mirror Check)", color = Color.White, fontSize = 13.sp)
                    Switch(
                        checked = flipHorizontal,
                        onCheckedChange = { flipHorizontal = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Action Button
                Button(
                    onClick = {
                        onSaveSettings(
                            title,
                            fps.toInt(),
                            selectedPreset.width,
                            selectedPreset.height,
                            backgroundType,
                            backgroundColor,
                            flipHorizontal,
                            rotationAngle
                        )
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
