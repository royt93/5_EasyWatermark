package com.mckimquyen.watermark.ui.dlg

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.databinding.DlgCardFrameBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * FEAT-28 Frame & Shadow Builder — bật/tắt khung thẻ + chỉnh bán kính bo góc, độ bóng, màu nền.
 * Chỉ áp lúc export (xem `BatchExportEngine`), nên preview editor không đổi.
 */
class CardFramePbFragment : BaseBindBSDFragment<DlgCardFrameBinding>() {

    /** BUG-69: observer bind UI không ghi ngược repo; accessibility click vẫn phải toggle config. */
    private var isBindingSwitch = false

    override fun bindView(layoutInflater: LayoutInflater, container: ViewGroup?): DlgCardFrameBinding =
        DlgCardFrameBinding.inflate(layoutInflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareViewModel.waterMark.observe(viewLifecycleOwner) { config ->
            if (config == null) return@observe
            bind(config)
        }

        binding.swCardFrame.setOnCheckedChangeListener { _, isChecked ->
            binding.groupCardCustomize.isVisible = isChecked
            if (!isBindingSwitch) shareViewModel.toggleCardFrame()
        }

        binding.slideCardCorner.addOnChangeListener { _, value, fromUser ->
            binding.tvCardCornerValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) shareViewModel.updateCardCornerRadiusPercent(value / PERCENT_BASE)
        }
        binding.slideCardShadow.addOnChangeListener { _, value, fromUser ->
            binding.tvCardShadowValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) shareViewModel.updateCardShadowPercent(value / PERCENT_BASE)
        }

        binding.flCardBackground.setOnClickListener { showBackgroundPicker() }
    }

    private fun bind(config: WaterMark) {
        isBindingSwitch = true
        try {
            if (binding.swCardFrame.isChecked != config.cardFrameEnabled) {
                binding.swCardFrame.isChecked = config.cardFrameEnabled
            }
            binding.groupCardCustomize.isVisible = config.cardFrameEnabled
        } finally {
            isBindingSwitch = false
        }

        val corner = config.cardCornerRadiusPercent * PERCENT_BASE
        if (binding.slideCardCorner.value != corner) binding.slideCardCorner.value = corner
        binding.tvCardCornerValue.text = getString(R.string.position_anchor_margin_value, corner.toInt())

        val shadow = config.cardShadowPercent * PERCENT_BASE
        if (binding.slideCardShadow.value != shadow) binding.slideCardShadow.value = shadow
        binding.tvCardShadowValue.text = getString(R.string.position_anchor_margin_value, shadow.toInt())

        // mutate() để không đổi màu constant state dùng chung với view khác cùng drawable.
        (binding.vCardBackgroundSwatch.background.mutate() as? GradientDrawable)?.setColor(config.cardBackgroundColor)
    }

    private fun showBackgroundPicker() {
        val activity = activity ?: return
        ColorPickerDialog.Builder(activity)
            .setTitle(getString(R.string.card_frame_background_color))
            .setPreferenceName(SP_CARD_BG_COLOR_PICKER)
            .setPositiveButton(
                getString(R.string.tips_confirm_dialog),
                object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.color?.let { shareViewModel.updateCardBackgroundColor(it) }
                    }
                }
            )
            .setNegativeButton(getString(R.string.tips_cancel_dialog)) { dialogInterface, _ -> dialogInterface.dismiss() }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(COLOR_PICKER_BOTTOM_SPACE)
            .show()
    }

    companion object {
        private const val TAG = "CardFramePbFragment"
        private const val SP_CARD_BG_COLOR_PICKER = "card_frame_bg_color_picker_dialog"
        private const val PERCENT_BASE = 100f
        private const val COLOR_PICKER_BOTTOM_SPACE = 20

        fun safetyShow(manager: FragmentManager) {
            try {
                if (manager.findFragmentByTag(TAG) == null) {
                    CardFramePbFragment().show(manager, TAG)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }
}
