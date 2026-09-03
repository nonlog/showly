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
import com.michaldrabik.ui_base.databinding.ViewMediaShowGridBinding
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.onLongClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf

class ShowGridItemView<Item : ListItem> : ShowView<Item> {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewMediaShowGridBinding.inflate(LayoutInflater.from(context), this)

  override val imageView: ImageView = binding.mediaShowGridImage
  override val placeholderView: ImageView = binding.mediaShowGridPlaceholder

  private lateinit var item: Item

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    binding.mediaShowGridRoot.onClick { itemClickListener?.invoke(item) }
    binding.mediaShowGridRoot.onLongClick { itemLongClickListener?.invoke(item) }
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
      mediaShowGridTitle.text = title
      mediaShowGridProgress.visibleIf(item.isLoading)
    }
    loadImage(item)
  }

  private fun clear() {
    with(binding) {
      mediaShowGridTitle.text = ""
      mediaShowGridPlaceholder.gone()
      mediaShowGridProgress.gone()
      Glide.with(this@ShowGridItemView).clear(mediaShowGridImage)
    }
  }
}
