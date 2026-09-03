package com.michaldrabik.ui_my_shows.myshows.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.BaseAdapter
import com.michaldrabik.ui_base.common.ListItem
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_base.common.ListViewMode.GRID
import com.michaldrabik.ui_base.common.ListViewMode.LIST_COMPACT
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_base.common.views.media.ShowCompactItemView
import com.michaldrabik.ui_base.common.views.media.ShowGridItemView
import com.michaldrabik.ui_model.MyShowsSection
import com.michaldrabik.ui_model.SortOrder
import com.michaldrabik.ui_model.SortType
import com.michaldrabik.ui_my_shows.myshows.recycler.MyShowsItem.Type
import com.michaldrabik.ui_my_shows.myshows.views.MyShowAllView
import com.michaldrabik.ui_my_shows.myshows.views.MyShowHeaderView
import com.michaldrabik.ui_my_shows.myshows.views.MyShowsRecentsView

class MyShowsAdapter(
  private val itemClickListener: (ListItem) -> Unit,
  private val itemLongClickListener: (ListItem) -> Unit,
  private val onSortOrderClickListener: (MyShowsSection, SortOrder, SortType) -> Unit,
  private val onListViewModeClickListener: () -> Unit,
  private val onNetworksClickListener: () -> Unit,
  private val onGenresClickListener: () -> Unit,
  private val onTypeClickListener: () -> Unit,
  private val missingImageListener: (ListItem, Boolean) -> Unit,
  private val missingTranslationListener: (ListItem) -> Unit,
  listChangeListener: () -> Unit,
) : BaseAdapter<MyShowsItem>(
    listChangeListener = listChangeListener,
  ) {

  companion object {
    private const val VIEW_TYPE_HEADER = 1
    private const val VIEW_TYPE_SHOW_NORMAL = 2
    private const val VIEW_TYPE_RECENTS_SECTION = 3
    private const val VIEW_TYPE_SHOW_COMPACT = 4
    private const val VIEW_TYPE_SHOW_GRID = 5
  }

  override val asyncDiffer = AsyncListDiffer(this, MyShowsItemDiffCallback())

  var listViewMode: ListViewMode = LIST_NORMAL
    set(value) {
      if (field == value) return
      field = value
      notifyDataSetChanged()
    }

  fun setItems(
    newItems: List<MyShowsItem>,
    notifyChangeList: List<Type>?,
  ) {
    val notifyChange = notifyChangeList?.contains(Type.ALL_SHOWS_ITEM) == true
    super.setItems(newItems, notifyChange)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ) = when (viewType) {
    VIEW_TYPE_HEADER -> BaseViewHolder(MyShowHeaderView(parent.context))
    VIEW_TYPE_RECENTS_SECTION -> BaseViewHolder(MyShowsRecentsView(parent.context))
    VIEW_TYPE_SHOW_NORMAL -> BaseViewHolder(
      MyShowAllView(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_SHOW_COMPACT -> BaseViewHolder(
      ShowCompactItemView<MyShowsItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_SHOW_GRID -> BaseViewHolder(
      ShowGridItemView<MyShowsItem>(parent.context).applyMediaListeners(),
    )
    else -> throw IllegalStateException()
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    val item = asyncDiffer.currentList[position]
    when (holder.itemViewType) {
      VIEW_TYPE_HEADER -> {
        (holder.itemView as MyShowHeaderView).bind(
          item = item.header!!,
          viewMode = listViewMode,
          typeClickListener = onTypeClickListener,
          sortClickListener = onSortOrderClickListener,
          networksClickListener = onNetworksClickListener,
          genresClickListener = onGenresClickListener,
          listModeClickListener = onListViewModeClickListener,
        )
      }
      VIEW_TYPE_RECENTS_SECTION -> {
        (holder.itemView as MyShowsRecentsView).bind(
          item.recentsSection!!,
          itemClickListener,
          itemLongClickListener,
        )
      }
      VIEW_TYPE_SHOW_NORMAL -> (holder.itemView as MyShowAllView).bind(item)
      VIEW_TYPE_SHOW_COMPACT -> {
        (holder.itemView as ShowCompactItemView<MyShowsItem>).bind(
          item = item,
          title = item.displayTitle(),
          subtitle = item.displaySubtitle(),
          translationMissing = item.translation == null,
        )
      }
      VIEW_TYPE_SHOW_GRID -> {
        (holder.itemView as ShowGridItemView<MyShowsItem>).bind(
          item = item,
          title = item.displayTitle(),
          translationMissing = item.translation == null,
        )
      }
    }
  }

  override fun getItemViewType(position: Int) =
    when (asyncDiffer.currentList[position].type) {
      Type.ALL_SHOWS_HEADER -> VIEW_TYPE_HEADER
      Type.RECENT_SHOWS -> VIEW_TYPE_RECENTS_SECTION
      Type.ALL_SHOWS_ITEM -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_SHOW_NORMAL
        LIST_COMPACT -> VIEW_TYPE_SHOW_COMPACT
        GRID -> VIEW_TYPE_SHOW_GRID
      }
    }

  private fun <T : com.michaldrabik.ui_base.common.views.ShowView<MyShowsItem>> T.applyMediaListeners(): T =
    apply {
      itemClickListener = { item -> this@MyShowsAdapter.itemClickListener(item) }
      itemLongClickListener = { item -> this@MyShowsAdapter.itemLongClickListener(item) }
      missingImageListener = { item, force -> this@MyShowsAdapter.missingImageListener(item, force) }
      missingTranslationListener = { item -> this@MyShowsAdapter.missingTranslationListener(item) }
    }

  private fun MyShowsItem.displayTitle() = translation?.title?.takeIf { it.isNotBlank() } ?: show.title

  private fun MyShowsItem.displaySubtitle(): String =
    listOfNotNull(
      show.network.takeIf { it.isNotBlank() },
      show.year.takeIf { it > 0 }?.toString(),
    ).joinToString(" · ")
}
