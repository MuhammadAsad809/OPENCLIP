package com.openclip.studio.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportVideoDialog(
    canvasWidth: Int,
    canvasHeight: Int,
    onDismiss: () -> Unit,
    onFramesExtracted: (List<Bitmap>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var frameStep by remember { mutableStateOf(2f) } // Extract every 2nd frame by default
    var maxFramesToExtract by remember { mutableStateOf(24f) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableStateOf(0f) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Video / Rotoscoping",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text("Extract video frames to trace over", color = Color.Gray, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Select Video Button
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A3C), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedVideoUri != null) "Video Selected (Tap to Change)" else "Select Video from Device",
                        fontSize = 13.sp
                    )
                }

                if (selectedVideoUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frame Sampling Rate", color = Color.White, fontSize = 13.sp)
                        Text("1 out of ${frameStep.toInt()} frames", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = frameStep,
                        onValueChange = { frameStep = it },
                        valueRange = 1f..6f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max Frames Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Maximum Frames to Import", color = Color.White, fontSize = 13.sp)
                        Text("${maxFramesToExtract.toInt()} frames", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = maxFramesToExtract,
                        onValueChange = { maxFramesToExtract = it },
                        valueRange = 6f..60f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                    )
                }

                if (isExtracting) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { extractProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extracting frames… ${(extractProgress * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val uri = selectedVideoUri ?: return@Button
                        isExtracting = true
                        scope.launch {
                            val frames = extractVideoFrames(
                                context = context,
                                uri = uri,
                                step = frameStep.toInt(),
                                maxCount = maxFramesToExtract.toInt(),
                                targetWidth = canvasWidth,
                                targetHeight = canvasHeight,
                                onProgress = { extractProgress = it }
                            )
                            isExtracting = false
                            if (frames.isNotEmpty()) {
                                onFramesExtracted(frames)
                                onDismiss()
                            }
                        }
                    },
                    enabled = selectedVideoUri != null && !isExtracting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Import Frames into Project", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

private suspend fun extractVideoFrames(
    context: Context,
    uri: Uri,
    step: Int,
    maxCount: Int,
    targetWidth: Int,
    targetHeight: Int,
    onProgress: (Float) -> Unit
): List<Bitmap> = withContext(Dispatchers.IO) {
    val results = mutableListOf<Bitmap>()
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 1000L
        val intervalMs = (1000L / 12L) * step.coerceAtLeast(1)

        var timeUs = 0L
        var count = 0

        while (timeUs / 1000L < durationMs && count < maxCount) {
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                val scaled = Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
                results.add(scaled)
                count++
            }
            timeUs += (intervalMs * 1000L)
            onProgress(count.toFloat() / maxCount.toFloat())
        }
    } catch (_: Exception) {
    } finally {
        retriever.release()
    }
    results
}
