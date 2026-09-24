package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.MediaDao
import com.example.data.local.MediaEntity
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistItemEntity
import com.example.model.MediaItemData
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MediaRepository(
    private val context: Context,
    private val appDatabase: AppDatabase
) {
    private val mediaDao: MediaDao = appDatabase.mediaDao()
    private val playlistDao: PlaylistDao = appDatabase.playlistDao()

    fun getVideos(): Flow<List<MediaItemData>> =
        mediaDao.getMediaByType(MediaType.VIDEO.name).map { list -> list.map { it.toMediaItemData() } }

    fun getAudio(): Flow<List<MediaItemData>> =
        mediaDao.getMediaByType(MediaType.AUDIO.name).map { list -> list.map { it.toMediaItemData() } }

    fun getImages(): Flow<List<MediaItemData>> =
        mediaDao.getMediaByType(MediaType.IMAGE.name).map { list -> list.map { it.toMediaItemData() } }

    fun getRecentlyPlayed(): Flow<List<MediaItemData>> =
        mediaDao.getRecentlyPlayed().map { list -> list.map { it.toMediaItemData() } }

    fun search(query: String): Flow<List<MediaItemData>> =
        mediaDao.searchMedia(query).map { list -> list.map { it.toMediaItemData() } }

    suspend fun getMediaItem(uriString: String): MediaItemData? = withContext(Dispatchers.IO) {
        mediaDao.getMediaByUri(uriString)?.toMediaItemData()
    }

    suspend fun updatePlaybackPosition(uriString: String, positionMs: Long) = withContext(Dispatchers.IO) {
        mediaDao.updatePlaybackPosition(uriString, positionMs, System.currentTimeMillis())
    }

    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val scannedItems = mutableListOf<MediaEntity>()

        // 1. Scan Videos
        scanVideos(contentResolver, scannedItems)

        // 2. Scan Audio
        scanAudio(contentResolver, scannedItems)

        // 3. Scan Images
        scanImages(contentResolver, scannedItems)

        // Merge with existing playback positions so resume states are never lost
        for (item in scannedItems) {
            val existing = mediaDao.getMediaByUri(item.uriString)
            if (existing != null) {
                scannedItems[scannedItems.indexOf(item)] = item.copy(
                    lastPositionMs = existing.lastPositionMs,
                    lastPlayedTimestamp = existing.lastPlayedTimestamp
                )
            }
        }

        if (scannedItems.isNotEmpty()) {
            mediaDao.insertAll(scannedItems)
        }
    }

    private fun scanVideos(resolver: ContentResolver, outList: MutableList<MediaEntity>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        try {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Video.Media.ARTIST)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val name = cursor.getString(nameCol) ?: "Video $id"
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: name else name
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "" else ""
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/*" else "video/*"
                    val date = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val width = if (wCol != -1) cursor.getInt(wCol) else 0
                    val height = if (hCol != -1) cursor.getInt(hCol) else 0
                    val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "" else ""

                    outList.add(
                        MediaEntity(
                            uriString = contentUri.toString(),
                            title = title,
                            artist = artist,
                            album = "",
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            mediaType = MediaType.VIDEO.name,
                            dateModified = date,
                            width = width,
                            height = height,
                            folderName = bucket
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if permission or query fails
        }
    }

    private fun scanAudio(resolver: ContentResolver, outList: MutableList<MediaEntity>) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        try {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val name = cursor.getString(nameCol) ?: "Audio $id"
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: name else name
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "" else ""
                    val album = if (albumCol != -1) cursor.getString(albumCol) ?: "" else ""
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/*" else "audio/*"
                    val date = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()

                    outList.add(
                        MediaEntity(
                            uriString = contentUri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            mediaType = MediaType.AUDIO.name,
                            dateModified = date,
                            folderName = album
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }
    }

    private fun scanImages(resolver: ContentResolver, outList: MutableList<MediaEntity>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        try {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val name = cursor.getString(nameCol) ?: "Image $id"
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: name else name
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/*" else "image/*"
                    val date = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                    val width = if (wCol != -1) cursor.getInt(wCol) else 0
                    val height = if (hCol != -1) cursor.getInt(hCol) else 0
                    val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "" else ""

                    outList.add(
                        MediaEntity(
                            uriString = contentUri.toString(),
                            title = title,
                            sizeBytes = size,
                            mimeType = mime,
                            mediaType = MediaType.IMAGE.name,
                            dateModified = date,
                            width = width,
                            height = height,
                            folderName = bucket
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }
    }

    suspend fun importFromUri(uri: Uri): MediaItemData? = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // If not supported by provider, proceed with temporary URI access
            }

            val mime = context.contentResolver.getType(uri) ?: ""
            var displayName = "Imported Media"
            var size = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }

            val mediaType = when {
                mime.startsWith("audio/") -> MediaType.AUDIO
                mime.startsWith("image/") -> MediaType.IMAGE
                mime.startsWith("video/") -> MediaType.VIDEO
                displayName.endsWith(".mp4", true) || displayName.endsWith(".mkv", true) || displayName.endsWith(".webm", true) || displayName.endsWith(".avi", true) || displayName.endsWith(".mov", true) -> MediaType.VIDEO
                displayName.endsWith(".mp3", true) || displayName.endsWith(".flac", true) || displayName.endsWith(".wav", true) || displayName.endsWith(".m4a", true) || displayName.endsWith(".ogg", true) || displayName.endsWith(".aac", true) -> MediaType.AUDIO
                displayName.endsWith(".jpg", true) || displayName.endsWith(".png", true) || displayName.endsWith(".webp", true) || displayName.endsWith(".jpeg", true) || displayName.endsWith(".gif", true) -> MediaType.IMAGE
                else -> MediaType.VIDEO
            }

            val entity = MediaEntity(
                uriString = uri.toString(),
                title = displayName,
                sizeBytes = size,
                mimeType = mime,
                mediaType = mediaType.name,
                dateModified = System.currentTimeMillis()
            )

            mediaDao.insertOrUpdate(entity)
            entity.toMediaItemData()
        } catch (_: Exception) {
            null
        }
    }

    // Playlists
    fun getPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun createPlaylist(name: String, type: String = "ALL"): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name, type = type))
    }

    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylistName(id, newName)
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist(id)
        playlistDao.deletePlaylist(id)
    }

    fun getPlaylistItems(playlistId: Long): Flow<List<MediaItemData>> {
        return playlistDao.getPlaylistItems(playlistId).map { items ->
            val result = mutableListOf<MediaItemData>()
            for (item in items) {
                val media = mediaDao.getMediaByUri(item.mediaUri)?.toMediaItemData()
                if (media != null) {
                    result.add(media)
                }
            }
            result
        }
    }

    suspend fun addToPlaylist(playlistId: Long, mediaUri: String) = withContext(Dispatchers.IO) {
        val currentItems = playlistDao.getPlaylistItems(playlistId).firstOrNull() ?: emptyList()
        val nextIndex = currentItems.size
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaUri = mediaUri,
                orderIndex = nextIndex
            )
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, mediaUri: String) = withContext(Dispatchers.IO) {
        playlistDao.removePlaylistItem(playlistId, mediaUri)
    }
}
