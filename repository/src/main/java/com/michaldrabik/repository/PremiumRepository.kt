package com.michaldrabik.repository

import android.app.Activity
import com.michaldrabik.repository.settings.SettingsRepository
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QPurchaseResult
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.entitlements.QEntitlement
import com.qonversion.android.sdk.dto.offerings.QOfferings
import com.qonversion.android.sdk.dto.products.QProduct
import com.qonversion.android.sdk.listeners.QonversionEntitlementsCallback
import com.qonversion.android.sdk.listeners.QonversionOfferingsCallback
import com.qonversion.android.sdk.listeners.QonversionPurchaseCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PremiumProduct(
  val id: String,
  val title: String,
  val description: String,
  val price: String,
)

@Singleton
class PremiumRepository @Inject constructor(
  private val settingsRepository: SettingsRepository,
) {
  companion object {
    const val ENTITLEMENT_ID = "premium_access"
  }

  private val products = mutableMapOf<String, QProduct>()

  val isPremium: Boolean
    get() = settingsRepository.premium.isPremium

  suspend fun refresh(): Boolean =
    suspendCancellableCoroutine { continuation ->
      Qonversion.shared.checkEntitlements(
        object : QonversionEntitlementsCallback {
          override fun onSuccess(entitlements: Map<String, QEntitlement>) {
            if (continuation.isActive) continuation.resume(updateEntitlement(entitlements))
          }

          override fun onError(error: QonversionError) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Unable to refresh Premium status"))
          }
        },
      )
    }

  suspend fun loadProducts(): List<PremiumProduct> =
    suspendCancellableCoroutine { continuation ->
      Qonversion.shared.offerings(
        object : QonversionOfferingsCallback {
          override fun onSuccess(offerings: QOfferings) {
            val available = offerings.main?.products ?: offerings.availableOfferings.firstOrNull()?.products.orEmpty()
            products.clear()
            available.forEach { products[it.qonversionId] = it }
            val mapped = available.map { product ->
              PremiumProduct(
                id = product.qonversionId,
                title = product.storeDetails?.name ?: product.qonversionId,
                description = product.storeDetails?.description.orEmpty(),
                price = product.prettyPrice.orEmpty(),
              )
            }
            if (continuation.isActive) continuation.resume(mapped)
          }

          override fun onError(error: QonversionError) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Unable to load Premium products"))
          }
        },
      )
    }

  suspend fun restore(): Boolean =
    suspendCancellableCoroutine { continuation ->
      Qonversion.shared.restore(
        object : QonversionEntitlementsCallback {
          override fun onSuccess(entitlements: Map<String, QEntitlement>) {
            if (continuation.isActive) continuation.resume(updateEntitlement(entitlements))
          }

          override fun onError(error: QonversionError) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Unable to restore Premium purchases"))
          }
        },
      )
    }

  suspend fun purchase(activity: Activity, productId: String): Boolean {
    val product = products[productId] ?: return false
    return suspendCancellableCoroutine { continuation ->
      Qonversion.shared.purchase(
        activity,
        product,
        object : QonversionPurchaseCallback {
          override fun onResult(result: QPurchaseResult) {
            if (!continuation.isActive) return
            when {
              result.isSuccessful -> continuation.resume(updateEntitlement(result.entitlements))
              result.isCanceledByUser -> continuation.resume(false)
              result.isPending -> continuation.resume(false)
              else -> continuation.resumeWithException(IllegalStateException("Premium purchase failed"))
            }
          }
        },
      )
    }
  }

  fun identify(userId: String) {
    if (userId.isBlank()) return
    runCatching { Qonversion.shared.identify(userId) }
  }

  fun logout() {
    runCatching { Qonversion.shared.logout() }
  }

  private fun updateEntitlement(entitlements: Map<String, QEntitlement>): Boolean {
    val wasPremium = settingsRepository.premium.isPremium
    val active = entitlements[ENTITLEMENT_ID]?.isActive == true
    settingsRepository.premium.isPremium = active
    settingsRepository.premium.showPremiumExpired = wasPremium && !active
    if (active) settingsRepository.premium.showPaywall = false
    return active
  }
}
