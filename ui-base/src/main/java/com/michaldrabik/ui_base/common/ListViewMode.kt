package com.michaldrabik.ui_base.common

enum class ListViewMode {
  LIST_NORMAL,
  LIST_COMPACT,
  GRID,
  ;

  fun next(): ListViewMode = entries[(ordinal + 1) % entries.size]

  companion object {
    fun fromName(name: String?): ListViewMode = entries.firstOrNull { it.name == name } ?: LIST_NORMAL
  }
}
