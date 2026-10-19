package com.openclip.studio.ui.editor

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openclip.studio.ui.theme.*
import kotlin.math.*

/**
 * Professional FlipaClip 5.0.2 Color Panel
 * Tabs:
 * 1. Wheel (Interactive HSV 360° Color Disc)
 * 2. Classic (Presets & Standard Palettes)
 * 3. Harmony (Complementary, Analogous, Triadic, Split-Complementary)
 * 4. Values (RGB 0-255, Opacity, Hex Input)
 * 5. Custom (User Saved Swatches)
 */
@Composable
fun ProColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Wheel, 1: Classic, 2: Harmony, 3: Values, 4: Custom

    // HSV representation
    val initialHsv = FloatArray(3).apply {
        android.graphics.Color.colorToHSV(initialColor, this)
    }
    var hue by remember { mutableStateOf(initialHsv[0]) } // 0..360
    var saturation by remember { mutableStateOf(initialHsv[1]) } // 0..1
    var value by remember { mutableStateOf(initialHsv[2]) } // 0..1
    var alpha by remember { mutableStateOf(android.graphics.Color.alpha(initialColor) / 255f) }

    fun updateColorFromHsv(h: Float, s: Float, v: Float, a: Float = alpha) {
        hue = h.coerceIn(0f, 360f)
        saturation = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        alpha = a.coerceIn(0f, 1f)
        val rgb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, saturation, value))
        selectedColor = rgb
    }

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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Preview & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Preview Swatches (Initial vs Selected)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(initialColor))
                                .border(1.dp, Color(0xFF55555C), RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("→", color = TextSecondary, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(selectedColor))
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        )
                    }

                    // Hex code display
                    Text(
                        text = String.format("#%08X", selectedColor),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // OK Confirm Button
                    Button(
                        onClick = {
                            onColorSelected(selectedColor)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                val tabTitles = listOf("Wheel", "Classic", "Harmony", "Values", "Custom")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = AccentPrimary,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) AccentPrimary else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> ColorWheelTab(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onHsvChanged = { h, s, v ->
                            updateColorFromHsv(h, s, v)
                        }
                    )
                    1 -> ClassicColorsTab(
                        selectedColor = selectedColor,
                        onColorSelected = { color ->
                            selectedColor = color
                            android.graphics.Color.colorToHSV(color, initialHsv)
                            hue = initialHsv[0]
                            saturation = initialHsv[1]
                            value = initialHsv[2]
                            alpha = android.graphics.Color.alpha(color) / 255f
                        }
                    )
                    2 -> ColorHarmonyTab(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        alpha = alpha,
                        onColorSelected = { color ->
                            selectedColor = color
                            android.graphics.Color.colorToHSV(color, initialHsv)
                            hue = initialHsv[0]
                            saturation = initialHsv[1]
                            value = initialHsv[2]
                        }
                    )
                    3 -> ValuesTab(
                        color = selectedColor,
                        alpha = alpha,
                        onRgbChanged = { r, g, b, a ->
                            selectedColor = android.graphics.Color.argb((a * 255).toInt(), r, g, b)
                            android.graphics.Color.colorToHSV(selectedColor, initialHsv)
                            hue = initialHsv[0]
                            saturation = initialHsv[1]
                            value = initialHsv[2]
                            alpha = a
                        }
                    )
                    4 -> CustomPaletteTab(
                        context = context,
                        currentColor = selectedColor,
                        onColorSelected = { color ->
                            selectedColor = color
                            android.graphics.Color.colorToHSV(color, initialHsv)
                            hue = initialHsv[0]
                            saturation = initialHsv[1]
                            value = initialHsv[2]
                            alpha = android.graphics.Color.alpha(color) / 255f
                        }
                    )
                }
            }
        }
    }
}

/**
 * 1. Interactive 360° Color Wheel
 */
