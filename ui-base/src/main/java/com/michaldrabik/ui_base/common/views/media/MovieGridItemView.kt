package com.michaldrabik.ui_base.common.views.media

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.michaldrabik.ui_base.common.MovieListItem
import com.michaldrabik.ui_base.common.views.MovieView
import com.michaldrabik.ui_base.databinding.ViewMediaMovieGridBinding
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.onLongClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf

class MovieGridItemView<Item : MovieListItem> : MovieView<Item> {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewMediaMovieGridBinding.inflate(LayoutInflater.from(context), this)

  override val imageView: ImageView = binding.mediaMovieGridImage
  override val placeholderView: ImageView = binding.mediaMovieGridPlaceholder

  private lateinit var item: Item

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    binding.mediaMovieGridRoot.onClick { itemClickListener?.invoke(item) }
    binding.mediaMovieGridRoot.onLongClick { itemLongClickListener?.invoke(item) }
  }

  fun bind(
    item: Item,
    title: String,
    translationMissing: Boolean,
  ) {
    clear()
    this.item = item
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    imageLoadCompleteListener = {
      if (translationMissing) missingTranslationListener?.invoke(item)
    }
    with(binding) {
      mediaMovieGridTitle.text = title
      mediaMovieGridProgress.visibleIf(item.isLoading)
    }
    loadImage(item)
  }

  private fun clear() {
    with(binding) {
      mediaMovieGridTitle.text = ""
      mediaMovieGridPlaceholder.gone()
      mediaMovieGridProgress.gone()
      Glide.with(this@MovieGridItemView).clear(mediaMovieGridImage)
    }
  }
}
