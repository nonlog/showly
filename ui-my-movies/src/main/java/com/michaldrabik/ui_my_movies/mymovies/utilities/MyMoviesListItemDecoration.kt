package com.michaldrabik.ui_my_movies.mymovies.utilities

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.michaldrabik.ui_base.common.views.media.MovieCompactItemView
import com.michaldrabik.ui_base.common.views.media.MovieGridItemView
import com.michaldrabik.ui_my_movies.mymovies.views.MyMovieAllView
import com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesAdapter

class MyMoviesListItemDecoration(
  context: Context,
  @DimenRes spacingDimen: Int,
) : ItemDecoration() {

  private val spacing = context.resources.getDimensionPixelSize(spacingDimen)

  override fun getItemOffsets(
    outRect: Rect,
    view: View,
    parent: RecyclerView,
    state: RecyclerView.State,
  ) {
    if (!isMediaView(view)) return

    outRect.top = spacing
    outRect.bottom = spacing

    when (val manager = parent.layoutManager) {
      is GridLayoutManager -> {
        val totalSpan = manager.spanCount
        val column = getPosition(parent, view).coerceAtLeast(0) % totalSpan
        outRect.left = (spacing * 2) * column / totalSpan
        outRect.right = (spacing * 2) * ((totalSpan - 1) - column) / totalSpan
      }
      is LinearLayoutManager -> {
        outRect.left = 0
        outRect.right = 0
      }
    }
  }

  private fun isMediaView(view: View) =
    view is MyMovieAllView ||
      view is MovieCompactItemView<*> ||
      view is MovieGridItemView<*>

  private fun getPosition(
    parent: RecyclerView,
    view: View,
  ): Int {
    val nonMediaItemCount = (parent.adapter as MyMoviesAdapter)
      .getItems()
      .count { it.type != com.michaldrabik.ui_my_movies.mymovies.recycler.MyMoviesItem.Type.ALL_MOVIES_ITEM }
    return parent.getChildAdapterPosition(view) - nonMediaItemCount
  }
}
