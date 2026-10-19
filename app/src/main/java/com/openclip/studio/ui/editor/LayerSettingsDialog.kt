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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openclip.studio.core.model.BlendMode
import com.openclip.studio.core.model.Layer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerSettingsDialog(
    layer: Layer,
    canMergeDown: Boolean = false,
    onDismiss: () -> Unit,
    onMergeDown: (() -> Unit)? = null,
    onSaveLayer: (Layer) -> Unit
) {
    var opacity by remember { mutableStateOf(layer.opacity) }
    var blendMode by remember { mutableStateOf(layer.blendMode) }
    var alphaLock by remember { mutableStateOf(layer.alphaLock) }
    var isClippingMask by remember { mutableStateOf(layer.isClippingMask) }
    
    // Glow FX
    var glowEnabled by remember { mutableStateOf(layer.glowEnabled) }
    var glowColor by remember { mutableStateOf(layer.glowColor) }
    var glowRadius by remember { mutableStateOf(layer.glowRadius) }
    var glowAlpha by remember { mutableStateOf(layer.glowAlpha) }

    // Pixelator FX
    var pixelatorEnabled by remember { mutableStateOf(layer.pixelatorEnabled) }
    var pixelSize by remember { mutableStateOf(layer.pixelSize.toFloat()) }

    // Drop Shadow FX
    var shadowEnabled by remember { mutableStateOf(layer.shadowEnabled) }
    var shadowRadius by remember { mutableStateOf(layer.shadowRadius) }

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
                    Column {
                        Text(
                            text = "Layer Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(layer.name, color = Color(0xFF00E5FF), fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Layer Opacity", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${(opacity * 100).toInt()}%", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Blending Mode
                Text("Blending Mode (Beta)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                val modes = listOf(
                    BlendMode.NORMAL to "Normal",
                    BlendMode.MULTIPLY to "Multiply",
                    BlendMode.SCREEN to "Screen",
                    BlendMode.OVERLAY to "Overlay",
                    BlendMode.DARKEN to "Darken",
                    BlendMode.LIGHTEN to "Lighten",
                    BlendMode.COLOR_DODGE to "Color Dodge",
                    BlendMode.SOFT_LIGHT to "Soft Light",
                    BlendMode.DIFFERENCE to "Difference"
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { (mode, label) ->
                                val isSelected = blendMode == mode
                                Button(
                                    onClick = { blendMode = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2A2A3C),
                                        contentColor = if (isSelected) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alpha Lock Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alpha Lock", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Paints only on existing non-transparent pixels", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = alphaLock,
                        onCheckedChange = { alphaLock = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clipping Mask Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clipping Mask", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Clips this layer's strokes to the layer below it", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isClippingMask,
                        onCheckedChange = { isClippingMask = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Glow FX (Beta)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Glow Effect (Beta)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = glowEnabled,
                        onCheckedChange = { glowEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700), checkedTrackColor = Color(0xFFFFD700).copy(alpha = 0.5f))
                    )
                }

                if (glowEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Glow Size (${glowRadius.toInt()} px)", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = glowRadius,
                        onValueChange = { glowRadius = it },
                        valueRange = 2f..60f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD700), activeTrackColor = Color(0xFFFFD700))
                    )

                    Text("Glow Color", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFFFFFF00.toInt(), 0xFF00E5FF.toInt(), 0xFFFF0055.toInt(), 0xFFFFFFFF.toInt(), 0xFF00FF66.toInt()).forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(if (glowColor == col) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { glowColor = col }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pixel Effect (Pixelator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pixel Effect", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Retro 8-bit downsampled pixelation", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = pixelatorEnabled,
                        onCheckedChange = { pixelatorEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
                    )
                }

                if (pixelatorEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pixel Size (${pixelSize.toInt()} px)", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = pixelSize,
                        onValueChange = { pixelSize = it },
                        valueRange = 2f..32f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Drop Shadow FX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Drop Shadow", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Depth shadow behind layer line art", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = shadowEnabled,
                        onCheckedChange = { shadowEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (canMergeDown && onMergeDown != null) {
                    OutlinedButton(
                        onClick = {
                            onMergeDown()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF9F0A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9F0A))
                    ) {
                        Text("Merge Down with Layer Below", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        onSaveLayer(
                            layer.copy(
                                opacity = opacity,
                                blendMode = blendMode,
                                alphaLock = alphaLock,
                                isClippingMask = isClippingMask,
                                glowEnabled = glowEnabled,
                                glowColor = glowColor,
                                glowRadius = glowRadius,
                                glowAlpha = glowAlpha,
                                pixelatorEnabled = pixelatorEnabled,
                                pixelSize = pixelSize.toInt(),
                                shadowEnabled = shadowEnabled,
                                shadowRadius = shadowRadius
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
