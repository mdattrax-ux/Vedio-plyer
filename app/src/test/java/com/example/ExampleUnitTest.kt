package com.example

import com.example.model.MediaItemData
import com.example.model.MediaType
import com.example.playback.PlaybackStateData
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testMediaItemDurationFormatting() {
    val itemShort = MediaItemData(
      uriString = "content://media/1",
      title = "Track 1",
      durationMs = 125000L // 2 min 5 sec
    )
    assertEquals("02:05", itemShort.durationFormatted)

    val itemLong = MediaItemData(
      uriString = "content://media/2",
      title = "Movie",
      durationMs = 3665000L // 1 hr 1 min 5 sec
    )
    assertEquals("1:01:05", itemLong.durationFormatted)
  }

  @Test
  fun testMediaItemSizeFormatting() {
    val item = MediaItemData(
      uriString = "content://media/3",
      title = "Video",
      sizeBytes = 15728640L // ~15 MB
    )
    assertEquals("15.0 MB", item.sizeFormatted)
  }

  @Test
  fun testPlaybackStateProgress() {
    val state = PlaybackStateData(
      currentPositionMs = 30000L,
      durationMs = 60000L
    )
    assertEquals(0.5f, state.progressFraction, 0.001f)
    assertEquals("00:30", state.positionFormatted)
    assertEquals("01:00", state.durationFormatted)
  }
}
