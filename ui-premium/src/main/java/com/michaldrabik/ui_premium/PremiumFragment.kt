package com.michaldrabik.ui_premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.michaldrabik.repository.PremiumProduct
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_premium.databinding.FragmentPremiumBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PremiumFragment : BaseFragment<PremiumViewModel>(R.layout.fragment_premium) {

  override val viewModel by viewModels<PremiumViewModel>()
  private val binding by viewBinding(FragmentPremiumBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.load() },
    )
  }

  private fun setupView() {
    with(binding) {
      premiumToolbar.setNavigationOnClickListener { activity?.onBackPressed() }
      premiumRoot.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = padding.top + inset)
      }
    }
  }

  private fun render(uiState: PremiumUiState) {
    binding.premiumProgress.visibleIf(uiState.isLoading)
    renderProducts(uiState)
  }

  private fun renderProducts(uiState: PremiumUiState) {
    with(binding.premiumPurchaseItems) {
      removeAllViews()
      val status = TextView(requireContext()).apply {
        text = when {
          uiState.isPremium -> getString(R.string.textPremiumActive)
          uiState.message != null -> uiState.message
          uiState.products.isEmpty() && !uiState.isLoading -> getString(R.string.textPremiumProductsUnavailable)
          else -> getString(R.string.textPremiumForkSupport)
        }
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setPadding(16, 16, 16, 16)
      }
      addView(status)
      uiState.products.forEach { addView(createProductCard(it)) }
      val restore = MaterialButton(requireContext()).apply {
        setText(R.string.textPremiumRestore)
        isEnabled = !uiState.isLoading
        setOnClickListener { viewModel.restore() }
      }
      addView(restore, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
  }

  private fun createProductCard(product: PremiumProduct): View {
    val card = MaterialCardView(requireContext())
    LayoutInflater.from(requireContext()).inflate(R.layout.view_purchase_item, card, true)
    card.findViewById<TextView>(R.id.viewPurchaseItemTitle).text = product.title
    card.findViewById<TextView>(R.id.viewPurchaseItemDescription).text = product.description
    card.findViewById<TextView>(R.id.viewPurchaseItemDescriptionDetails).text = getString(R.string.textPremiumProductSupport)
    card.findViewById<TextView>(R.id.viewPurchaseItemPrice).text = product.price
    card.setOnClickListener { viewModel.purchase(requireActivity(), product.id) }
    card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    return card
  }
}
