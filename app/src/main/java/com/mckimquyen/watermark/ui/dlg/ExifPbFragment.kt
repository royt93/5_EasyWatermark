package com.mckimquyen.watermark.ui.dlg

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.databinding.DlgExifBorderBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * FEAT-14 Custom Frame Builder — nhóm control `groupCustomize` (band color/thickness/serif
 * caption) cho phép tuỳ chỉnh nhẹ lên style EXIF đang chọn. Tái dùng [ColorPickerDialog] (cùng
 * thư viện `ColorFragment` đang dùng cho màu watermark text) thay vì nhúng lại `ColorFragment`
 * (vốn thiết kế để replace vào container full-screen, không hợp bottom sheet ở đây).
 */
class ExifPbFragment : BaseBindBSDFragment<DlgExifBorderBinding>() {

    // BUG-18: KHÔNG dùng `by lazy` — nó cache vĩnh viễn theo instance Fragment, trong khi
    // BottomSheetDialogFragment có thể tái tạo View (onCreateView gọi lại) nhiều lần trong cùng
    // 1 Fragment instance (xoay màn hình, dialog bị hệ thống tái tạo). Property getter thuần
    // luôn đọc từ `binding` hiện tại — không giữ tham chiếu tới View đã bị gỡ khỏi hierarchy.
    private val styleButtons
        get() = mapOf(
            ExifFrameStyle.CLASSIC to binding.btnStyleClassic,
            ExifFrameStyle.POLAROID to binding.btnStylePolaroid,
            ExifFrameStyle.FILM_STRIP to binding.btnStyleFilmStrip,
            ExifFrameStyle.MINIMAL to binding.btnStyleMinimal
        )

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgExifBorderBinding {
        return DlgExifBorderBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        styleButtons.forEach { (style, button) ->
            button.setOnClickListener {
                shareViewModel.selectExifFrameStyle(style)
                highlightStyle(style)
            }
        }

        shareViewModel.waterMark.observe(viewLifecycleOwner) { config ->
            if (config == null) return@observe
            if (binding.swExif.isChecked != config.enableExif) {
                binding.swExif.isChecked = config.enableExif
            }
            binding.groupFrameStyle.isVisible = config.enableExif
            binding.groupCustomize.isVisible = config.enableExif
            highlightStyle(ExifFrameStyle.obtain(config.exifFrameStyle))
            bindCustomizeValues(config, ExifFrameStyle.obtain(config.exifFrameStyle))
        }

        binding.swExif.setOnCheckedChangeListener { buttonView, isChecked ->
            binding.groupFrameStyle.isVisible = isChecked
            binding.groupCustomize.isVisible = isChecked
            if (buttonView.isPressed) {
                shareViewModel.toggleExifBorder()
            }
        }

        binding.vExifBandColorSwatch.setOnClickListener { showBandColorPicker() }

        binding.slideExifBandThickness.addOnChangeListener { _, value, fromUser ->
            binding.tvExifBandThicknessValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) {
                shareViewModel.updateExifBandThicknessPercent(value / 100f)
            }
        }

        binding.swExifSerifCaption.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                shareViewModel.updateExifUseSerifCaption(isChecked)
            }
        }

        binding.tvExifCustomizeReset.setOnClickListener {
            shareViewModel.resetExifCustomization()
        }
    }

    private fun highlightStyle(selected: ExifFrameStyle) {
        ExifFrameStyleHighlighter.apply(styleButtons, selected)
    }

    /** FEAT-14 — đổ giá trị đang lưu (hoặc mặc định của style nếu user chưa tuỳ chỉnh) lên UI. */
    private fun bindCustomizeValues(config: WaterMark, style: ExifFrameStyle) {
        val bandColor = config.exifBandColor ?: style.defaultBandColor
        // mutate() để tránh đổi màu constant state dùng chung, ảnh hưởng view khác dùng cùng drawable.
        (binding.vExifBandColorSwatch.background.mutate() as? GradientDrawable)?.setColor(bandColor)

        val thicknessPercentValue = (config.exifBandThicknessPercent ?: style.defaultBandThicknessPercent) * 100f
        if (binding.slideExifBandThickness.value != thicknessPercentValue) {
            binding.slideExifBandThickness.value = thicknessPercentValue
        }
        binding.tvExifBandThicknessValue.text = getString(R.string.position_anchor_margin_value, thicknessPercentValue.toInt())

        val useSerif = config.exifUseSerifCaption ?: style.defaultUseSerifCaption
        if (binding.swExifSerifCaption.isChecked != useSerif) {
            binding.swExifSerifCaption.isChecked = useSerif
        }
    }

    /** [ColorPickerDialog] nhớ vị trí chọn lần trước qua `setPreferenceName` (giống [ColorFragment]). */
    private fun showBandColorPicker() {
        val activity = activity ?: return
        ColorPickerDialog.Builder(activity)
            .setTitle(getString(R.string.exif_customize_band_color))
            .setPreferenceName(SP_EXIF_BAND_COLOR_PICKER)
            .setPositiveButton(
                getString(R.string.tips_confirm_dialog),
                object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.color?.let { shareViewModel.updateExifBandColor(it) }
                    }
                }
            )
            .setNegativeButton(getString(R.string.tips_cancel_dialog)) { dialogInterface, _ -> dialogInterface.dismiss() }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(20)
            .show()
    }

    companion object {
        private const val TAG = "ExifPbFragment"
        private const val SP_EXIF_BAND_COLOR_PICKER = "exif_band_color_picker_dialog"

        fun safetyShow(manager: FragmentManager) {
            try {
                if (manager.findFragmentByTag(TAG) == null) {
                    ExifPbFragment().show(manager, TAG)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
