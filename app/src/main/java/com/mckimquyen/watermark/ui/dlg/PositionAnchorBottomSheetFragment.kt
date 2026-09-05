package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.databinding.FPositionAnchorBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment

/**
 * Bottom sheet chọn preset neo watermark theo lưới 3x3 + slider margin (mở từ [com.mckimquyen.watermark.ui.panel.TileModeFragment]
 * khi tileMode = CLAMP). Tap 1 ô gọi thẳng [com.mckimquyen.watermark.ui.MainViewModel.selectAnchor],
 * vị trí thật được [com.mckimquyen.watermark.ui.widget.WaterMarkImageView.applyAnchor] tính toán
 * (cần biết kích thước watermark thật nên không tính ở đây).
 */
class PositionAnchorBottomSheetFragment : BaseBindBSDFragment<FPositionAnchorBottomSheetBinding>() {

    private val anchorButtons by lazy {
        mapOf(
            Anchor.TOP_LEFT to binding.btnAnchorTopLeft,
            Anchor.TOP_CENTER to binding.btnAnchorTopCenter,
            Anchor.TOP_RIGHT to binding.btnAnchorTopRight,
            Anchor.CENTER_LEFT to binding.btnAnchorCenterLeft,
            Anchor.CENTER to binding.btnAnchorCenter,
            Anchor.CENTER_RIGHT to binding.btnAnchorCenterRight,
            Anchor.BOTTOM_LEFT to binding.btnAnchorBottomLeft,
            Anchor.BOTTOM_CENTER to binding.btnAnchorBottomCenter,
            Anchor.BOTTOM_RIGHT to binding.btnAnchorBottomRight
        )
    }

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FPositionAnchorBottomSheetBinding {
        return FPositionAnchorBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        anchorButtons.forEach { (anchor, button) ->
            button.setOnClickListener {
                shareViewModel.selectAnchor(anchor)
                highlightAnchor(anchor)
            }
        }

        binding.slMargin.addOnChangeListener { _, value, fromUser ->
            binding.tvMarginValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) {
                shareViewModel.updateMarginPercent(value / 100f)
            }
        }

        shareViewModel.waterMark.observe(viewLifecycleOwner) {
            val waterMark = it ?: return@observe
            highlightAnchor(Anchor.obtain(waterMark.anchor))
            val marginPercentValue = (waterMark.marginPercent * 100f)
            if (binding.slMargin.value != marginPercentValue) {
                binding.slMargin.value = marginPercentValue
            }
            binding.tvMarginValue.text = getString(R.string.position_anchor_margin_value, marginPercentValue.toInt())
        }
    }

    private fun highlightAnchor(selected: Anchor) {
        anchorButtons.forEach { (anchor, button) ->
            setSelected(button, anchor == selected)
        }
    }

    private fun setSelected(button: MaterialButton, selected: Boolean) {
        val strokeColorRes = if (selected) R.color.glass_text_primary else R.color.glass_text_hint
        button.strokeColor = ContextCompat.getColorStateList(requireContext(), strokeColorRes)
        button.strokeWidth = ((if (selected) 2f else 1f) * resources.displayMetrics.density).toInt()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setOnShowListener {
                val bottomSheet = (this as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    val behavior = BottomSheetBehavior.from(it)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
        }
    }

    companion object {
        const val TAG = "PositionAnchorBottomSheetFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? PositionAnchorBottomSheetFragment
                when {
                    f == null -> {
                        PositionAnchorBottomSheetFragment().show(manager, TAG)
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
