package com.michaldrabik.data_remote.trakt.model

data class SyncHistoryItem(
  val id: Long,
  val type: String,
  val action: String,
  val show: Show? = null,
  val movie: Movie? = null,
  val episode: Episode? = null,
  val watched_at: String? = null,
)
