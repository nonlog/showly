package com.michaldrabik.ui_base.common.views.media

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.michaldrabik.ui_base.common.ListItem
import com.michaldrabik.ui_base.common.views.ShowView
import com.michaldrabik.ui_base.databinding.ViewMediaShowCompactBinding
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.onLongClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import java.util.Locale.ENGLISH

class ShowCompactItemView<Item : ListItem> : ShowView<Item> {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewMediaShowCompactBinding.inflate(LayoutInflater.from(context), this)

  override val imageView: ImageView = binding.mediaShowCompactImage
  override val placeholderView: ImageView = binding.mediaShowCompactPlaceholder

  private lateinit var item: Item

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    binding.mediaShowCompactRoot.onClick { itemClickListener?.invoke(item) }
    binding.mediaShowCompactRoot.onLongClick { itemLongClickListener?.invoke(item) }
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
      mediaShowCompactTitle.text = title
      mediaShowCompactSubtitle.text = subtitle
      mediaShowCompactRating.text = String.format(ENGLISH, "%.1f", item.show.rating)
      mediaShowCompactRating.visibleIf(item.show.rating > 0F)
      mediaShowCompactProgress.visibleIf(item.isLoading)
    }
    loadImage(item)
  }

  private fun clear() {
    with(binding) {
      mediaShowCompactTitle.text = ""
      mediaShowCompactSubtitle.text = ""
      mediaShowCompactRating.text = ""
      mediaShowCompactPlaceholder.gone()
      mediaShowCompactProgress.gone()
      Glide.with(this@ShowCompactItemView).clear(mediaShowCompactImage)
    }
  }
}
