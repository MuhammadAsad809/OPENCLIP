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
import com.openclip.studio.core.model.OnionConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionSettingsDialog(
    config: OnionConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (OnionConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var framesBefore by remember { mutableStateOf(config.framesBefore.toFloat()) }
    var framesAfter by remember { mutableStateOf(config.framesAfter.toFloat()) }
    var startOpacity by remember { mutableStateOf(config.startOpacity) }
    var endOpacity by remember { mutableStateOf(config.endOpacity) }
    var coloredOnion by remember { mutableStateOf(config.coloredOnion) }
    var prevColor by remember { mutableStateOf(config.prevColor) }
    var nextColor by remember { mutableStateOf(config.nextColor) }
    var loop by remember { mutableStateOf(config.loop) }

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
                        text = "Onion Skin Settings",
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
                    Text("Turn On Onion Skin", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

                // Frames Before
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Frames Before", color = Color.White, fontSize = 13.sp)
                    Text("${framesBefore.toInt()}", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = framesBefore,
                    onValueChange = { framesBefore = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5252), activeTrackColor = Color(0xFFFF5252))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Frames After
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Frames After", color = Color.White, fontSize = 13.sp)
                    Text("${framesAfter.toInt()}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = framesAfter,
                    onValueChange = { framesAfter = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF4CAF50), activeTrackColor = Color(0xFF4CAF50))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Color Tinting Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Colored Onion Skin", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Red for past frames, Green for next", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = coloredOnion,
                        onCheckedChange = { coloredOnion = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start Opacity", color = Color.White, fontSize = 13.sp)
                    Text("${(startOpacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = startOpacity,
                    onValueChange = { startOpacity = it },
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("End Opacity (Fade falloff)", color = Color.White, fontSize = 13.sp)
                    Text("${(endOpacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = endOpacity,
                    onValueChange = { endOpacity = it },
                    valueRange = 0.05f..0.5f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Loop Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Loop Onion Skin", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Wraps preview across first and last frames", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = loop,
                        onCheckedChange = { loop = it },
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
                            OnionConfig(
                                enabled = enabled,
                                framesBefore = framesBefore.toInt(),
                                framesAfter = framesAfter.toInt(),
                                startOpacity = startOpacity,
                                endOpacity = endOpacity,
                                coloredOnion = coloredOnion,
                                prevColor = prevColor,
                                nextColor = nextColor,
                                loop = loop
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
