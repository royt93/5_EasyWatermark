package com.mckimquyen.watermark.ui.dlg

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FCompareBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment

/**
 * FEAT-18: slider so sánh trước/sau trong editor — kéo đổi [com.mckimquyen.watermark.ui.MainViewModel.compareReveal]
 * (state UI thuần, không ghi DataStore), `MainActivity` quan sát và áp trực tiếp vào
 * `WaterMarkImageView.compareRevealFraction` để vẽ lại live. Đóng sheet (bất kỳ cách nào — vuốt
 * xuống, back, hay đóng lập trình) đều reset về 1f (hiện watermark đầy đủ) qua [onDismiss].
 */
class CompareBottomSheetFragment : BaseBindBSDFragment<FCompareBottomSheetBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FCompareBottomSheetBinding {
        return FCompareBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initial = shareViewModel.compareReveal.value ?: 1f
        binding.slCompare.value = initial * 100f
        binding.tvValue.text = getString(R.string.position_anchor_margin_value, (initial * 100f).toInt())
        binding.slCompare.addOnChangeListener { _, value, fromUser ->
            binding.tvValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) {
                shareViewModel.updateCompareReveal(value / 100f)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        shareViewModel.updateCompareReveal(1f)
    }

    companion object {
        const val TAG = "CompareBottomSheetFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? CompareBottomSheetFragment
                when {
                    f == null -> {
                        CompareBottomSheetFragment().show(manager, TAG)
                    }
                    !f.isAdded -> {
                        f.show(manager, TAG)
                    }
                }
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
