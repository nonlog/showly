package com.michaldrabik.ui_premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.michaldrabik.repository.PremiumRepository
import com.michaldrabik.repository.UserTraktManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
  private val premiumRepository: PremiumRepository,
  private val userTraktManager: UserTraktManager,
) : ViewModel() {

  private val state = MutableStateFlow(PremiumUiState(isLoading = true, isPremium = premiumRepository.isPremium))
  val uiState = state.asStateFlow()

  fun load() {
    viewModelScope.launch {
      state.value = state.value.copy(isLoading = true, message = null)
      runCatching {
        userTraktManager.getUsername().takeIf { it.isNotBlank() }?.let(premiumRepository::identify)
        val active = premiumRepository.refresh()
        val products = if (active) emptyList() else premiumRepository.loadProducts()
        state.value = PremiumUiState(isLoading = false, isPremium = active, products = products)
      }.onFailure {
        state.value = state.value.copy(isLoading = false, message = "Premium status is temporarily unavailable.")
      }
    }
  }

  fun restore() {
    viewModelScope.launch {
      state.value = state.value.copy(isLoading = true, message = null)
      runCatching { premiumRepository.restore() }
        .onSuccess { active -> state.value = state.value.copy(isLoading = false, isPremium = active, message = if (active) "Premium purchases restored." else "No active Premium purchase was found.") }
        .onFailure { state.value = state.value.copy(isLoading = false, message = "Unable to restore Premium purchases.") }
    }
  }

  fun purchase(activity: Activity, productId: String) {
    viewModelScope.launch {
      state.value = state.value.copy(isLoading = true, message = null)
      runCatching { premiumRepository.purchase(activity, productId) }
        .onSuccess { active -> state.value = state.value.copy(isLoading = false, isPremium = active, message = if (active) "Premium is active. Thank you for supporting Showly." else null) }
        .onFailure { state.value = state.value.copy(isLoading = false, message = "Unable to complete Premium purchase.") }
    }
  }
}
