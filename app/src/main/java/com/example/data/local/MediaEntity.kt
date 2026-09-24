package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.MediaItemData
import com.example.model.MediaType

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val uriString: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val mediaType: String = "VIDEO", // VIDEO, AUDIO, IMAGE
    val dateModified: Long = 0L,
    val lastPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val folderName: String = ""
) {
    fun toMediaItemData(): MediaItemData {
        val type = try {
            MediaType.valueOf(mediaType)
        } catch (_: Exception) {
            MediaType.VIDEO
        }
        return MediaItemData(
            uriString = uriString,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            mediaType = type,
            dateModified = dateModified,
            lastPositionMs = lastPositionMs,
            width = width,
            height = height,
            folderName = folderName
        )
    }

    companion object {
        fun fromMediaItemData(item: MediaItemData, lastPlayedTimestamp: Long = System.currentTimeMillis()): MediaEntity {
            return MediaEntity(
                uriString = item.uriString,
                title = item.title,
                artist = item.artist,
                album = item.album,
                durationMs = item.durationMs,
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType,
                mediaType = item.mediaType.name,
                dateModified = item.dateModified,
                lastPositionMs = item.lastPositionMs,
                lastPlayedTimestamp = lastPlayedTimestamp,
                width = item.width,
                height = item.height,
                folderName = item.folderName
            )
        }
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = "ALL" // ALL, VIDEO, AUDIO
)

@Entity(
    tableName = "playlist_items",
    indices = [
        androidx.room.Index("playlistId"),
        androidx.room.Index("mediaUri")
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaUri: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)
