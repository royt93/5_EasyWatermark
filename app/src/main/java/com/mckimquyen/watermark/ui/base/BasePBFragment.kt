package com.mckimquyen.watermark.ui.base

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.databinding.FBasePbBinding
import com.mckimquyen.watermark.utils.ktx.toColor

abstract class BasePBFragment : BaseBindFragment<FBasePbBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FBasePbBinding {
        val b = FBasePbBinding.inflate(layoutInflater, container, false)

        b.slideContentSize.apply {
            value = formatValue(shareViewModel.waterMark.value)
            contentDescription = getSliderDescription(shareViewModel.waterMark.value)
            addOnChangeListener { slider, value, fromUser ->
                doOnChange(slider = slider, value = value, fromUser = fromUser)
                slider.contentDescription = getSliderDescription(shareViewModel.waterMark.value)
            }
        }

        b.tvProgressVertical.apply {
            text = formatValueTips(shareViewModel.waterMark.value)
        }

        applySliderColors(b)

        return b
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shareViewModel.waterMark.observe(viewLifecycleOwner) {
            binding?.tvProgressVertical?.text = formatValueTips(it)
            binding?.slideContentSize?.contentDescription = getSliderDescription(it)
        }
    }

    companion object {
        fun applySliderColors(b: FBasePbBinding) {
            val context = b.root.context
            val activeColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorPrimary,
                Color.BLACK
            )
            val inactiveColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceVariant,
                Color.LTGRAY
            )
            val onSurfaceColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurface,
                Color.BLACK
            )

            b.slideContentSize.trackActiveTintList = ColorStateList.valueOf(activeColor)
            b.slideContentSize.trackInactiveTintList = ColorStateList.valueOf(inactiveColor)
            b.slideContentSize.thumbTintList = ColorStateList.valueOf(activeColor)
            b.tvProgressVertical.setTextColor(onSurfaceColor)
        }
    }

    abstract fun doOnChange(slider: Slider, value: Float, fromUser: Boolean)

    abstract fun formatValue(config: WaterMark?): Float

    abstract fun formatValueTips(config: WaterMark?): String

    open fun getSliderTitleRes(): Int = 0

    open fun getSliderTitle(): String {
        val res = getSliderTitleRes()
        return if (res != 0) context?.getString(res) ?: "" else ""
    }

    open fun getSliderDescription(config: WaterMark?): String {
        val title = getSliderTitle()
        val tips = formatValueTips(config)
        return if (title.isNotEmpty()) "$title: $tips" else tips
    }
}
