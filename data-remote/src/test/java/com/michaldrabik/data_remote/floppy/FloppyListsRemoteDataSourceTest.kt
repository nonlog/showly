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
}
