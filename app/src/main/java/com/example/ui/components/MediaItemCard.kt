package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaItemData
import com.example.model.MediaType
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DividerGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SurfaceGray
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun MediaItemCard(
    item: MediaItemData,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = PureWhite,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("media_card_${item.uriString.hashCode()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceGray),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.uriString,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 68.dp, height = 50.dp)
                )

                // Overlay icon if no image
                Icon(
                    imageVector = when (item.mediaType) {
                        MediaType.VIDEO -> Icons.Default.VideoFile
                        MediaType.AUDIO -> Icons.Default.MusicNote
                        MediaType.IMAGE -> Icons.Default.Image
                    },
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )

                // Duration badge on bottom right of video thumbnail
                if (item.mediaType != MediaType.IMAGE && item.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.durationFormatted,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val subtext = when (item.mediaType) {
                        MediaType.VIDEO -> {
                            val parts = mutableListOf<String>()
                            if (item.width > 0 && item.height > 0) parts.add("${item.width}x${item.height}")
                            if (item.sizeFormatted.isNotEmpty()) parts.add(item.sizeFormatted)
                            if (parts.isEmpty()) item.durationFormatted else parts.joinToString(" • ")
                        }
                        MediaType.AUDIO -> {
                            val parts = mutableListOf<String>()
                            if (item.artist.isNotEmpty()) parts.add(item.artist)
                            if (item.album.isNotEmpty()) parts.add(item.album)
                            if (parts.isEmpty()) item.durationFormatted else parts.joinToString(" • ")
                        }
                        MediaType.IMAGE -> {
                            val parts = mutableListOf<String>()
                            if (item.width > 0 && item.height > 0) parts.add("${item.width}x${item.height}")
                            if (item.sizeFormatted.isNotEmpty()) parts.add(item.sizeFormatted)
                            parts.joinToString(" • ")
                        }
                    }
                    Text(
                        text = subtext,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Resume watch progress bar if partially played
                if (item.lastPositionMs > 0 && item.durationMs > 0) {
                    val progress = (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(2.dp),
                        color = TextPrimary,
                        trackColor = DividerGray
                    )
                }
            }

            // Options menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(PureWhite)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details & Info", color = TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            menuExpanded = false
                            onShowDetails()
                        }
                    )
                }
            }
        }
    }
}
