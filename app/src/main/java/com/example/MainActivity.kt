package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.MediaItemData
import com.example.model.MediaType
import com.example.ui.MainTab
import com.example.ui.MainViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.BottomNavBar
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.MediaInfoDialog
import com.example.ui.components.MediaItemCard
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TopSearchBar
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.ImagesScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.RecentScreen
import com.example.ui.screens.SongsScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.AuraPlayerTheme
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextTertiary

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuraPlayerTheme {
                AuraApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AuraApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val videos by viewModel.videos.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val images by viewModel.images.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    val playbackState by viewModel.playbackState.collectAsState()
    val activeVideoPlayer by viewModel.activeVideoPlayer.collectAsState()
    val activeImageViewer by viewModel.activeImageViewer.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

    val playlistItems by remember(selectedPlaylist) {
        if (selectedPlaylist != null) viewModel.getPlaylistItemsFlow(selectedPlaylist!!.id)
        else androidx.compose.runtime.mutableStateOf(kotlinx.coroutines.flow.MutableStateFlow(emptyList<MediaItemData>())).value
    }.collectAsState()

    // Dialog states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var itemForAddToPlaylist by remember { mutableStateOf<MediaItemData?>(null) }
    var itemForDetails by remember { mutableStateOf<MediaItemData?>(null) }

    // SAF File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris?.forEach { uri ->
            viewModel.importUri(uri)
        }
    }

    // Storage and Notification permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.scanMedia()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    val openPicker = {
        filePickerLauncher.launch(arrayOf("video/*", "audio/*", "image/*"))
    }

    Box(modifier = Modifier.fillMaxSize().background(PureWhite)) {
        Scaffold(
            topBar = {
                Surface(
                    color = PureWhite,
                    modifier = Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                ) {
                    TopSearchBar(
                        searchQuery = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onClearSearch = { viewModel.clearSearch() },
                        onOpenSettings = { showSettingsDialog = true },
                        onOpenFilePicker = openPicker,
                        onRefreshScan = { viewModel.scanMedia() }
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    // Mini player bar
                    if (playbackState.currentItem != null && !activeVideoPlayer) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = { viewModel.playbackConnection.togglePlayPause() },
                            onSkipNext = { viewModel.playbackConnection.skipToNext() },
                            onOpenPlayer = {
                                if (playbackState.currentItem?.mediaType == MediaType.VIDEO) {
                                    viewModel.openVideoPlayer()
                                } else {
                                    viewModel.openVideoPlayer()
                                }
                            }
                        )
                    }

                    // Navigation bar
                    BottomNavBar(
                        selectedTab = selectedTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            },
            containerColor = PureWhite,
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isSearching) {
                    // Search Results List
                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                text = "No media matching \"$searchQuery\"",
                                color = TextTertiary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = "${searchResults.size} Results",
                                    fontSize = 14.sp,
                                    color = TextTertiary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(searchResults, key = { it.uriString }) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = {
                                        when (item.mediaType) {
                                            MediaType.VIDEO -> viewModel.playVideo(item)
                                            MediaType.AUDIO -> viewModel.playAudio(item)
                                            MediaType.IMAGE -> viewModel.openImageViewer(item)
                                        }
                                    },
                                    onAddToPlaylist = { itemForAddToPlaylist = item },
                                    onShowDetails = { itemForDetails = item }
                                )
                            }
                        }
                    }
                } else {
                    when (selectedTab) {
                        MainTab.VIDEOS -> {
                            VideosScreen(
                                videos = videos,
                                onPlayVideo = { item, list -> viewModel.playVideo(item, list) },
                                onAddToPlaylist = { itemForAddToPlaylist = it },
                                onShowDetails = { itemForDetails = it },
                                onOpenFilePicker = openPicker,
                                onRefreshScan = { viewModel.scanMedia() }
                            )
                        }
                        MainTab.SONGS -> {
                            SongsScreen(
                                songs = songs,
                                onPlaySong = { item, list -> viewModel.playAudio(item, list) },
                                onAddToPlaylist = { itemForAddToPlaylist = it },
                                onShowDetails = { itemForDetails = it },
                                onOpenFilePicker = openPicker,
                                onRefreshScan = { viewModel.scanMedia() }
                            )
                        }
                        MainTab.IMAGES -> {
                            ImagesScreen(
                                images = images,
                                onOpenImage = { viewModel.openImageViewer(it) },
                                onOpenFilePicker = openPicker,
                                onRefreshScan = { viewModel.scanMedia() }
                            )
                        }
                        MainTab.PLAYLISTS -> {
                            PlaylistsScreen(
                                playlists = playlists,
                                selectedPlaylist = selectedPlaylist,
                                playlistItems = playlistItems,
                                onSelectPlaylist = { viewModel.selectPlaylist(it) },
                                onCreatePlaylist = { showCreatePlaylistDialog = true },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onPlayAll = { list ->
                                    list.firstOrNull()?.let { first ->
                                        if (first.mediaType == MediaType.VIDEO) viewModel.playVideo(first, list)
                                        else viewModel.playAudio(first, list)
                                    }
                                },
                                onShuffleAll = { list ->
                                    list.firstOrNull()?.let { first ->
                                        if (first.mediaType == MediaType.VIDEO) viewModel.playVideo(first, list)
                                        else viewModel.playAudio(first, list)
                                    }
                                },
                                onPlayMedia = { item, list ->
                                    if (item.mediaType == MediaType.VIDEO) viewModel.playVideo(item, list)
                                    else viewModel.playAudio(item, list)
                                },
                                onRemoveItem = { plId, uri -> viewModel.removeFromPlaylist(plId, uri) },
                                onShowDetails = { itemForDetails = it }
                            )
                        }
                        MainTab.RECENT -> {
                            RecentScreen(
                                recentItems = recent,
                                onPlayMedia = { item, list ->
                                    when (item.mediaType) {
                                        MediaType.VIDEO -> viewModel.playVideo(item, list)
                                        MediaType.AUDIO -> viewModel.playAudio(item, list)
                                        MediaType.IMAGE -> viewModel.openImageViewer(item)
                                    }
                                },
                                onAddToPlaylist = { itemForAddToPlaylist = it },
                                onShowDetails = { itemForDetails = it }
                            )
                        }
                    }
                }
            }
        }

        // Full Screen Video Player
        if (activeVideoPlayer && playbackState.currentItem != null) {
            VideoPlayerScreen(
                playbackConnection = viewModel.playbackConnection,
                playbackState = playbackState,
                doubleTapSeekSeconds = preferences.doubleTapSeekSeconds,
                onClose = { viewModel.closeVideoPlayer() }
            )
        }

        // Full Screen Image Viewer
        activeImageViewer?.let { imageItem ->
            ImageViewerScreen(
                item = imageItem,
                onClose = { viewModel.closeImageViewer() }
            )
        }

        // Dialogs
        if (showSettingsDialog) {
            SettingsDialog(
                preferences = preferences,
                onDismiss = { showSettingsDialog = false },
                onUpdateSpeed = { viewModel.setDefaultSpeed(it) },
                onUpdateResume = { viewModel.setResumePlayback(it) },
                onUpdateBackground = { viewModel.setBackgroundPlayback(it) },
                onUpdateDoubleTap = { viewModel.setDoubleTapSeekSeconds(it) }
            )
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name -> viewModel.createPlaylist(name) }
            )
        }

        itemForAddToPlaylist?.let { item ->
            AddToPlaylistDialog(
                mediaItem = item,
                playlists = playlists,
                onDismiss = { itemForAddToPlaylist = null },
                onAddToPlaylist = { plId -> viewModel.addToPlaylist(plId, item.uriString) },
                onCreateNewPlaylist = { showCreatePlaylistDialog = true }
            )
        }

        itemForDetails?.let { item ->
            MediaInfoDialog(
                item = item,
                onDismiss = { itemForDetails = null }
            )
        }
    }
}
