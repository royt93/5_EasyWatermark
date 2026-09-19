package com.mckimquyen.watermark.ui.panel

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FColorBinding
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.ui.widget.onItemClick
import com.mckimquyen.watermark.ui.widget.utils.BounceEdgeEffectFactory
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class ColorFragment : BaseBindFragment<FColorBinding>() {

    private val white by lazy {
        Color.WHITE
    }

    private val black by lazy {
        Color.BLACK
    }

    private val yellow by lazy {
        Color.parseColor("#FFB800")
    }

    private val orange by lazy {
        Color.parseColor("#FF3535")
    }

    private val pink by lazy {
        Color.parseColor("#FF008A")
    }

    private val blue by lazy {
        Color.parseColor("#00D1FF")
    }

    private val green by lazy {
        Color.parseColor("#1BFF3F")
    }

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FColorBinding {
        val b = FColorBinding.inflate(layoutInflater, container, false)

        val savedColor = shareViewModel.waterMark.value?.textColor ?: Color.WHITE
        val colorArrayList = buildColorList(savedColor)

        b.rvColor.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter =
                ColorPreviewAdapter(colorArrayList)
            onItemClick { recyclerView, position, _ ->
                if (position == colorArrayList.size - 1 && activity != null) {
                    showColorPickerDialog(this.adapter as ColorPreviewAdapter)
                    return@onItemClick
                }
                (recyclerView.adapter as ColorPreviewAdapter).let {
                    val color = it.previewList[position].color
                    changeIvColor(color)
                    it.updateSelectedColor(color)
                }
            }
            edgeEffectFactory = BounceEdgeEffectFactory(context, this)
        }
        return b
    }

    private fun showColorPickerDialog(adapter: ColorPreviewAdapter) {
        ColorPickerDialog.Builder(activity)
            .setTitle(requireActivity().getString(R.string.tips_choose_color_dialog))
            .setPreferenceName(SP_COLOR_PICKER_DIALOG)
            .setPositiveButton(
                requireActivity().getString(R.string.tips_confirm_dialog),
                object : ColorEnvelopeListener {
                    override fun onColorSelected(
                        envelope: ColorEnvelope?,
                        fromUser: Boolean
                    ) {
                        envelope?.color?.let {
                            adapter.updateSelectedColor(it)
                            changeIvColor(it, Color.alpha(it))
                        }
                    }
                }
            )
            .setNegativeButton(requireActivity().getString(R.string.tips_cancel_dialog)) { dialogInterface, _ -> dialogInterface.dismiss() }
            .attachAlphaSlideBar(true)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(20)
            .show()
    }

    private fun changeIvColor(color: Int, alpha: Int = -1) {
        with(shareViewModel) {
            updateTextColor(color)
            if (alpha != -1) {
                updateAlpha(alpha)
            }
        }
    }

    private fun buildColorList(savedColor: Int): ArrayList<ColorPreviewAdapter.PreViewModel> {
        return arrayListOf(
            ColorPreviewAdapter.PreViewModel(
                color = white,
                selected = savedColor == white,
                contentDescription = getString(R.string.color_white)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = black,
                selected = savedColor == black,
                contentDescription = getString(R.string.color_black)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = yellow,
                selected = savedColor == yellow,
                contentDescription = getString(R.string.color_yellow)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = orange,
                selected = savedColor == orange,
                contentDescription = getString(R.string.color_orange)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = pink,
                selected = savedColor == pink,
                contentDescription = getString(R.string.color_pink)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = blue,
                selected = savedColor == blue,
                contentDescription = getString(R.string.color_blue)
            ),
            ColorPreviewAdapter.PreViewModel(
                color = green,
                selected = savedColor == green,
                contentDescription = getString(R.string.color_green)
            ),
            ColorPreviewAdapter.PreViewModel(
                type = ColorPreviewAdapter.PreviewType.Res,
                resId = R.drawable.ic_btn_color_picker,
                contentDescription = getString(R.string.tips_choose_color_dialog)
            )
        )
    }

    companion object {
        const val TAG = "ColorFragment"
        private const val SP_COLOR_PICKER_DIALOG = "water_mark_color_picker_dialog"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    ColorFragment(),
                    TAG
                )
            }
        }
    }
}
