package com.openclip.studio.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openclip.studio.R
import com.openclip.studio.ui.theme.AccentPrimary
import com.openclip.studio.ui.theme.DarkBorder
import com.openclip.studio.ui.theme.DarkCard
import com.openclip.studio.ui.theme.DarkSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class CutoutMode {
    COLOR_KEY,     // Auto chroma key (removes sampled background color)
    PAPER_LINEART, // Preserves dark pencil/ink lines, extracts paper transparency
    TRANSPARENCY   // Preserves existing alpha
}

/**
 * FlipaClip 5.0.2 Magic Cut Feature
 * Removes backgrounds, extracts line art, and creates transparent animation stickers/props.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicCutDialog(
    canvasWidth: Int,
    canvasHeight: Int,
    onDismiss: () -> Unit,
    onCutoutReady: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var cutoutMode by remember { mutableStateOf(CutoutMode.COLOR_KEY) }
    var tolerance by remember { mutableStateOf(35f) }
    var keyColor by remember { mutableStateOf(AndroidColor.WHITE) }
    var invertSelection by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        // Scale to reasonable editing size (max 1024)
                        val maxDim = 1024
                        val scale = if (bmp.width > maxDim || bmp.height > maxDim) {
                            maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                        } else 1f
                        val scaled = Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt().coerceAtLeast(1),
                            (bmp.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                        sourceBitmap = scaled
                        // Sample top-left corner as default key color
                        keyColor = scaled.getPixel(0, 0)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Process cutout in background whenever settings change
    LaunchedEffect(sourceBitmap, cutoutMode, tolerance, keyColor, invertSelection) {
        val src = sourceBitmap ?: return@LaunchedEffect
        isProcessing = true
        withContext(Dispatchers.Default) {
            val result = processMagicCut(src, cutoutMode, tolerance.toInt(), keyColor, invertSelection)
            withContext(Dispatchers.Main) {
                processedBitmap = result
                isProcessing = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings_magic_cut),
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Magic Cut",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% Free Smart Background Eraser",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Source Image Chooser / Preview Area
                if (sourceBitmap == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap to Select Image from Gallery",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PNG, JPG, WebP supported",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Preview with Checkerboard Background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2B2B36))
                            .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayBmp = processedBitmap ?: sourceBitmap
                        if (displayBmp != null) {
                            Image(
                                bitmap = displayBmp.asImageBitmap(),
                                contentDescription = "Cutout Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = AccentPrimary
                            )
                        }
                        // Change image button overlay
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f))
                        ) {
                            Text("Change Photo", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mode Selection Tabs
                    Text(
                        text = "Extraction Mode",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = cutoutMode == CutoutMode.COLOR_KEY,
                            onClick = { cutoutMode = CutoutMode.COLOR_KEY },
                            label = { Text("Chroma Key", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = cutoutMode == CutoutMode.PAPER_LINEART,
                            onClick = { cutoutMode = CutoutMode.PAPER_LINEART },
                            label = { Text("Line Art / Sketch", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tolerance Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (cutoutMode == CutoutMode.PAPER_LINEART) "Line Contrast" else "Cutout Tolerance",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${tolerance.toInt()}%",
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Slider(
                        value = tolerance,
                        onValueChange = { tolerance = it },
                        valueRange = 5f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPrimary,
                            activeTrackColor = AccentPrimary
                        )
                    )

                    // Invert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Invert Cutout Selection", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = invertSelection,
                            onCheckedChange = { invertSelection = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentPrimary,
                                checkedTrackColor = AccentPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action Button: Insert Cutout to Frame
                    Button(
                        onClick = {
                            val finalCutout = processedBitmap ?: sourceBitmap
                            if (finalCutout != null) {
                                onCutoutReady(finalCutout)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stamp Cutout to Active Frame",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pixel-level background removal and line art extraction.
 */
private fun processMagicCut(
    source: Bitmap,
    mode: CutoutMode,
    tolerance: Int,
    keyColor: Int,
    invert: Boolean
): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val keyR = AndroidColor.red(keyColor)
    val keyG = AndroidColor.green(keyColor)
    val keyB = AndroidColor.blue(keyColor)
    val tolDist = (tolerance * 2.55f).toInt()

    when (mode) {
        CutoutMode.COLOR_KEY -> {
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)
                val diff = (abs(r - keyR) + abs(g - keyG) + abs(b - keyB)) / 3

                val match = diff <= tolDist
                val shouldErase = if (invert) !match else match
                if (shouldErase) {
                    pixels[i] = AndroidColor.TRANSPARENT
                }
            }
        }
        CutoutMode.PAPER_LINEART -> {
            // For scanned line art / paper drawings:
            // Calculate luminance. Dark pixels stay opaque ink; light paper becomes transparent.
            val thresholdLum = (255 - tolerance * 2.0f).coerceIn(40f, 245f)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = AndroidColor.red(p)
                val g = AndroidColor.green(p)
                val b = AndroidColor.blue(p)
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                if (lum > thresholdLum) {
                    pixels[i] = AndroidColor.TRANSPARENT
                } else {
                    // Smooth alpha falloff for clean pencil/ink anti-aliasing
                    val alpha = ((thresholdLum - lum) / thresholdLum * 255f).toInt().coerceIn(0, 255)
                    pixels[i] = AndroidColor.argb(if (invert) 255 - alpha else alpha, r, g, b)
                }
            }
        }
        CutoutMode.TRANSPARENCY -> {
            // Keep existing alpha
        }
    }

    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, w, 0, 0, w, h)
    return output
}
