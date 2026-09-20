package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
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

    private val anchorButtons
        get() = mapOf(
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

    private fun getAnchorNameRes(anchor: Anchor): Int = when (anchor) {
        Anchor.TOP_LEFT -> R.string.anchor_top_left
        Anchor.TOP_CENTER -> R.string.anchor_top_center
        Anchor.TOP_RIGHT -> R.string.anchor_top_right
        Anchor.CENTER_LEFT -> R.string.anchor_center_left
        Anchor.CENTER -> R.string.anchor_center
        Anchor.CENTER_RIGHT -> R.string.anchor_center_right
        Anchor.BOTTOM_LEFT -> R.string.anchor_bottom_left
        Anchor.BOTTOM_CENTER -> R.string.anchor_bottom_center
        Anchor.BOTTOM_RIGHT -> R.string.anchor_bottom_right
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

        val activeColor = com.google.android.material.color.MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorPrimary,
            android.graphics.Color.BLACK
        )
        val inactiveColor = com.google.android.material.color.MaterialColors.getColor(
            requireContext(),
            com.google.android.material.R.attr.colorSurfaceVariant,
            android.graphics.Color.LTGRAY
        )
        binding.slMargin.trackActiveTintList = android.content.res.ColorStateList.valueOf(activeColor)
        binding.slMargin.trackInactiveTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
        binding.slMargin.thumbTintList = android.content.res.ColorStateList.valueOf(activeColor)

        binding.slMargin.contentDescription = getString(R.string.position_anchor_margin)
        binding.slMargin.addOnChangeListener { _, value, fromUser ->
            val formatted = getString(R.string.position_anchor_margin_value, value.toInt())
            binding.tvMarginValue.text = formatted
            binding.slMargin.contentDescription = "${getString(R.string.position_anchor_margin)}: $formatted"
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
            val formatted = getString(R.string.position_anchor_margin_value, marginPercentValue.toInt())
            binding.tvMarginValue.text = formatted
            binding.slMargin.contentDescription = "${getString(R.string.position_anchor_margin)}: $formatted"
        }
    }

    private fun highlightAnchor(selected: Anchor) {
        anchorButtons.forEach { (anchor, button) ->
            setSelected(button, anchor, anchor == selected)
        }
    }

    private fun setSelected(button: MaterialButton, anchor: Anchor, selected: Boolean) {
        val baseName = getString(getAnchorNameRes(anchor))
        button.contentDescription = if (selected) {
            getString(R.string.anchor_selected_format, baseName)
        } else {
            baseName
        }
        val strokeColor = if (selected) {
            com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimary)
        } else {
            com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutlineVariant)
        }
        val bgColor = if (selected) {
            com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer)
        } else {
            android.graphics.Color.TRANSPARENT
        }
        val textColor = if (selected) {
            com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimaryContainer)
        } else {
            com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface)
        }
        button.setTextColor(textColor)
        button.iconTint = android.content.res.ColorStateList.valueOf(textColor)
        button.strokeColor = android.content.res.ColorStateList.valueOf(strokeColor)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        button.strokeWidth = ((if (selected) 2f else 1f) * resources.displayMetrics.density).toInt()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (this is com.google.android.material.bottomsheet.BottomSheetDialog) {
                setupBottomSheet(this, expandFully = false)
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
