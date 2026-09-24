package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.AspectRatioMode
import com.example.model.AudioTrackInfo
import com.example.model.SubtitleTrackInfo
import com.example.playback.PlaybackConnection
import com.example.playback.PlaybackStateData
import com.example.ui.components.DoubleTapSeekOverlay
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DividerGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SurfaceGray
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    playbackConnection: PlaybackConnection,
    playbackState: PlaybackStateData,
    doubleTapSeekSeconds: Int = 10,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showBackwardIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioTracksDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }

    var gestureBrightnessText by remember { mutableStateOf<String?>(null) }
    var gestureVolumeText by remember { mutableStateOf<String?>(null) }

    var isLandscape by remember { mutableStateOf(false) }

    // Auto-hide controls timer
    LaunchedEffect(controlsVisible, lastInteractionTime, playbackState.isPlaying) {
        if (controlsVisible && playbackState.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Reset orientation on dispose
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        // Media3 PlayerView AndroidView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playbackConnection.controller
                }
            },
            update = { playerView ->
                playerView.player = playbackConnection.controller
                playerView.resizeMode = when (playbackState.aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.FIXED_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    AspectRatioMode.FIXED_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Detection Overlay:
        // Left half: Double tap seek backward & Vertical drag brightness
        // Right half: Double tap seek forward & Vertical drag volume
        // Single tap: toggle controls visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            lastInteractionTime = System.currentTimeMillis()
                            val isLeft = offset.x < size.width / 2f
                            if (isLeft) {
                                playbackConnection.seekBy(-doubleTapSeekSeconds * 1000L)
                                showBackwardIndicator = true
                                scope.launch {
                                    delay(650)
                                    showBackwardIndicator = false
                                }
                            } else {
                                playbackConnection.seekBy(doubleTapSeekSeconds * 1000L)
                                showForwardIndicator = true
                                scope.launch {
                                    delay(650)
                                    showForwardIndicator = false
                                }
                            }
                        },
                        onTap = {
                            controlsVisible = !controlsVisible
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            gestureBrightnessText = null
                            gestureVolumeText = null
                        },
                        onVerticalDrag = { change, dragAmount ->
                            val isLeft = change.position.x < size.width / 2f
                            if (isLeft) {
                                // Brightness adjustment
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    val curBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                    val newBrightness = (curBrightness - (dragAmount / size.height)).coerceIn(0.01f, 1.0f)
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                    gestureBrightnessText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                }
                            } else {
                                // Volume adjustment
                                val curVol = playbackState.volume
                                val newVol = (curVol - (dragAmount / size.height)).coerceIn(0f, 1f)
                                playbackConnection.setVolume(newVol)
                                gestureVolumeText = "Volume: ${(newVol * 100).toInt()}%"
                            }
                        }
                    )
                }
        )

        // Double-Tap Animated Indicators
        DoubleTapSeekOverlay(
            showBackwardIndicator = showBackwardIndicator,
            showForwardIndicator = showForwardIndicator,
            seekSeconds = doubleTapSeekSeconds
        )

        // Gesture feedback banner (Volume / Brightness)
        if (gestureBrightnessText != null || gestureVolumeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (gestureBrightnessText != null) Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = gestureBrightnessText ?: gestureVolumeText ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error message overlay
        if (playbackState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(PureWhite, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Playback Error",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackState.errorMessage,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { playbackConnection.retryPlayback() },
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = PureWhite),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry")
                        }
                        Button(
                            onClick = {
                                playbackConnection.clearError()
                                onClose()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGray, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Controls Overlay (Animated In/Out)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
            ) {
                // Top Bar
                Surface(
                    color = Color(0xB318181B),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onClose()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close player",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = playbackState.currentItem?.title ?: "Video",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Subtitle toggle
                        if (playbackState.subtitleTracks.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showSubtitlesDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = if (playbackState.isSubtitleEnabled) Color.White else Color(0x80FFFFFF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Audio track selector
                        if (playbackState.audioTracks.size > 1) {
                            IconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showAudioTracksDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Audio Tracks",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Speed selector
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                showSpeedDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // PiP button
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                    try {
                                        val pipParams = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(pipParams)
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("pip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Orientation toggle
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Center Controls: Prev, Rewind 10s, Play/Pause, Forward 10s, Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            playbackConnection.skipToPrevious()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            playbackConnection.seekBy(-doubleTapSeekSeconds * 1000L)
                            showBackwardIndicator = true
                            scope.launch {
                                delay(650)
                                showBackwardIndicator = false
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xCCFFFFFF))
                            .clickable {
                                lastInteractionTime = System.currentTimeMillis()
                                playbackConnection.togglePlayPause()
                            }
                            .testTag("player_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = TextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            playbackConnection.seekBy(doubleTapSeekSeconds * 1000L)
                            showForwardIndicator = true
                            scope.launch {
                                delay(650)
                                showForwardIndicator = false
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            playbackConnection.skipToNext()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Controls Bar
                Surface(
                    color = Color(0xB318181B),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        // Slider / Seek Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = playbackState.positionFormatted,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Slider(
                                value = playbackState.progressFraction,
                                onValueChange = { fraction ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    val targetMs = (fraction * playbackState.durationMs).toLong()
                                    playbackConnection.seekTo(targetMs)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = PureWhite,
                                    activeTrackColor = PureWhite,
                                    inactiveTrackColor = Color(0x66FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("player_seek_slider")
                            )

                            Text(
                                text = playbackState.durationFormatted,
                                color = Color(0xFFD1D1D6),
                                fontSize = 11.sp
                            )
                        }

                        // Bottom Actions Row: Repeat, Shuffle, Aspect Ratio, Volume, Speed Tag
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Repeat mode
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        playbackConnection.cycleRepeatMode()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = when (playbackState.repeatMode) {
                                            1 -> Icons.Default.RepeatOne
                                            else -> Icons.Default.Repeat
                                        },
                                        contentDescription = "Repeat",
                                        tint = if (playbackState.repeatMode > 0) PureWhite else Color(0x80FFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Shuffle
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        playbackConnection.toggleShuffle()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (playbackState.isShuffle) PureWhite else Color(0x80FFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Mute / Unmute
                                IconButton(
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        playbackConnection.toggleMute()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = PureWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Aspect ratio toggle
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33FFFFFF))
                                        .clickable {
                                            lastInteractionTime = System.currentTimeMillis()
                                            val nextMode = when (playbackState.aspectRatioMode) {
                                                AspectRatioMode.FIT -> AspectRatioMode.FILL
                                                AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                                AspectRatioMode.ZOOM -> AspectRatioMode.FIXED_16_9
                                                AspectRatioMode.FIXED_16_9 -> AspectRatioMode.FIXED_4_3
                                                AspectRatioMode.FIXED_4_3 -> AspectRatioMode.FIT
                                            }
                                            playbackConnection.setAspectRatio(nextMode)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = playbackState.aspectRatioMode.label,
                                        color = PureWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Playback Speed pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33FFFFFF))
                                        .clickable {
                                            lastInteractionTime = System.currentTimeMillis()
                                            showSpeedDialog = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${playbackState.playbackSpeed}x",
                                        color = PureWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // White + Gray Dialogs for Track selection & Speed

    // 1. Speed Dialog
    if (showSpeedDialog) {
        Dialog(onDismissRequest = { showSpeedDialog = false }) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Playback Speed", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                        val isSelected = playbackState.playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) SurfaceGray else PureWhite)
                                .clickable {
                                    playbackConnection.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${speed}x",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextPrimary
                            )
                            if (isSelected) {
                                Text(text = "Selected", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Audio Tracks Dialog
    if (showAudioTracksDialog) {
        Dialog(onDismissRequest = { showAudioTracksDialog = false }) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Select Audio Track", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(playbackState.audioTracks) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (track.isSelected) SurfaceGray else PureWhite)
                                    .clickable {
                                        playbackConnection.selectAudioTrack(track)
                                        showAudioTracksDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = TextPrimary
                                )
                                if (track.isSelected) {
                                    Text(text = "Active", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Subtitles Dialog
    if (showSubtitlesDialog) {
        Dialog(onDismissRequest = { showSubtitlesDialog = false }) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Subtitles", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Disable Subtitles option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!playbackState.isSubtitleEnabled) SurfaceGray else PureWhite)
                            .clickable {
                                playbackConnection.setSubtitleEnabled(false)
                                showSubtitlesDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            fontSize = 14.sp,
                            fontWeight = if (!playbackState.isSubtitleEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary
                        )
                        if (!playbackState.isSubtitleEnabled) {
                            Text(text = "Active", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = 4.dp))

                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(playbackState.subtitleTracks) { sub ->
                            val isSelected = playbackState.isSubtitleEnabled && sub.isSelected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SurfaceGray else PureWhite)
                                    .clickable {
                                        playbackConnection.selectSubtitleTrack(sub)
                                        showSubtitlesDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = TextPrimary
                                )
                                if (isSelected) {
                                    Text(text = "Active", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
