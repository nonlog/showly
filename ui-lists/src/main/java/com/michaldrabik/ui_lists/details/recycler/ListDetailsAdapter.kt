package com.michaldrabik.ui_lists.details.recycler

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.common.ListViewMode
import com.michaldrabik.ui_base.common.ListViewMode.GRID
import com.michaldrabik.ui_base.common.ListViewMode.LIST_COMPACT
import com.michaldrabik.ui_base.common.ListViewMode.LIST_NORMAL
import com.michaldrabik.ui_lists.details.helpers.ListItemDragListener
import com.michaldrabik.ui_lists.details.helpers.ListItemSwipeListener
import com.michaldrabik.ui_lists.details.helpers.ReorderListCallbackAdapter
import com.michaldrabik.ui_lists.details.views.ListDetailsCompactItemView
import com.michaldrabik.ui_lists.details.views.ListDetailsGridItemView
import com.michaldrabik.ui_lists.details.views.ListDetailsItemView
import com.michaldrabik.ui_lists.details.views.ListDetailsMovieItemView
import com.michaldrabik.ui_lists.details.views.ListDetailsShowItemView
import java.util.Collections

class ListDetailsAdapter(
  val itemClickListener: (ListDetailsItem) -> Unit,
  val missingImageListener: (ListDetailsItem, Boolean) -> Unit,
  val missingTranslationListener: (ListDetailsItem) -> Unit,
  val itemsChangedListener: () -> Unit,
  val itemsClearedListener: (List<ListDetailsItem>) -> Unit,
  val itemsSwipedListener: (ListDetailsItem) -> Unit,
  val itemDragStartListener: ListItemDragListener,
  val itemSwipeStartListener: ListItemSwipeListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
  ReorderListCallbackAdapter {

  companion object {
    private const val VIEW_TYPE_SHOW_NORMAL = 1
    private const val VIEW_TYPE_MOVIE_NORMAL = 2
    private const val VIEW_TYPE_SHOW_COMPACT = 3
    private const val VIEW_TYPE_MOVIE_COMPACT = 4
    private const val VIEW_TYPE_SHOW_GRID = 5
    private const val VIEW_TYPE_MOVIE_GRID = 6
  }

  var items = listOf<ListDetailsItem>()

  var listViewMode: ListViewMode = LIST_NORMAL
    set(value) {
      if (field == value) return
      field = value
      notifyDataSetChanged()
    }

  fun setItems(
    newItems: List<ListDetailsItem>,
    notifyItemsChange: Boolean,
  ) {
    val diff = DiffUtil.calculateDiff(ListDetailsDiffCallback(items, newItems))
    diff.dispatchUpdatesTo(this)
    items = newItems
    if (notifyItemsChange) itemsChangedListener.invoke()
  }

  override fun getItemViewType(position: Int): Int {
    val item = items[position]
    return when {
      item.isShow() -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_SHOW_NORMAL
        LIST_COMPACT -> VIEW_TYPE_SHOW_COMPACT
        GRID -> VIEW_TYPE_SHOW_GRID
      }
      item.isMovie() -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_MOVIE_NORMAL
        LIST_COMPACT -> VIEW_TYPE_MOVIE_COMPACT
        GRID -> VIEW_TYPE_MOVIE_GRID
      }
      else -> throw IllegalStateException()
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ): RecyclerView.ViewHolder {
    val view = when (viewType) {
      VIEW_TYPE_SHOW_NORMAL -> ListDetailsShowItemView(parent.context)
      VIEW_TYPE_MOVIE_NORMAL -> ListDetailsMovieItemView(parent.context)
      VIEW_TYPE_SHOW_COMPACT, VIEW_TYPE_MOVIE_COMPACT -> ListDetailsCompactItemView(parent.context)
      VIEW_TYPE_SHOW_GRID, VIEW_TYPE_MOVIE_GRID -> ListDetailsGridItemView(parent.context)
      else -> throw IllegalStateException()
    }.apply {
      itemClickListener = { item -> this@ListDetailsAdapter.itemClickListener(item) }
      missingImageListener = { item, force -> this@ListDetailsAdapter.missingImageListener(item, force) }
      missingTranslationListener = { item -> this@ListDetailsAdapter.missingTranslationListener(item) }
    }
    return ListDetailsItemViewHolder(view, itemDragStartListener, itemSwipeStartListener)
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    val item = items[position]
    when (holder.itemViewType) {
      VIEW_TYPE_SHOW_NORMAL -> (holder.itemView as ListDetailsShowItemView).bind(item)
      VIEW_TYPE_MOVIE_NORMAL -> (holder.itemView as ListDetailsMovieItemView).bind(item)
      VIEW_TYPE_SHOW_COMPACT, VIEW_TYPE_MOVIE_COMPACT -> (holder.itemView as ListDetailsCompactItemView).bind(item)
      VIEW_TYPE_SHOW_GRID, VIEW_TYPE_MOVIE_GRID -> (holder.itemView as ListDetailsGridItemView).bind(item)
      else -> throw IllegalStateException()
    }
  }

  override fun getItemCount() = items.size

  override fun onItemMove(
    fromPosition: Int,
    toPosition: Int,
  ): Boolean {
    if (fromPosition < toPosition) {
      for (i in fromPosition until toPosition) {
        Collections.swap(items, i, i + 1)
      }
    } else {
      for (i in fromPosition downTo toPosition + 1) {
        Collections.swap(items, i, i - 1)
      }
    }
    notifyItemMoved(fromPosition, toPosition)
    return true
  }

  override fun onItemCleared() = itemsClearedListener(items)

  override fun onItemSwiped(viewHolder: RecyclerView.ViewHolder) {
    val item = ((viewHolder as ListDetailsItemViewHolder).itemView as ListDetailsItemView).item
    itemsSwipedListener(item)
  }

  @SuppressLint("ClickableViewAccessibility")
  class ListDetailsItemViewHolder(
    itemView: ListDetailsItemView,
    dragStartListener: ListItemDragListener,
    swipeStartListener: ListItemSwipeListener,
  ) : RecyclerView.ViewHolder(itemView) {
    init {
      itemView.itemDragStartListener = {
        dragStartListener.onListItemDragStarted(this)
      }
      itemView.itemSwipeStartListener = {
        swipeStartListener.onListItemSwipeStarted(this)
      }
    }
  }
}
