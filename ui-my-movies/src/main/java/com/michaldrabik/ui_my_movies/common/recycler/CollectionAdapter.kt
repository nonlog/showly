package com.michaldrabik.ui_my_movies.common.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.BaseMovieAdapter
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_base.common.ListViewMode.GRID
import com.michaldrabik.ui_base.common.ListViewMode.LIST_COMPACT
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.common.views.MovieView
import com.michaldrabik.ui_base.common.views.media.MovieCompactItemView
import com.michaldrabik.ui_base.common.views.media.MovieGridItemView
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_movies.common.recycler.CollectionListItem.FiltersItem
import com.michaldrabik.ui_my_movies.common.recycler.CollectionListItem.MovieItem
import com.michaldrabik.ui_my_movies.common.views.CollectionMovieFiltersView
import com.michaldrabik.ui_my_movies.common.views.CollectionMovieView

class CollectionAdapter(
  listChangeListener: () -> Unit,
  private val itemClickListener: (CollectionListItem) -> Unit,
  private val itemLongClickListener: (CollectionListItem) -> Unit,
  private val sortChipClickListener: (SortOrder, SortType) -> Unit,
  private val upcomingChipClickListener: () -> Unit,
  private val genreChipClickListener: () -> Unit,
  private val listViewChipClickListener: () -> Unit,
  private val missingImageListener: (CollectionListItem, Boolean) -> Unit,
  private val missingTranslationListener: (CollectionListItem) -> Unit,
  private val upcomingChipVisible: Boolean = true,
) : BaseMovieAdapter<CollectionListItem>(
    listChangeListener = listChangeListener,
  ) {

  companion object {
    private const val VIEW_TYPE_MOVIE_NORMAL = 1
    private const val VIEW_TYPE_FILTERS = 2
    private const val VIEW_TYPE_MOVIE_COMPACT = 3
    private const val VIEW_TYPE_MOVIE_GRID = 4
  }

  override val asyncDiffer = AsyncListDiffer(this, CollectionItemDiffCallback())

  var listViewMode: ListViewMode = LIST_NORMAL
    set(value) {
      if (field == value) return
      field = value
      notifyDataSetChanged()
    }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ) = when (viewType) {
    VIEW_TYPE_MOVIE_NORMAL -> BaseViewHolder(
      CollectionMovieView(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_MOVIE_COMPACT -> BaseViewHolder(
      MovieCompactItemView<MovieItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_MOVIE_GRID -> BaseViewHolder(
      MovieGridItemView<MovieItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_FILTERS -> BaseViewHolder(
      CollectionMovieFiltersView(parent.context).apply {
        onSortChipClicked = this@CollectionAdapter.sortChipClickListener
        onFilterUpcomingClicked = this@CollectionAdapter.upcomingChipClickListener
        onGenreChipClicked = this@CollectionAdapter.genreChipClickListener
        onListViewModeClicked = this@CollectionAdapter.listViewChipClickListener
        isUpcomingChipVisible = upcomingChipVisible
      },
    )
    else -> throw IllegalStateException()
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    when (val item = asyncDiffer.currentList[position]) {
      is FiltersItem -> (holder.itemView as CollectionMovieFiltersView).bind(item, listViewMode)
      is MovieItem -> when (holder.itemViewType) {
        VIEW_TYPE_MOVIE_NORMAL -> (holder.itemView as CollectionMovieView).bind(item)
        VIEW_TYPE_MOVIE_COMPACT -> {
          (holder.itemView as MovieCompactItemView<MovieItem>).bind(
            item = item,
            title = item.displayTitle(),
            subtitle = item.movie.year.takeIf { it > 0 }?.toString().orEmpty(),
            translationMissing = item.translation == null,
          )
        }
        VIEW_TYPE_MOVIE_GRID -> {
          (holder.itemView as MovieGridItemView<MovieItem>).bind(
            item = item,
            title = item.displayTitle(),
            translationMissing = item.translation == null,
          )
        }
      }
    }
  }

  override fun getItemViewType(position: Int) =
    when (asyncDiffer.currentList[position]) {
      is MovieItem -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_MOVIE_NORMAL
        LIST_COMPACT -> VIEW_TYPE_MOVIE_COMPACT
        GRID -> VIEW_TYPE_MOVIE_GRID
      }
      is FiltersItem -> VIEW_TYPE_FILTERS
      else -> throw IllegalStateException()
    }

  private fun <T : MovieView<MovieItem>> T.applyMediaListeners(): T =
    apply {
      itemClickListener = { item -> this@CollectionAdapter.itemClickListener(item) }
      itemLongClickListener = { item -> this@CollectionAdapter.itemLongClickListener(item) }
      missingImageListener = { item, force -> this@CollectionAdapter.missingImageListener(item, force) }
      missingTranslationListener = { item -> this@CollectionAdapter.missingTranslationListener(item) }
    }

  private fun MovieItem.displayTitle() = translation?.title?.takeIf { it.isNotBlank() } ?: movie.title
}
