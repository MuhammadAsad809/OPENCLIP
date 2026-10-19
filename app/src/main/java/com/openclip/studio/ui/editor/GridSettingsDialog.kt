package com.openclip.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.openclip.studio.core.model.GridConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridSettingsDialog(
    config: GridConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (GridConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var hSpacing by remember { mutableStateOf(config.hSpacing.toFloat()) }
    var vSpacing by remember { mutableStateOf(config.vSpacing.toFloat()) }
    var opacity by remember { mutableStateOf(config.opacity) }
    var color by remember { mutableStateOf(config.color) }
    var isIsometric by remember { mutableStateOf(config.isIsometric) }

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
                        text = "Grid & Guide Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Turn On Grid", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Horizontal Line Spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Horizontal line spacing", color = Color.White, fontSize = 13.sp)
                    Text("${hSpacing.toInt()} px", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = hSpacing,
                    onValueChange = { hSpacing = it },
                    valueRange = 10f..120f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vertical Line Spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vertical line spacing", color = Color.White, fontSize = 13.sp)
                    Text("${vSpacing.toInt()} px", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = vSpacing,
                    onValueChange = { vSpacing = it },
                    valueRange = 10f..120f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Line Opacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Line opacity", color = Color.White, fontSize = 13.sp)
                    Text("${(opacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Color Swatches
                Text("Grid Color", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        0xFF888888.toInt(),
                        0xFFFFFFFF.toInt(),
                        0xFF00E5FF.toInt(),
                        0xFFFF5252.toInt(),
                        0xFFFFD700.toInt()
                    ).forEach { col ->
                        val isSelected = color == col
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.DarkGray,
                                    shape = CircleShape
                                )
                                .clickable { color = col }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Isometric / Perspective Guide Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Isometric 3D Grid Guide", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Diagonal perspective lines for 3D art", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isIsometric,
                        onCheckedChange = { isIsometric = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        onSaveConfig(
                            GridConfig(
                                enabled = enabled,
                                hSpacing = hSpacing.toInt(),
                                vSpacing = vSpacing.toInt(),
                                opacity = opacity,
                                color = color,
                                isIsometric = isIsometric
                            )
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
