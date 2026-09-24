package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.AuraApplication
import com.example.model.AspectRatioMode
import com.example.model.AudioTrackInfo
import com.example.model.MediaItemData
import com.example.model.MediaType
import com.example.model.SubtitleTrackInfo
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackConnection private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val _playbackState = MutableStateFlow(PlaybackStateData())
    val playbackState: StateFlow<PlaybackStateData> = _playbackState.asStateFlow()

    private var currentPlaylist: List<MediaItemData> = emptyList()
    private var positionUpdateJob: Job? = null
    private var previousVolume: Float = 1.0f

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.let { player ->
                    player.addListener(PlayerEventListener())
                    updateStateFromPlayer(player)
                    startPeriodicPositionUpdates()
                }
            } catch (_: Exception) {
                _playbackState.update { it.copy(errorMessage = "Unable to connect to playback service.") }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPeriodicPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(300)
                controller?.let { player ->
                    if (player.isPlaying || player.playbackState == Player.STATE_READY) {
                        _playbackState.update { current ->
                            current.copy(
                                currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = player.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateStateFromPlayer(player: Player) {
        val currentMediaItem = player.currentMediaItem
        val mediaUri = currentMediaItem?.mediaId.orEmpty()
        val foundItem = currentPlaylist.find { it.uriString == mediaUri }
            ?: currentMediaItem?.let {
                MediaItemData(
                    uriString = mediaUri,
                    title = it.mediaMetadata.title?.toString() ?: "Now Playing",
                    artist = it.mediaMetadata.artist?.toString() ?: "",
                    album = it.mediaMetadata.albumTitle?.toString() ?: "",
                    durationMs = player.duration.coerceAtLeast(0L)
                )
            }

        val audioTracks = mutableListOf<AudioTrackInfo>()
        val subtitleTracks = mutableListOf<SubtitleTrackInfo>()
        extractTracks(player.currentTracks, audioTracks, subtitleTracks)

        val playerRepeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> 1
            Player.REPEAT_MODE_ALL -> 2
            else -> 0
        }

        val currentTrackParams = player.trackSelectionParameters
        val isSubtitleDisabled = currentTrackParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        _playbackState.update { state ->
            state.copy(
                currentItem = foundItem,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                playbackSpeed = player.playbackParameters.speed,
                repeatMode = playerRepeatMode,
                isShuffle = player.shuffleModeEnabled,
                volume = player.volume,
                isMuted = player.volume == 0f,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                isSubtitleEnabled = !isSubtitleDisabled,
                queue = currentPlaylist,
                queueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                errorMessage = null
            )
        }
    }

    private fun extractTracks(
        tracks: Tracks,
        outAudio: MutableList<AudioTrackInfo>,
        outSubtitles: MutableList<SubtitleTrackInfo>
    ) {
        var audioIndex = 0
        var subIndex = 0
        for (group in tracks.groups) {
            val trackType = group.type
            if (trackType == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Audio Track ${audioIndex + 1}"
                    outAudio.add(
                        AudioTrackInfo(
                            index = audioIndex++,
                            id = format.id ?: "$audioIndex",
                            label = label,
                            language = format.language,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            } else if (trackType == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val label = format.label ?: format.language ?: "Subtitle ${subIndex + 1}"
                    outSubtitles.add(
                        SubtitleTrackInfo(
                            index = subIndex++,
                            id = format.id ?: "$subIndex",
                            label = label,
                            language = format.language,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
    }

    fun playMedia(
        item: MediaItemData,
        playlist: List<MediaItemData> = listOf(item),
        startIndex: Int = playlist.indexOfFirst { it.uriString == item.uriString }.coerceAtLeast(0),
        resumePositionMs: Long = item.lastPositionMs
    ) {
        val player = controller ?: return
        currentPlaylist = playlist

        val mediaItems = playlist.map { media ->
            val uri = Uri.parse(media.uriString)
            MediaItem.Builder()
                .setMediaId(media.uriString)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(media.title)
                        .setArtist(media.artist)
                        .setAlbumTitle(media.album)
                        .setDisplayTitle(media.title)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, if (resumePositionMs > 1000L) resumePositionMs else 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.let { player ->
            val duration = player.duration
            val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            player.seekTo(target)
            _playbackState.update { it.copy(currentPositionMs = target) }
        }
    }

    fun seekBy(deltaMs: Long) {
        controller?.let { player ->
            val newPosition = (player.currentPosition + deltaMs).coerceAtLeast(0L)
            val duration = player.duration
            val target = if (duration > 0) newPosition.coerceAtMost(duration) else newPosition
            player.seekTo(target)
            _playbackState.update { it.copy(currentPositionMs = target) }
        }
    }

    fun skipToNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.let { player ->
            player.playbackParameters = PlaybackParameters(speed)
            _playbackState.update { it.copy(playbackSpeed = speed) }
        }
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _playbackState.update {
            it.copy(
                repeatMode = when (nextMode) {
                    Player.REPEAT_MODE_ONE -> 1
                    Player.REPEAT_MODE_ALL -> 2
                    else -> 0
                }
            )
        }
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffle = newShuffle) }
    }

    fun setVolume(volume: Float) {
        val player = controller ?: return
        val clamped = volume.coerceIn(0f, 1f)
        player.volume = clamped
        _playbackState.update { it.copy(volume = clamped, isMuted = clamped == 0f) }
    }

    fun toggleMute() {
        val player = controller ?: return
        if (player.volume > 0f) {
            previousVolume = player.volume
            setVolume(0f)
        } else {
            setVolume(if (previousVolume > 0f) previousVolume else 1.0f)
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatioMode = mode) }
    }

    @OptIn(UnstableApi::class)
    fun selectAudioTrack(audioTrack: AudioTrackInfo) {
        val player = controller ?: return
        val currentTracks = player.currentTracks
        var targetGroup: TrackGroup? = null
        var trackIdxInGroup = -1

        var counter = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (counter == audioTrack.index) {
                        targetGroup = group.mediaTrackGroup
                        trackIdxInGroup = i
                        break
                    }
                    counter++
                }
                if (targetGroup != null) break
            }
        }

        if (targetGroup != null && trackIdxInGroup != -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(targetGroup, trackIdxInGroup))
                .build()
            updateStateFromPlayer(player)
        }
    }

    @OptIn(UnstableApi::class)
    fun selectSubtitleTrack(subTrack: SubtitleTrackInfo) {
        val player = controller ?: return
        val currentTracks = player.currentTracks
        var targetGroup: TrackGroup? = null
        var trackIdxInGroup = -1

        var counter = 0
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (counter == subTrack.index) {
                        targetGroup = group.mediaTrackGroup
                        trackIdxInGroup = i
                        break
                    }
                    counter++
                }
                if (targetGroup != null) break
            }
        }

        if (targetGroup != null && trackIdxInGroup != -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(targetGroup, trackIdxInGroup))
                .build()
            updateStateFromPlayer(player)
        }
    }

    @OptIn(UnstableApi::class)
    fun setSubtitleEnabled(enable: Boolean) {
        val player = controller ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enable)
            .build()
        _playbackState.update { it.copy(isSubtitleEnabled = enable) }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    fun retryPlayback() {
        val player = controller ?: return
        _playbackState.value.currentItem?.let { item ->
            playMedia(item, currentPlaylist, _playbackState.value.queueIndex, _playbackState.value.currentPositionMs)
        } ?: run {
            player.prepare()
            player.play()
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let { updateStateFromPlayer(it) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let { updateStateFromPlayer(it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { updateStateFromPlayer(it) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            controller?.let { updateStateFromPlayer(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val friendlyMsg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Media file was not found or has been moved."
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Codec or media format unsupported on this device."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Media file appears to be corrupted."
                else -> "Unable to play this media file."
            }
            _playbackState.update { it.copy(errorMessage = friendlyMsg, isPlaying = false, isBuffering = false) }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackConnection? = null

        fun getInstance(context: Context): PlaybackConnection {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackConnection(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
