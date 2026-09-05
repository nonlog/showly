package com.michaldrabik.ui_base.floppy

internal object BridgeHistoryKey {
  const val DOMAIN = "history"

  fun movie(
    tmdbId: Long,
    watchedAt: Long,
  ): String = "m:$tmdbId:$watchedAt"

  fun episode(
    showTmdbId: Long,
    season: Int,
    episode: Int,
    watchedAt: Long,
  ): String = "e:$showTmdbId:$season:$episode:$watchedAt"
}
