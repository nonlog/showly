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
import com.michaldrabik.ui_base.databinding.ViewMediaMovieCompactBinding
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.onLongClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import java.util.Locale.ENGLISH

class MovieCompactItemView<Item : MovieListItem> : MovieView<Item> {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewMediaMovieCompactBinding.inflate(LayoutInflater.from(context), this)

  override val imageView: ImageView = binding.mediaMovieCompactImage
  override val placeholderView: ImageView = binding.mediaMovieCompactPlaceholder

  private lateinit var item: Item

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    binding.mediaMovieCompactRoot.onClick { itemClickListener?.invoke(item) }
    binding.mediaMovieCompactRoot.onLongClick { itemLongClickListener?.invoke(item) }
  }

  fun bind(
    item: Item,
    title: String,
    subtitle: String,
    translationMissing: Boolean,
  ) {
    clear()
    this.item = item
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    imageLoadCompleteListener = {
      if (translationMissing) missingTranslationListener?.invoke(item)
    }
    with(binding) {
      mediaMovieCompactTitle.text = title
      mediaMovieCompactSubtitle.text = subtitle
      mediaMovieCompactRating.text = String.format(ENGLISH, "%.1f", item.movie.rating)
      mediaMovieCompactRating.visibleIf(item.movie.rating > 0F)
      mediaMovieCompactProgress.visibleIf(item.isLoading)
    }
    loadImage(item)
  }

  private fun clear() {
    with(binding) {
      mediaMovieCompactTitle.text = ""
      mediaMovieCompactSubtitle.text = ""
      mediaMovieCompactRating.text = ""
      mediaMovieCompactPlaceholder.gone()
      mediaMovieCompactProgress.gone()
      Glide.with(this@MovieCompactItemView).clear(mediaMovieCompactImage)
    }
  }
}
