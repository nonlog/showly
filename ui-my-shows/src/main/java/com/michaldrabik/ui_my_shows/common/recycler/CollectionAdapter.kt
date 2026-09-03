package com.michaldrabik.ui_my_shows.common.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.BaseAdapter
import com.michaldrabik.ui_base.BaseMovieAdapter
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_base.common.ListViewMode.GRID
import com.michaldrabik.ui_base.common.ListViewMode.LIST_COMPACT
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.common.views.ShowView
import com.michaldrabik.ui_base.common.views.media.ShowCompactItemView
import com.michaldrabik.ui_base.common.views.media.ShowGridItemView
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem.FiltersItem
import com.michaldrabik.ui_my_shows.common.recycler.CollectionListItem.ShowItem
import com.michaldrabik.ui_my_shows.common.views.CollectionShowFiltersView
import com.michaldrabik.ui_my_shows.common.views.CollectionShowView

class CollectionAdapter(
  listChangeListener: () -> Unit,
  private val itemClickListener: (CollectionListItem) -> Unit,
  private val itemLongClickListener: (CollectionListItem) -> Unit,
  private val sortChipClickListener: (SortOrder, SortType) -> Unit,
  private val upcomingChipClickListener: () -> Unit,
  private val listViewChipClickListener: () -> Unit,
  private val networksChipClickListener: () -> Unit,
  private val genresChipClickListener: () -> Unit,
  private val missingImageListener: (CollectionListItem, Boolean) -> Unit,
  private val missingTranslationListener: (CollectionListItem) -> Unit,
  private val upcomingChipVisible: Boolean = true,
) : BaseAdapter<CollectionListItem>(
    listChangeListener = listChangeListener,
  ) {

  companion object {
    private const val VIEW_TYPE_SHOW_NORMAL = 1
    private const val VIEW_TYPE_FILTERS = 2
    private const val VIEW_TYPE_SHOW_COMPACT = 3
    private const val VIEW_TYPE_SHOW_GRID = 4
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
    VIEW_TYPE_SHOW_NORMAL -> BaseMovieAdapter.BaseViewHolder(
      CollectionShowView(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_SHOW_COMPACT -> BaseMovieAdapter.BaseViewHolder(
      ShowCompactItemView<ShowItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_SHOW_GRID -> BaseMovieAdapter.BaseViewHolder(
      ShowGridItemView<ShowItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_FILTERS -> BaseMovieAdapter.BaseViewHolder(
      CollectionShowFiltersView(parent.context).apply {
        onSortChipClicked = this@CollectionAdapter.sortChipClickListener
        onFilterUpcomingClicked = this@CollectionAdapter.upcomingChipClickListener
        onListViewModeClicked = this@CollectionAdapter.listViewChipClickListener
        onNetworksChipClick = this@CollectionAdapter.networksChipClickListener
        onGenresChipClick = this@CollectionAdapter.genresChipClickListener
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
      is FiltersItem -> (holder.itemView as CollectionShowFiltersView).bind(item, listViewMode)
      is ShowItem -> when (holder.itemViewType) {
        VIEW_TYPE_SHOW_NORMAL -> (holder.itemView as CollectionShowView).bind(item)
        VIEW_TYPE_SHOW_COMPACT -> {
          (holder.itemView as ShowCompactItemView<ShowItem>).bind(
            item = item,
            title = item.displayTitle(),
            subtitle = item.displaySubtitle(),
            translationMissing = item.translation == null,
          )
        }
        VIEW_TYPE_SHOW_GRID -> {
          (holder.itemView as ShowGridItemView<ShowItem>).bind(
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
      is ShowItem -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_SHOW_NORMAL
        LIST_COMPACT -> VIEW_TYPE_SHOW_COMPACT
        GRID -> VIEW_TYPE_SHOW_GRID
      }
      is FiltersItem -> VIEW_TYPE_FILTERS
      else -> throw IllegalStateException()
    }

  private fun <T : ShowView<ShowItem>> T.applyMediaListeners(): T =
    apply {
      itemClickListener = { item -> this@CollectionAdapter.itemClickListener(item) }
      itemLongClickListener = { item -> this@CollectionAdapter.itemLongClickListener(item) }
      missingImageListener = { item, force -> this@CollectionAdapter.missingImageListener(item, force) }
      missingTranslationListener = { item -> this@CollectionAdapter.missingTranslationListener(item) }
    }

  private fun ShowItem.displayTitle() = translation?.title?.takeIf { it.isNotBlank() } ?: show.title

  private fun ShowItem.displaySubtitle(): String =
    listOfNotNull(
      show.network.takeIf { it.isNotBlank() },
      show.year.takeIf { it > 0 }?.toString(),
    ).joinToString(" · ")
}
