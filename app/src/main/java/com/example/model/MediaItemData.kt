package com.example.model

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    FIXED_16_9("16:9"),
    FIXED_4_3("4:3")
}

data class MediaItemData(
    val uriString: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val dateModified: Long = 0L,
    val lastPositionMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val folderName: String = ""
) {
    val durationFormatted: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val sizeFormatted: String
        get() {
            if (sizeBytes <= 0) return ""
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.1f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}

data class AudioTrackInfo(
    val index: Int,
    val id: String,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val index: Int,
    val id: String,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)
