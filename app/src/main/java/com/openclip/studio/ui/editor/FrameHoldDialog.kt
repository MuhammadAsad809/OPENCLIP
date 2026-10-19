package com.openclip.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.openclip.studio.ui.theme.*

/**
 * Frame Hold / Exposure Duration Dialog
 * Allows extending frame timing (drawing remains visible for N frames)
 * without duplicating files or consuming memory.
 */
@Composable
fun FrameHoldDialog(
    frameIndex: Int,
    currentHold: Int,
    onDismiss: () -> Unit,
    onSetHold: (Int) -> Unit
) {
    var holdDuration by remember { mutableStateOf(currentHold) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(4.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Frame ${frameIndex + 1} Exposure",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hold duration in animation timeline",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Presets
                val presets = listOf(1, 2, 3, 4, 6, 8, 12)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { d ->
                        val isSel = holdDuration == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) AccentPrimary else Color(0xFF222226))
                                .border(1.dp, if (isSel) AccentPrimary else Color(0xFF383840), RoundedCornerShape(10.dp))
                                .clickable { holdDuration = d },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${d}x",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duration:", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = holdDuration.toFloat(),
                        onValueChange = { holdDuration = it.toInt() },
                        valueRange = 1f..24f,
                        steps = 22,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                    Text("$holdDuration frames", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.dp))
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onSetHold(holdDuration)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set Exposure (${holdDuration}x)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
