package com.michaldrabik.ui_premium

import com.michaldrabik.repository.PremiumProduct

data class PremiumUiState(
  val isLoading: Boolean = false,
  val isPremium: Boolean = false,
  val products: List<PremiumProduct> = emptyList(),
  val message: String? = null,
)