@Composable
fun ColorWheelTab(
    hue: Float,
    saturation: Float,
    value: Float,
    onHsvChanged: (Float, Float, Float) -> Unit
) {
    var curVal by remember(value) { mutableStateOf(value) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val wheelSize = 220.dp
        Box(
            modifier = Modifier
                .size(wheelSize)
                .pointerInput(Unit) {
                    fun handlePointer(pos: Offset, sizePx: Float) {
                        val center = Offset(sizePx / 2f, sizePx / 2f)
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val radius = sizePx / 2f
                        val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f

                        val sat = (dist / radius).coerceIn(0f, 1f)
                        onHsvChanged(angle, sat, curVal)
                    }

                    detectDragGestures { change, _ ->
                        change.consume()
                        handlePointer(change.position, size.width.toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val sizePx = size.width.toFloat()
                        val center = Offset(sizePx / 2f, sizePx / 2f)
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val radius = sizePx / 2f
                        val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f

                        val sat = (dist / radius).coerceIn(0f, 1f)
                        onHsvChanged(angle, sat, curVal)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Draw Hue spectrum sweep gradient
                val colors = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                    Color.Blue, Color.Magenta, Color.Red
                )
                drawCircle(
                    brush = Brush.sweepGradient(colors, center),
                    radius = radius
                )

                // White radial gradient to simulate saturation falloff towards center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 1f - (1f - curVal) * 0.5f), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )

                // Black radial gradient for value falloff
                if (curVal < 1f) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 1f - curVal),
                        radius = radius
                    )
                }

                // Selected thumb indicator
                val angleRad = Math.toRadians(hue.toDouble())
                val thumbDist = saturation * radius
                val thumbX = center.x + (thumbDist * cos(angleRad)).toFloat()
                val thumbY = center.y + (thumbDist * sin(angleRad)).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.Black,
                    radius = 9.dp.toPx(),
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Value / Brightness Slider
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Brightness", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(70.dp))
            Slider(
                value = curVal,
                onValueChange = {
                    curVal = it
                    onHsvChanged(hue, saturation, it)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
            Text("${(curVal * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
        }
    }
}

/**
 * 2. Classic Colors & Presets
 */
@Composable
fun ClassicColorsTab(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val classicPalettes = listOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF8E8E93.toInt(), 0xFF636366.toInt(),
        0xFFFF3B30.toInt(), 0xFFFF2D55.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(),
        0xFF34C759.toInt(), 0xFF28CD41.toInt(), 0xFF00C7BE.toInt(), 0xFF30B0C7.toInt(),
        0xFF32ADE6.toInt(), 0xFF007AFF.toInt(), 0xFF5856D6.toInt(), 0xFFAF52DE.toInt(),
        0xFFA2845E.toInt(), 0xFF6D4C41.toInt(), 0xFF3E2723.toInt(), 0xFF212121.toInt(),
        0xFFFF8A80.toInt(), 0xFFFF80AB.toInt(), 0xFFFFD180.toInt(), 0xFFFFFF8D.toInt(),
        0xFFCCFF90.toInt(), 0xFFA7FFEB.toInt(), 0xFF80D8FF.toInt(), 0xFF82B1FF.toInt(),
        0xFFB388FF.toInt(), 0xFFEA80FC.toInt(), 0xFFBCAAA4.toInt(), 0xFFECEFF1.toInt()
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxWidth().height(230.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(classicPalettes) { color ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (color == selectedColor) 3.dp else 1.dp,
                        color = if (color == selectedColor) AccentPrimary else Color(0xFF55555C),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

/**
 * 3. Color Harmony (Complementary, Analogous, Triadic, Split-Complementary)
 */
@Composable
fun ColorHarmonyTab(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Float,
    onColorSelected: (Int) -> Unit
) {
    fun colorFromHueOffset(offsetDegrees: Float): Int {
        val h = (hue + offsetDegrees + 360f) % 360f
        return android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(h, saturation, value))
    }

    val complementary = listOf(
        Pair("Base", colorFromHueOffset(0f)),
        Pair("Complementary", colorFromHueOffset(180f))
    )

    val analogous = listOf(
        Pair("-30°", colorFromHueOffset(-30f)),
        Pair("Base", colorFromHueOffset(0f)),
        Pair("+30°", colorFromHueOffset(30f))
    )

    val triadic = listOf(
        Pair("Base", colorFromHueOffset(0f)),
        Pair("+120°", colorFromHueOffset(120f)),
        Pair("+240°", colorFromHueOffset(240f))
    )

    val splitComp = listOf(
        Pair("Base", colorFromHueOffset(0f)),
        Pair("+150°", colorFromHueOffset(150f)),
        Pair("+210°", colorFromHueOffset(210f))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HarmonyRow("Complementary (Contrast)", complementary, onColorSelected)
        HarmonyRow("Analogous (Natural Flow)", analogous, onColorSelected)
        HarmonyRow("Triadic (Vibrant Balance)", triadic, onColorSelected)
        HarmonyRow("Split-Complementary", splitComp, onColorSelected)
    }
}

@Composable
fun HarmonyRow(
    title: String,
    colors: List<Pair<String, Int>>,
    onColorSelected: (Int) -> Unit
) {
    Column {
        Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { (label, colorInt) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(colorInt))
                        .border(1.dp, Color(0xFF666670), RoundedCornerShape(8.dp))
                        .clickable { onColorSelected(colorInt) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val textColor = if (android.graphics.Color.luminance(colorInt) > 0.5f) Color.Black else Color.White
                    Text(label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 4. Values (RGB 0-255, Opacity, Hex Input)
 */
@Composable
fun ValuesTab(
    color: Int,
    alpha: Float,
    onRgbChanged: (Int, Int, Int, Float) -> Unit
) {
    var r by remember(color) { mutableStateOf(android.graphics.Color.red(color)) }
    var g by remember(color) { mutableStateOf(android.graphics.Color.green(color)) }
    var b by remember(color) { mutableStateOf(android.graphics.Color.blue(color)) }
    var a by remember(alpha) { mutableStateOf(alpha) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Red Slider
        ValueSlider("R", r, 255, Color(0xFFFF453A)) { newR ->
            r = newR
            onRgbChanged(r, g, b, a)
        }

        // Green Slider
        ValueSlider("G", g, 255, Color(0xFF32D74B)) { newG ->
            g = newG
            onRgbChanged(r, g, b, a)
        }

        // Blue Slider
        ValueSlider("B", b, 255, Color(0xFF0A84FF)) { newB ->
            b = newB
            onRgbChanged(r, g, b, a)
        }

        // Opacity Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Alpha", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(36.dp))
            Slider(
                value = a,
                onValueChange = {
                    a = it
                    onRgbChanged(r, g, b, a)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )
            Text("${(a * 100).toInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(42.dp))
        }
    }
}

@Composable
fun ValueSlider(
    label: String,
    value: Int,
    max: Int,
    trackColor: Color,
    onChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = trackColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChanged(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor)
        )
        Text("$value", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(42.dp))
    }
}

/**
 * 5. Custom Palette Tab (User Swatches)
 */
@Composable
fun CustomPaletteTab(
    context: Context,
    currentColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val prefs = remember { context.getSharedPreferences("openclip_custom_palettes", Context.MODE_PRIVATE) }
    var swatches by remember {
        val saved = prefs.getString("user_swatches", null)
        val list = if (saved.isNullOrEmpty()) {
            mutableStateListOf(
                0xFF2A2A2E.toInt(), 0xFFE53935.toInt(), 0xFF8E24AA.toInt(),
                0xFF1E88E5.toInt(), 0xFF00897B.toInt(), 0xFF7CB342.toInt(),
                0xFFFDD835.toInt(), 0xFFFB8C00.toInt()
            )
        } else {
            mutableStateListOf<Int>().apply {
                saved.split(",").mapNotNull { it.toIntOrNull() }.forEach { add(it) }
            }
        }
        mutableStateOf(list)
    }

    fun saveSwatches() {
        prefs.edit().putString("user_swatches", swatches.joinToString(",")).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Swatches (${swatches.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

            Button(
                onClick = {
                    if (!swatches.contains(currentColor)) {
                        swatches.add(currentColor)
                        saveSwatches()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Current", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(swatches) { color ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(color))
                        .border(1.5.dp, Color(0xFF666670), RoundedCornerShape(12.dp))
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}
