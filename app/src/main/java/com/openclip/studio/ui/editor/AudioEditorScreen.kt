package com.openclip.studio.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclip.studio.R
import com.openclip.studio.core.model.AudioClip
import com.openclip.studio.core.model.AudioTrack
import com.openclip.studio.core.model.Project
import com.openclip.studio.data.ProjectRepository
import com.openclip.studio.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Classic FlipaClip-Style Multi-Track Audio Studio & Editor
 * References:
 * - stage_audio_editor.xml
 * - include_audio_editor_top_toolbar.xml
 * - include_audio_editor_mid_toolbar.xml
 * - include_audio_editor_multitrack.xml
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(
    project: Project,
    currentFrameIndex: Int,
    repository: ProjectRepository,
    onDismiss: () -> Unit,
    onFrameChanged: (Int) -> Unit,
    onProjectUpdated: (Project) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeProject by remember { mutableStateOf(project) }
    var scrubberFrame by remember { mutableStateOf(currentFrameIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var selectedTrackIdForAdd by remember { mutableStateOf(0) }
    var showAddClipOptionsSheet by remember { mutableStateOf(false) }

    // Media players for active audio tracks
    val activePlayers = remember { mutableStateMapOf<String, MediaPlayer>() }

    // Audio file picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}.mp3")
                        FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
                        
                        // Estimate duration
                        val mp = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                        }
                        val durationMs = mp.duration
                        mp.release()
                        val fps = activeProject.fps.coerceIn(1, 60)
                        val durationFrames = ((durationMs / 1000f) * fps).toInt().coerceAtLeast(1)

                        val updated = repository.addAudioClip(
                            projectId = activeProject.id,
                            trackId = selectedTrackIdForAdd,
                            name = uri.lastPathSegment?.substringAfterLast("/")?.take(20) ?: "Audio Clip",
                            srcFile = tempFile,
                            startFrame = scrubberFrame,
                            durationFrames = durationFrames
                        )
                        if (updated != null) {
                            activeProject = updated
                            onProjectUpdated(updated)
                            Toast.makeText(context, "Audio clip added to Track ${selectedTrackIdForAdd + 1}", Toast.LENGTH_SHORT).show()
                        }
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import audio: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Playback loop synchronized with frames
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val fps = activeProject.fps.coerceIn(1, 60)
            val frameDelay = (1000L / fps)

            // Start playing audio clips that start at current frame
            for (track in activeProject.audioTracks) {
                if (track.muted) continue
                for (clip in track.clips) {
                    if (clip.muted) continue
                    if (clip.startFrame == scrubberFrame) {
                        try {
                            val player = MediaPlayer().apply {
                                setDataSource(clip.filePath)
                                setVolume(track.volume * clip.volume, track.volume * clip.volume)
                                prepare()
                                start()
                            }
                            activePlayers[clip.id] = player
                        } catch (e: Exception) {}
                    }
                }
            }

            while (isPlaying) {
                delay(frameDelay)
                val next = scrubberFrame + 1
                if (next < activeProject.frameCount) {
                    scrubberFrame = next
                    onFrameChanged(next)

                    // Trigger any clip starting at this frame
                    for (track in activeProject.audioTracks) {
                        if (track.muted) continue
                        for (clip in track.clips) {
                            if (clip.muted) continue
                            if (clip.startFrame == next) {
                                try {
                                    val player = MediaPlayer().apply {
                                        setDataSource(clip.filePath)
                                        setVolume(track.volume * clip.volume, track.volume * clip.volume)
                                        prepare()
                                        start()
                                    }
                                    activePlayers[clip.id] = player
                                } catch (e: Exception) {}
                            }
                        }
                    }
                } else {
                    // Loop back to start
                    scrubberFrame = 0
                    onFrameChanged(0)
                    activePlayers.values.forEach { try { it.stop(); it.release() } catch (e: Exception) {} }
                    activePlayers.clear()
                }
            }
        } else {
            activePlayers.values.forEach { try { it.stop(); it.release() } catch (e: Exception) {} }
            activePlayers.clear()
        }
    }

    // Dispose players on exit
    DisposableEffect(Unit) {
        onDispose {
            activePlayers.values.forEach { try { it.stop(); it.release() } catch (e: Exception) {} }
            activePlayers.clear()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP TOOLBAR
                Surface(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    color = DarkCapsule
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stage_close),
                                contentDescription = "Close Audio Studio",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text("Audio Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${activeProject.title} • ${activeProject.fps} FPS", color = AccentPrimary, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Share
                        IconButton(
                            onClick = { Toast.makeText(context, "Export audio project", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stage_share),
                                contentDescription = "Share",
                                tint = AccentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 2. 16:9 CANVAS PREVIEW AREA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF141416)),
                    contentAlignment = Alignment.Center
                ) {
                    val frameBmp = remember(activeProject.id, scrubberFrame) {
                        repository.getCompositedFrame(activeProject, scrubberFrame)
                    }

                    Image(
                        bitmap = frameBmp.asImageBitmap(),
                        contentDescription = "Frame $scrubberFrame Preview",
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(activeProject.canvasWidth.toFloat() / activeProject.canvasHeight.toFloat())
                            .border(1.dp, Color(0xFF383840)),
                        contentScale = ContentScale.Fit
                    )

                    // Timecode badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC1E1E22)
                    ) {
                        val totalSecs = scrubberFrame / activeProject.fps.toFloat()
                        val mins = (totalSecs / 60).toInt()
                        val secs = (totalSecs % 60).toInt()
                        val frames = scrubberFrame % activeProject.fps
                        Text(
                            text = String.format("%02d:%02d:%02d (Frame %d / %d)", mins, secs, frames, scrubberFrame + 1, activeProject.frameCount),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                // 3. MIDDLE PLAYBACK & CONTROL BAR
                Surface(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    color = Color(0xFF222226)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Master Audio Toggle
                        IconButton(
                            onClick = { Toast.makeText(context, "Master Audio Volume", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_audio_on),
                                contentDescription = "Master Audio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Rewind to Frame 0
                        IconButton(
                            onClick = {
                                isPlaying = false
                                scrubberFrame = 0
                                onFrameChanged(0)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stage_rewind),
                                contentDescription = "Rewind",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Play / Pause Toggle Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentPrimary)
                                .clickable { isPlaying = !isPlaying },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stage_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Fast Forward to End
                        IconButton(
                            onClick = {
                                isPlaying = false
                                scrubberFrame = activeProject.frameCount - 1
                                onFrameChanged(activeProject.frameCount - 1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stage_fastforward),
                                contentDescription = "Fast Forward",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Add Track / Clip Button
                        Button(
                            onClick = {
                                selectedTrackIdForAdd = 0
                                showAddClipOptionsSheet = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. MULTI-TRACK AUDIO TIMELINE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF1A1A1E))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Ruler Header
                        AudioTimelineRuler(
                            frameCount = activeProject.frameCount.coerceAtLeast(36),
                            fps = activeProject.fps,
                            scrubberFrame = scrubberFrame
                        )

                        HorizontalDivider(color = Color(0xFF2E2E34), thickness = 1.dp)

                        // Multi-Track List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(activeProject.audioTracks, key = { it.id }) { track ->
                                AudioTrackRow(
                                    track = track,
                                    fps = activeProject.fps,
                                    maxFrames = activeProject.frameCount.coerceAtLeast(36),
                                    onAddClip = {
                                        selectedTrackIdForAdd = track.id
                                        showAddClipOptionsSheet = true
                                    },
                                    onDeleteClip = { clipId ->
                                        val updated = repository.deleteAudioClip(activeProject.id, track.id, clipId)
                                        if (updated != null) {
                                            activeProject = updated
                                            onProjectUpdated(updated)
                                        }
                                    }
                                )
                            }

                            item {
                                // Add Track Button
                                TextButton(
                                    onClick = {
                                        val newId = activeProject.audioTracks.size
                                        val newTrack = AudioTrack(id = newId, name = "Track ${newId + 1}")
                                        val updated = activeProject.copy(
                                            audioTracks = activeProject.audioTracks + newTrack,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        repository.saveProject(updated)
                                        activeProject = updated
                                        onProjectUpdated(updated)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add New Audio Track", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // 5. INTERACTIVE RED SCRUBBER NEEDLE
                    val totalFrames = activeProject.frameCount.coerceAtLeast(36)
                    val frameWidthPx = 40.dp
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = (80.dp + (frameWidthPx * scrubberFrame)))
                            .width(2.dp)
                            .background(AccentPrimary)
                    ) {
                        // Top marker triangle
                        Icon(
                            painter = painterResource(id = R.drawable.audio_playback_position_marker),
                            contentDescription = "Scrubber",
                            tint = AccentPrimary,
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }

    // ADD AUDIO OPTIONS SHEET (Import file or Record audio)
    if (showAddClipOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddClipOptionsSheet = false },
            containerColor = Color(0xFF26262A),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Add Audio to Track ${selectedTrackIdForAdd + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Import Audio File
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddClipOptionsSheet = false
                            audioPickerLauncher.launch("audio/*")
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF33333E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_stage_add_audio), contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Import Audio File", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Add MP3, WAV, or M4A music/sound from device", color = TextTertiary, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF33333C))

                // Option 2: Record with Microphone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAddClipOptionsSheet = false
                            showRecordDialog = true
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF33333E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_add_audio_recorder), contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Record Audio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Record voiceover or sound effects using device microphone", color = TextTertiary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // LIVE MICROPHONE RECORDER DIALOG
    if (showRecordDialog) {
        VoiceRecorderDialog(
            trackId = selectedTrackIdForAdd,
            startFrame = scrubberFrame,
            fps = activeProject.fps,
            onDismiss = { showRecordDialog = false },
            onSaveRecordedClip = { file, name, durationFrames ->
                showRecordDialog = false
                val updated = repository.addAudioClip(
                    projectId = activeProject.id,
                    trackId = selectedTrackIdForAdd,
                    name = name,
                    srcFile = file,
                    startFrame = scrubberFrame,
                    durationFrames = durationFrames
                )
                if (updated != null) {
                    activeProject = updated
                    onProjectUpdated(updated)
                    Toast.makeText(context, "Voice recording added to Track ${selectedTrackIdForAdd + 1}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Audio Timeline Ruler displaying Frame & Time ticks
 */
@Composable
fun AudioTimelineRuler(
    frameCount: Int,
    fps: Int,
    scrubberFrame: Int
) {
    val frameWidth = 40.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        color = Color(0xFF222226)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
                Text("Track", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            for (f in 0 until frameCount) {
                Box(
                    modifier = Modifier
                        .width(frameWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (f % fps == 0) {
                        Text(
                            text = "${f / fps}s",
                            color = if (f == scrubberFrame) AccentPrimary else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (f % 6 == 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(8.dp)
                                .background(Color(0xFF55555C))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Audio Track Row
 */
@Composable
fun AudioTrackRow(
    track: AudioTrack,
    fps: Int,
    maxFrames: Int,
    onAddClip: () -> Unit,
    onDeleteClip: (String) -> Unit
) {
    val frameWidth = 40.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF202024)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Header (Width 80dp)
        Surface(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight(),
            color = Color(0xFF28282E),
            border = BorderStroke(0.5.dp, Color(0xFF383840))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(track.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onAddClip,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Clip", tint = AccentPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Track Clips Canvas (Scrollable)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
        ) {
            // Background grid lines
            Row(modifier = Modifier.fillMaxHeight()) {
                for (f in 0 until maxFrames) {
                    Box(
                        modifier = Modifier
                            .width(frameWidth)
                            .fillMaxHeight()
                            .border(0.5.dp, Color(0xFF26262B))
                    )
                }
            }

            // Audio Clips
            for (clip in track.clips) {
                val clipStartOffset = frameWidth * clip.startFrame
                val clipWidth = frameWidth * clip.durationFrames.coerceAtLeast(2)

                Surface(
                    modifier = Modifier
                        .padding(start = clipStartOffset, top = 6.dp, bottom = 6.dp)
                        .width(clipWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color(0xFF2A2A38),
                    border = BorderStroke(1.dp, AccentPrimary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clip.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${clip.durationFrames}f (${String.format("%.1fs", clip.durationFrames / fps.toFloat())})",
                                color = TextTertiary,
                                fontSize = 9.sp
                            )
                        }

                        // Stylized Waveform Bars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            val barHeights = listOf(12, 24, 18, 30, 16, 26, 14, 20)
                            barHeights.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(h.dp)
                                        .background(AccentPrimary, RoundedCornerShape(1.dp))
                                )
                            }
                        }

                        IconButton(
                            onClick = { onDeleteClip(clip.id) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete Clip", tint = Color(0xFFFF453A), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Built-in Voice/Microphone Recorder Dialog
 */
@Composable
fun VoiceRecorderDialog(
    trackId: Int,
    startFrame: Int,
    fps: Int,
    onDismiss: () -> Unit,
    onSaveRecordedClip: (file: File, name: String, durationFrames: Int) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationMs by remember { mutableStateOf(0L) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // Pulsating animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(isRecording) {
        val startTime = System.currentTimeMillis()
        while (isRecording) {
            delay(100)
            recordingDurationMs = System.currentTimeMillis() - startTime
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (e: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Microphone Voice Recorder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pulsating Recording Button
                Box(
                    modifier = Modifier
                        .size((72 * if (isRecording) pulseScale else 1f).dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFFF3B30) else AccentPrimary)
                        .clickable {
                            if (!isRecording) {
                                try {
                                    val f = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
                                    outputFile = f
                                    val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        MediaRecorder(context)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        MediaRecorder()
                                    }
                                    r.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    r.setOutputFile(f.absolutePath)
                                    r.prepare()
                                    r.start()
                                    recorder = r
                                    isRecording = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                try {
                                    recorder?.stop()
                                    recorder?.release()
                                    recorder = null
                                    isRecording = false
                                } catch (e: Exception) {
                                    isRecording = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add_audio_recorder),
                        contentDescription = "Record",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val secs = (recordingDurationMs / 1000).toInt()
                val millis = (recordingDurationMs % 1000) / 100
                Text(
                    text = String.format("%02d:%02d.%d", secs / 60, secs % 60, millis),
                    color = if (isRecording) Color(0xFFFF3B30) else Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isRecording) "Recording... Tap button to stop" else "Tap red button to start recording",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            if (!isRecording && outputFile != null && outputFile!!.exists() && recordingDurationMs > 300) {
                Button(
                    onClick = {
                        val durationFrames = ((recordingDurationMs / 1000f) * fps).toInt().coerceAtLeast(1)
                        onSaveRecordedClip(outputFile!!, "Voice Recording", durationFrames)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Add to Track", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
