package com.michaldrabik.data_remote.floppy

import org.junit.Assert.assertEquals
import org.junit.Test

class FloppyListsRemoteDataSourceTest {

  @Test
  fun `encodes and decodes list ownership`() {
    val encoded = encodeFloppyListOwnership(localListId = 7, remoteListId = 91)

    assertEquals("7:91", encoded)
    assertEquals(7L to 91L, decodeFloppyListOwnership(encoded))
  }

  @Test
  fun `rejects invalid list ownership`() {
    assertEquals(null, decodeFloppyListOwnership(""))
    assertEquals(null, decodeFloppyListOwnership("7"))
    assertEquals(null, decodeFloppyListOwnership("0:91"))
    assertEquals(null, decodeFloppyListOwnership("7:-1"))
  }

  @Test
  fun `encodes and decodes movie list item ownership`() {
    val item = FloppyListItemRef(FloppyWatchlistType.MOVIES, 603)
    val encoded = encodeFloppyListItemOwnership(localListId = 7, item = item)

    assertEquals("7:m:603", encoded)
    assertEquals(7L to item, decodeFloppyListItemOwnership(encoded))
  }

  @Test
  fun `encodes and decodes show list item ownership`() {
    val item = FloppyListItemRef(FloppyWatchlistType.SHOWS, 1399)
    val encoded = encodeFloppyListItemOwnership(localListId = 8, item = item)

    assertEquals("8:s:1399", encoded)
    assertEquals(8L to item, decodeFloppyListItemOwnership(encoded))
  }

  @Test
  fun `rejects invalid list item ownership`() {
    assertEquals(null, decodeFloppyListItemOwnership("7:x:603"))
    assertEquals(null, decodeFloppyListItemOwnership("7:m"))
    assertEquals(null, decodeFloppyListItemOwnership("-1:m:603"))
    assertEquals(null, decodeFloppyListItemOwnership("7:s:0"))
  }
}
