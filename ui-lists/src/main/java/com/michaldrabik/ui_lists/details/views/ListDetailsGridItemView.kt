package com.michaldrabik.ui_lists.details.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_lists.R
import com.michaldrabik.ui_lists.databinding.ViewListDetailsGridItemBinding
import com.michaldrabik.ui_lists.details.recycler.ListDetailsItem
import kotlin.math.abs

class ListDetailsGridItemView : ListDetailsItemView {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private val binding = ViewListDetailsGridItemBinding.inflate(LayoutInflater.from(context), this)

  override val imageView: ImageView = binding.listDetailsGridImage
  override val placeholderView: ImageView = binding.listDetailsGridPlaceholder

  init {
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    binding.listDetailsGridRoot.onClick { if (item.isEnabled) itemClickListener?.invoke(item) }
    setupSwipe()
  }

  override fun bind(item: ListDetailsItem) {
    super.bind(item)
    clear()
    imageLoadCompleteListener = {
      if (item.translation == null) missingTranslationListener?.invoke(item)
    }
    with(binding) {
      listDetailsGridTitle.text = item.displayTitle()
      listDetailsGridProgress.visibleIf(item.isLoading)
      listDetailsGridPlaceholder.setImageResource(if (item.isShow()) R.drawable.ic_television else R.drawable.ic_film)
      listDetailsGridRoot.alpha = if (item.isEnabled) 1F else 0.45F
    }
    loadImage(item)
  }

  private fun ListDetailsItem.displayTitle(): String {
    val translated = translation?.title?.takeIf { it.isNotBlank() }
    return translated ?: if (isShow()) requireShow().title else requireMovie().title
  }

  private fun setupSwipe() {
    var startX = 0F
    binding.listDetailsGridRoot.setOnTouchListener { _, event ->
      when (event.action) {
        ACTION_DOWN -> startX = event.x
        ACTION_UP -> startX = 0F
        ACTION_MOVE -> if (abs(startX - event.x) > 50F) {
          itemSwipeStartListener?.invoke()
          return@setOnTouchListener true
        }
      }
      false
    }
  }

  private fun clear() {
    with(binding) {
      listDetailsGridTitle.text = ""
      listDetailsGridPlaceholder.gone()
      listDetailsGridProgress.gone()
      Glide.with(this@ListDetailsGridItemView).clear(listDetailsGridImage)
    }
  }
}
