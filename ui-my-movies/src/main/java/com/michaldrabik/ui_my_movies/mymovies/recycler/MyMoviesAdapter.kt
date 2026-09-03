package com.michaldrabik.ui_my_movies.mymovies.recycler

import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.ui_base.BaseAdapter
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
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type
import com.michaldrabik.ui_my_movies.mymovies.views.MyMovieAllView
import com.michaldrabik.ui_my_movies.mymovies.views.MyMovieHeaderView
import com.michaldrabik.ui_my_movies.mymovies.views.MyMoviesRecentsView

class MyMoviesAdapter(
  private val itemClickListener: (MyMoviesItem) -> Unit,
  private val itemLongClickListener: (MyMoviesItem) -> Unit,
  private val missingImageListener: (MyMoviesItem, Boolean) -> Unit,
  private val missingTranslationListener: (MyMoviesItem) -> Unit,
  private val onSortOrderClickListener: (SortOrder, SortType) -> Unit,
  private val onGenresClickListener: () -> Unit,
  private val onListViewModeClickListener: () -> Unit,
  listChangeListener: (() -> Unit),
) : BaseMovieAdapter<MyMoviesItem>(
    listChangeListener = listChangeListener,
  ) {

  companion object {
    private const val VIEW_TYPE_HEADER = 1
    private const val VIEW_TYPE_MOVIE_NORMAL = 2
    private const val VIEW_TYPE_RECENTS_SECTION = 3
    private const val VIEW_TYPE_MOVIE_COMPACT = 4
    private const val VIEW_TYPE_MOVIE_GRID = 5
  }

  init {
    stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
  }

  override val asyncDiffer = AsyncListDiffer(this, MyMoviesItemDiffCallback())

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
    VIEW_TYPE_HEADER -> BaseViewHolder(MyMovieHeaderView(parent.context))
    VIEW_TYPE_RECENTS_SECTION -> BaseViewHolder(MyMoviesRecentsView(parent.context))
    VIEW_TYPE_MOVIE_NORMAL -> BaseAdapter.BaseViewHolder(
      MyMovieAllView(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_MOVIE_COMPACT -> BaseAdapter.BaseViewHolder(
      MovieCompactItemView<MyMoviesItem>(parent.context).applyMediaListeners(),
    )
    VIEW_TYPE_MOVIE_GRID -> BaseAdapter.BaseViewHolder(
      MovieGridItemView<MyMoviesItem>(parent.context).applyMediaListeners(),
    )
    else -> throw IllegalStateException()
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int,
  ) {
    val item = asyncDiffer.currentList[position]
    when (holder.itemViewType) {
      VIEW_TYPE_HEADER -> (holder.itemView as MyMovieHeaderView).bind(
        item.header!!,
        listViewMode,
        onSortOrderClickListener,
        onGenresClickListener,
        onListViewModeClickListener,
      )
      VIEW_TYPE_RECENTS_SECTION -> (holder.itemView as MyMoviesRecentsView).bind(
        item.recentsSection!!,
        itemClickListener,
        itemLongClickListener,
      )
      VIEW_TYPE_MOVIE_NORMAL -> (holder.itemView as MyMovieAllView).bind(item)
      VIEW_TYPE_MOVIE_COMPACT -> {
        (holder.itemView as MovieCompactItemView<MyMoviesItem>).bind(
          item = item,
          title = item.displayTitle(),
          subtitle = item.movie.year
            .takeIf { it > 0 }
            ?.toString()
            .orEmpty(),
          translationMissing = item.translation == null,
        )
      }
      VIEW_TYPE_MOVIE_GRID -> {
        (holder.itemView as MovieGridItemView<MyMoviesItem>).bind(
          item = item,
          title = item.displayTitle(),
          translationMissing = item.translation == null,
        )
      }
    }
  }

  override fun getItemViewType(position: Int) =
    when (asyncDiffer.currentList[position].type) {
      Type.HEADER -> VIEW_TYPE_HEADER
      Type.RECENT_MOVIES -> VIEW_TYPE_RECENTS_SECTION
      Type.ALL_MOVIES_ITEM -> when (listViewMode) {
        LIST_NORMAL -> VIEW_TYPE_MOVIE_NORMAL
        LIST_COMPACT -> VIEW_TYPE_MOVIE_COMPACT
        GRID -> VIEW_TYPE_MOVIE_GRID
      }
    }

  private fun <T : MovieView<MyMoviesItem>> T.applyMediaListeners(): T =
    apply {
      itemClickListener = this@MyMoviesAdapter.itemClickListener
      itemLongClickListener = this@MyMoviesAdapter.itemLongClickListener
      missingImageListener = this@MyMoviesAdapter.missingImageListener
      missingTranslationListener = this@MyMoviesAdapter.missingTranslationListener
    }

  private fun MyMoviesItem.displayTitle() = translation?.title?.takeIf { it.isNotBlank() } ?: movie.title
}
