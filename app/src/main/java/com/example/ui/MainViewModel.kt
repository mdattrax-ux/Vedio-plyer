package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AuraApplication
import com.example.data.local.PlaylistEntity
import com.example.data.preferences.UserPreferences
import com.example.model.MediaItemData
import com.example.model.MediaType
import com.example.playback.PlaybackConnection
import com.example.playback.PlaybackStateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    VIDEOS("Videos"),
    SONGS("Songs"),
    IMAGES("Images"),
    PLAYLISTS("Playlists"),
    RECENT("Recent")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository = (application as AuraApplication).mediaRepository
    private val preferencesRepository = (application as AuraApplication).preferencesRepository
    val playbackConnection = PlaybackConnection.getInstance(application)

    val playbackState: StateFlow<PlaybackStateData> = playbackConnection.playbackState

    private val _selectedTab = MutableStateFlow(MainTab.VIDEOS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Full screen overlay states
    private val _activeVideoPlayer = MutableStateFlow(false)
    val activeVideoPlayer: StateFlow<Boolean> = _activeVideoPlayer.asStateFlow()

    private val _activeImageViewer = MutableStateFlow<MediaItemData?>(null)
    val activeImageViewer: StateFlow<MediaItemData?> = _activeImageViewer.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    val videos: StateFlow<List<MediaItemData>> = mediaRepository.getVideos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songs: StateFlow<List<MediaItemData>> = mediaRepository.getAudio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val images: StateFlow<List<MediaItemData>> = mediaRepository.getImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recent: StateFlow<List<MediaItemData>> = mediaRepository.getRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = mediaRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    // Search results combine
    val searchResults: StateFlow<List<MediaItemData>> = combine(
        _searchQuery,
        videos,
        songs,
        images
    ) { query, vList, sList, iList ->
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            (vList + sList + iList).filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.folderName.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        scanMedia()
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearching.value = query.isNotBlank()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
    }

    fun scanMedia() {
        viewModelScope.launch {
            mediaRepository.scanLocalMedia()
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val item = mediaRepository.importFromUri(uri)
            if (item != null) {
                when (item.mediaType) {
                    MediaType.VIDEO -> playVideo(item)
                    MediaType.AUDIO -> playAudio(item)
                    MediaType.IMAGE -> openImageViewer(item)
                }
            }
        }
    }

    fun playVideo(item: MediaItemData, playlist: List<MediaItemData> = listOf(item)) {
        playbackConnection.playMedia(item, playlist)
        _activeVideoPlayer.value = true
    }

    fun playAudio(item: MediaItemData, playlist: List<MediaItemData> = listOf(item)) {
        playbackConnection.playMedia(item, playlist)
        // For audio, mini-player or full player can be shown. If user wants, they can expand.
    }

    fun openImageViewer(item: MediaItemData) {
        _activeImageViewer.value = item
    }

    fun closeImageViewer() {
        _activeImageViewer.value = null
    }

    fun openVideoPlayer() {
        _activeVideoPlayer.value = true
    }

    fun closeVideoPlayer() {
        _activeVideoPlayer.value = false
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String, type: String = "ALL") {
        viewModelScope.launch {
            mediaRepository.createPlaylist(name, type)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            mediaRepository.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            mediaRepository.deletePlaylist(id)
            if (_selectedPlaylist.value?.id == id) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun addToPlaylist(playlistId: Long, mediaUri: String) {
        viewModelScope.launch {
            mediaRepository.addToPlaylist(playlistId, mediaUri)
        }
    }

    fun removeFromPlaylist(playlistId: Long, mediaUri: String) {
        viewModelScope.launch {
            mediaRepository.removeFromPlaylist(playlistId, mediaUri)
        }
    }

    fun getPlaylistItemsFlow(playlistId: Long): StateFlow<List<MediaItemData>> {
        return mediaRepository.getPlaylistItems(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Settings actions
    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultSpeed(speed)
            playbackConnection.setPlaybackSpeed(speed)
        }
    }

    fun setResumePlayback(enable: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateResumePlayback(enable)
        }
    }

    fun setBackgroundPlayback(enable: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateBackgroundPlayback(enable)
        }
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updateDoubleTapSeek(seconds)
        }
    }
}
