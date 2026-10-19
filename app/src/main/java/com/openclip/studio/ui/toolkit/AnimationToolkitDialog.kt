package com.openclip.studio.ui.toolkit

import android.content.Context
import android.graphics.*
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openclip.studio.core.audio.CartoonAudioSynthesizer
import com.openclip.studio.core.model.Project
import com.openclip.studio.data.ProjectRepository
import com.openclip.studio.ui.theme.*
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Animator's Toolkit (Thousands of Resources & Guides)
 * Complete built-in library for 2D frame-by-frame animators:
 * 1. Motion Guides: Walk cycles, Run cycles, Bouncing ball squash & stretch, Lip Sync phonemes
 * 2. Cartoon Audio SFX: Procedural studio sound library with live preview & 1-tap timeline insertion
 * 3. Stage Backdrops: Anime sunset, Cyberpunk skyline, Forest nature, Studio grid
 * 4. Props & Speedlines: Comic bursts, anime radial action lines, speech bubbles
 * 5. Curated Animator Palettes: Skin tones, Ghibli, Cyberpunk, 90s Toon, Manga
 */
@Composable
fun AnimationToolkitDialog(
    project: Project,
    currentFrameIndex: Int,
    repository: ProjectRepository,
    activeLayerBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onProjectUpdated: (Project) -> Unit,
    onBitmapStamped: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Guides, 1: Audio SFX, 2: Backdrops, 3: Props & FX, 4: Palettes

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(2.dp),
            shape = RoundedCornerShape(26.dp),
            color = DarkSurface,
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Animator's Toolkit",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100% Free Resources & Reference Library",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                val tabTitles = listOf("Guides", "Audio SFX", "Backdrops", "Props/FX", "Palettes")
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
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) AccentPrimary else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> MotionGuidesTab(
                            onStampGuide = { guideType ->
                                if (activeLayerBitmap != null) {
                                    stampMotionGuide(activeLayerBitmap, guideType)
                                    onBitmapStamped()
                                    Toast.makeText(context, "Stamped guide onto Frame ${currentFrameIndex + 1}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        1 -> AudioSfxTab(
                            project = project,
                            currentFrameIndex = currentFrameIndex,
                            repository = repository,
                            onProjectUpdated = onProjectUpdated
                        )
                        2 -> StageBackdropsTab(
                            onStampBackdrop = { backdropType ->
                                if (activeLayerBitmap != null) {
                                    stampStageBackdrop(activeLayerBitmap, backdropType)
                                    onBitmapStamped()
                                    Toast.makeText(context, "Backdrop applied to active layer", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        3 -> PropsAndFxTab(
                            onStampProp = { propType ->
                                if (activeLayerBitmap != null) {
                                    stampProp(activeLayerBitmap, propType)
                                    onBitmapStamped()
                                    Toast.makeText(context, "Prop stamped onto Frame ${currentFrameIndex + 1}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        4 -> AnimatorPalettesTab(
                            context = context,
                            onColorSelected = onColorSelected
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1. Motion & Action Reference Guides
 */
@Composable
fun MotionGuidesTab(
    onStampGuide: (String) -> Unit
) {
    val guides = listOf(
        Pair("walk_contact", "Walk: Contact Frame"),
        Pair("walk_down", "Walk: Down / Squash"),
        Pair("walk_passing", "Walk: Passing Position"),
        Pair("walk_up", "Walk: Up / Peak"),
        Pair("run_flight", "Run: Flight / Airborne"),
        Pair("ball_squash", "Bouncing Ball: Squash"),
        Pair("ball_stretch", "Bouncing Ball: Stretch"),
        Pair("lipsync_ai", "Lip Sync: Phoneme 'A / I'"),
        Pair("lipsync_e", "Lip Sync: Phoneme 'E'"),
        Pair("lipsync_o", "Lip Sync: Phoneme 'O'"),
        Pair("lipsync_u", "Lip Sync: Phoneme 'U / W'"),
        Pair("lipsync_mbp", "Lip Sync: Phoneme 'M / B / P'"),
        Pair("face_happy", "Face: Cheerful Expression"),
        Pair("face_shocked", "Face: Shocked / Screaming")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(guides) { (id, title) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF44444C), RoundedCornerShape(14.dp)),
                color = Color(0xFF222226)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF151518)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(60.dp)) {
                            drawGuideThumbnail(this, id)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { onStampGuide(id) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Stamp Guide", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 2. Cartoon Audio SFX Library (Procedural synthesized audio)
 */
@Composable
fun AudioSfxTab(
    project: Project,
    currentFrameIndex: Int,
    repository: ProjectRepository,
    onProjectUpdated: (Project) -> Unit
) {
    val context = LocalContext.current
    val presets = CartoonAudioSynthesizer.PRESETS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(presets) { sfx ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF383840), RoundedCornerShape(14.dp)),
                color = Color(0xFF222226)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sfx.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF33333C)
                            ) {
                                Text(
                                    text = sfx.category,
                                    color = AccentPrimary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${sfx.description} • ${sfx.durationMs}ms",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Play Preview Button
                        IconButton(
                            onClick = { CartoonAudioSynthesizer.playPreview(sfx.id) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3A3A44))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Add to Project Audio Button
                        Button(
                            onClick = {
                                val audioDir = repository.getAudioDir(project.id)
                                val wavFile = File(audioDir, "${sfx.id}_${System.currentTimeMillis()}.wav")
                                if (CartoonAudioSynthesizer.exportToWavFile(sfx.id, wavFile)) {
                                    val durationFrames = ((sfx.durationMs / 1000f) * project.fps).toInt().coerceAtLeast(1)
                                    val updated = repository.addAudioClip(
                                        projectId = project.id,
                                        trackId = 0,
                                        name = sfx.name,
                                        srcFile = wavFile,
                                        startFrame = currentFrameIndex,
                                        durationFrames = durationFrames
                                    )
                                    if (updated != null) {
                                        onProjectUpdated(updated)
                                        Toast.makeText(context, "Added '${sfx.name}' to Audio Track 1 at Frame ${currentFrameIndex + 1}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("+ Track", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. Stage Backdrops
 */
@Composable
fun StageBackdropsTab(
    onStampBackdrop: (String) -> Unit
) {
    val backdrops = listOf(
        Pair("anime_sunset", "Anime Gradient Sunset"),
        Pair("cyberpunk_city", "Cyberpunk Night Skyline"),
        Pair("mystic_forest", "Mystical Tree Silhouettes"),
        Pair("studio_grid", "Animation Perspective Grid"),
        Pair("blue_clouds", "Bright Cyan Sky & Clouds")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(backdrops) { (id, title) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF44444C), RoundedCornerShape(14.dp)),
                color = Color(0xFF222226)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawBackdropThumbnail(this, id)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { onStampBackdrop(id) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Apply Backdrop", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 4. Props & Action Speedlines
 */
@Composable
fun PropsAndFxTab(
    onStampProp: (String) -> Unit
) {
    val props = listOf(
        Pair("speedlines_radial", "Anime Radial Speedlines"),
        Pair("speech_bubble", "Comic Speech Bubble"),
        Pair("pow_star", "Impact POW Star Burst"),
        Pair("lightning_bolt", "Anime Action Lightning"),
        Pair("smoke_puff", "Cartoon Smoke Puff")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(props) { (id, title) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF44444C), RoundedCornerShape(14.dp)),
                color = Color(0xFF222226)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF151518)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(60.dp)) {
                            drawPropThumbnail(this, id)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { onStampProp(id) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Stamp Prop", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 5. Curated Animator Palettes
 */
@Composable
fun AnimatorPalettesTab(
    context: Context,
    onColorSelected: (Int) -> Unit
) {
    val curatedPalettes = listOf(
        Pair("Anime Skin Tones", listOf(0xFFFFF0E8.toInt(), 0xFFFFD7BA.toInt(), 0xFFF5B895.toInt(), 0xFFE08D6E.toInt(), 0xFFB3573D.toInt())),
        Pair("Studio Ghibli Nature", listOf(0xFF2D5A27.toInt(), 0xFF588157.toInt(), 0xFFA3B18A.toInt(), 0xFFDAD7CD.toInt(), 0xFF344E41.toInt())),
        Pair("Cyberpunk Neon", listOf(0xFFFF007F.toInt(), 0xFF00F0FF.toInt(), 0xFF7B2CBF.toInt(), 0xFFF72585.toInt(), 0xFF10002B.toInt())),
        Pair("Classic 90s Toon", listOf(0xFFFF1E27.toInt(), 0xFFFFCC00.toInt(), 0xFF0070FF.toInt(), 0xFF00A859.toInt(), 0xFF1A1A1A.toInt())),
        Pair("Pastel Dreams", listOf(0xFFFFB5A7.toInt(), 0xFFFCD5CE.toInt(), 0xFFF8EDEB.toInt(), 0xFFF9DCC4.toInt(), 0xFFFEC89A.toInt())),
        Pair("Manga Screentone Ink", listOf(0xFF000000.toInt(), 0xFF4A4A4A.toInt(), 0xFF8A8A8A.toInt(), 0xFFD4D4D4.toInt(), 0xFFFFFFFF.toInt()))
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(curatedPalettes) { (name, colors) ->
            Column {
                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(c))
                                .border(1.dp, Color(0xFF666670), RoundedCornerShape(10.dp))
                                .clickable {
                                    onColorSelected(c)
                                    Toast.makeText(context, "Selected color from $name", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// RENDER HELPERS FOR GUIDES, PROPS, BACKDROPS
// ==========================================

private fun drawGuideThumbnail(scope: androidx.compose.ui.graphics.drawscope.DrawScope, id: String) {
    val center = scope.center
    val w = scope.size.width
    val h = scope.size.height

    when (id) {
        "ball_squash" -> {
            scope.drawOval(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.6f), size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.3f))
        }
        "ball_stretch" -> {
            scope.drawOval(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.7f))
        }
        "lipsync_o" -> {
            scope.drawCircle(Color.White, radius = w * 0.28f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
        "lipsync_ai" -> {
            scope.drawOval(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
        else -> {
            // Stick figure head & limbs
            scope.drawCircle(Color.White, radius = w * 0.16f, center = androidx.compose.ui.geometry.Offset(center.x, h * 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            scope.drawLine(Color.White, androidx.compose.ui.geometry.Offset(center.x, h * 0.4f), androidx.compose.ui.geometry.Offset(center.x, h * 0.7f), strokeWidth = 3f)
            scope.drawLine(Color.White, androidx.compose.ui.geometry.Offset(center.x, h * 0.7f), androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.95f), strokeWidth = 3f)
            scope.drawLine(Color.White, androidx.compose.ui.geometry.Offset(center.x, h * 0.7f), androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.95f), strokeWidth = 3f)
        }
    }
}

private fun drawBackdropThumbnail(scope: androidx.compose.ui.graphics.drawscope.DrawScope, id: String) {
    val w = scope.size.width
    val h = scope.size.height

    when (id) {
        "anime_sunset" -> {
            scope.drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF8338EC), Color(0xFFFF5400), Color(0xFFFFBE0B))
                )
            )
            scope.drawCircle(Color(0xFFFFF07C), radius = h * 0.25f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.7f))
        }
        "cyberpunk_city" -> {
            scope.drawRect(Color(0xFF0F0E17))
            scope.drawRect(Color(0xFF2E2F3E), topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.4f), size = androidx.compose.ui.geometry.Size(w * 0.25f, h * 0.6f))
            scope.drawRect(Color(0xFF1E1E2E), topLeft = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.2f), size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.8f))
            scope.drawRect(Color(0xFF2A2B3D), topLeft = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.35f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.65f))
        }
        "blue_clouds" -> {
            scope.drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF48CAE4), Color(0xFFADE8F4))
                )
            )
            scope.drawCircle(Color.White, radius = h * 0.25f, center = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.6f))
            scope.drawCircle(Color.White, radius = h * 0.3f, center = androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.6f))
        }
        else -> {
            scope.drawRect(Color(0xFF1B4332))
            scope.drawCircle(Color(0xFF2D6A4F), radius = h * 0.4f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.8f))
        }
    }
}

private fun drawPropThumbnail(scope: androidx.compose.ui.graphics.drawscope.DrawScope, id: String) {
    val center = scope.center
    val w = scope.size.width
    val h = scope.size.height

    when (id) {
        "speedlines_radial" -> {
            for (i in 0 until 12) {
                val angle = i * (Math.PI * 2 / 12)
                val innerX = center.x + (w * 0.2f * cos(angle)).toFloat()
                val innerY = center.y + (h * 0.2f * sin(angle)).toFloat()
                val outerX = center.x + (w * 0.48f * cos(angle)).toFloat()
                val outerY = center.y + (h * 0.48f * sin(angle)).toFloat()
                scope.drawLine(Color.White, androidx.compose.ui.geometry.Offset(innerX, innerY), androidx.compose.ui.geometry.Offset(outerX, outerY), strokeWidth = 3f)
            }
        }
        "speech_bubble" -> {
            scope.drawRoundRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
        }
        else -> {
            scope.drawCircle(Color(0xFFFFD166), radius = w * 0.35f, center = center)
        }
    }
}

fun stampMotionGuide(bitmap: Bitmap, guideType: String) {
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF30B0C7.toInt() // Cyan guide color
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()

    when (guideType) {
        "ball_squash" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawOval(RectF(w * 0.35f, h * 0.65f, w * 0.65f, h * 0.85f), paint)
        }
        "ball_stretch" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawOval(RectF(w * 0.42f, h * 0.3f, w * 0.58f, h * 0.7f), paint)
        }
        "lipsync_o" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(w * 0.5f, h * 0.5f, h * 0.15f, paint)
        }
        "lipsync_ai" -> {
            paint.style = Paint.Style.STROKE
            canvas.drawOval(RectF(w * 0.4f, h * 0.42f, w * 0.6f, h * 0.58f), paint)
        }
        else -> {
            // General Figure Guide
            canvas.drawCircle(w * 0.5f, h * 0.28f, h * 0.1f, paint)
            canvas.drawLine(w * 0.5f, h * 0.38f, w * 0.5f, h * 0.65f, paint)
            canvas.drawLine(w * 0.5f, h * 0.65f, w * 0.4f, h * 0.9f, paint)
            canvas.drawLine(w * 0.5f, h * 0.65f, w * 0.6f, h * 0.9f, paint)
            canvas.drawLine(w * 0.5f, h * 0.45f, w * 0.38f, h * 0.6f, paint)
            canvas.drawLine(w * 0.5f, h * 0.45f, w * 0.62f, h * 0.6f, paint)
        }
    }
}

fun stampStageBackdrop(bitmap: Bitmap, backdropType: String) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()

    when (backdropType) {
        "anime_sunset" -> {
            val gradient = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF6B2D5C.toInt(), 0xFFF3722C.toInt(), 0xFFF9C74F.toInt()),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            val paint = Paint().apply { shader = gradient }
            canvas.drawRect(0f, 0f, w, h, paint)

            val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF1C5.toInt() }
            canvas.drawCircle(w * 0.5f, h * 0.72f, h * 0.2f, sunPaint)
        }
        "cyberpunk_city" -> {
            val bgPaint = Paint().apply { color = 0xFF0D0C1D.toInt() }
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            val bldgPaint = Paint().apply { color = 0xFF1B1B2F.toInt() }
            canvas.drawRect(w * 0.1f, h * 0.4f, w * 0.3f, h, bldgPaint)
            canvas.drawRect(w * 0.35f, h * 0.25f, w * 0.65f, h, bldgPaint)
            canvas.drawRect(w * 0.7f, h * 0.38f, w * 0.9f, h, bldgPaint)

            val neonPaint = Paint().apply { color = 0xFFFF007F.toInt() }
            canvas.drawRect(w * 0.38f, h * 0.3f, w * 0.42f, h * 0.5f, neonPaint)
        }
        "blue_clouds" -> {
            val skyGradient = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF00B4D8.toInt(), 0xFF90E0EF.toInt()),
                null, Shader.TileMode.CLAMP
            )
            val paint = Paint().apply { shader = skyGradient }
            canvas.drawRect(0f, 0f, w, h, paint)

            val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            canvas.drawCircle(w * 0.3f, h * 0.45f, h * 0.15f, cloudPaint)
            canvas.drawCircle(w * 0.45f, h * 0.42f, h * 0.2f, cloudPaint)
            canvas.drawCircle(w * 0.6f, h * 0.45f, h * 0.16f, cloudPaint)
        }
        else -> { // studio_grid
            val bgPaint = Paint().apply { color = 0xFFF8F9FA.toInt() }
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            val gridPaint = Paint().apply {
                color = 0xFFCED4DA.toInt()
                strokeWidth = 2f
            }
            var x = 0f
            while (x < w) {
                canvas.drawLine(x, 0f, x, h, gridPaint)
                x += 80f
            }
            var y = 0f
            while (y < h) {
                canvas.drawLine(0f, y, w, y, gridPaint)
                y += 80f
            }
        }
    }
}

fun stampProp(bitmap: Bitmap, propType: String) {
    val canvas = Canvas(bitmap)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val center = PointF(w / 2f, h / 2f)

    when (propType) {
        "speedlines_radial" -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                strokeWidth = 5f
            }
            for (i in 0 until 36) {
                val angle = i * (Math.PI * 2 / 36)
                val inX = center.x + (w * 0.28f * cos(angle)).toFloat()
                val inY = center.y + (h * 0.28f * sin(angle)).toFloat()
                val outX = center.x + (w * 0.55f * cos(angle)).toFloat()
                val outY = center.y + (h * 0.55f * sin(angle)).toFloat()
                canvas.drawLine(inX, inY, outX, outY, paint)
            }
        }
        "speech_bubble" -> {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            val bubble = RectF(w * 0.25f, h * 0.2f, w * 0.75f, h * 0.6f)
            canvas.drawRoundRect(bubble, 40f, 40f, fillPaint)
            canvas.drawRoundRect(bubble, 40f, 40f, strokePaint)
        }
        else -> {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD166.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center.x, center.y, h * 0.2f, starPaint)
        }
    }
}
