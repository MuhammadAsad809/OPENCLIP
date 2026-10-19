package com.openclip.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.openclip.studio.core.model.BrushConfig
import com.openclip.studio.core.model.MirrorMode
import com.openclip.studio.core.model.RulerType
import com.openclip.studio.ui.theme.*

/**
 * Ruler & Mirror Symmetry Tool (Scale Icon)
 * Reference FlipaClip 5.0.2:
 * - Line, Circle, Box Ruler with Lock Ruler toggle
 * - Mirror Tool: Vertical, Horizontal, Quad symmetry drawing
 */
@Composable
fun RulerMirrorDialog(
    config: BrushConfig,
    onDismiss: () -> Unit,
    onUpdateRulerType: (RulerType) -> Unit,
    onUpdateRulerLock: (Boolean) -> Unit,
    onUpdateMirrorMode: (MirrorMode) -> Unit
) {
    var selectedRuler by remember { mutableStateOf(config.rulerType) }
    var rulerLocked by remember { mutableStateOf(config.rulerLockedToCanvas) }
    var selectedMirror by remember { mutableStateOf(config.mirrorMode) }

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
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ruler & Mirror Symmetry",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Ruler Types
                Text("RULER GUIDE", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val rulers = listOf(
                    Pair("Off", RulerType.NONE),
                    Pair("Straight Line", RulerType.LINE),
                    Pair("Circle / Ellipse", RulerType.CIRCLE),
                    Pair("Box / Rectangle", RulerType.RECTANGLE)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rulers.forEach { (label, type) ->
                        val isSelected = selectedRuler == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color(0xFF222226))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AccentPrimary else Color(0xFF383840),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedRuler = type
                                    onUpdateRulerType(type)
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Lock Ruler Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222226))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock Ruler to Canvas", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (rulerLocked) "Ruler pans and zooms with canvas" else "Ruler is fixed to device screen",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = rulerLocked,
                        onCheckedChange = {
                            rulerLocked = it
                            onUpdateRulerLock(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 2: Mirror Symmetry Tool
                Text("MIRROR SYMMETRY (CHARACTER & FACE DRAWING)", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val mirrors = listOf(
                    Pair("Off (Single Stroke)", MirrorMode.NONE),
                    Pair("Vertical Mirror (Face / Body Symmetry)", MirrorMode.VERTICAL),
                    Pair("Horizontal Mirror (Water / Floor Reflection)", MirrorMode.HORIZONTAL),
                    Pair("Quad Mirror (4-Way Kaleidoscope)", MirrorMode.QUAD)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mirrors.forEach { (label, mode) ->
                        val isSelected = selectedMirror == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color(0xFF222226))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AccentPrimary else Color(0xFF383840),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedMirror = mode
                                    onUpdateMirrorMode(mode)
                                }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply & Close", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
