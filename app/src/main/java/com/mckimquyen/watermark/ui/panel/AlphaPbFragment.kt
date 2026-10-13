package com.mckimquyen.watermark.ui.panel

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.google.android.material.slider.Slider
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.ui.base.BasePBFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import kotlin.math.roundToInt

class AlphaPbFragment : BasePBFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.slideContentSize?.valueFrom = 0f
        binding?.slideContentSize?.valueTo = 100f
    }

    override fun doOnChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (fromUser) {
            shareViewModel.updateAlpha(alphaPercentToNative(value))
        }
    }

    override fun formatValue(config: WaterMark?): Float {
        return rebuildAlpha(config).toFloat().coerceAtLeast(0f).coerceAtMost(100f)
    }

    private fun rebuildAlpha(config: WaterMark?): Int {
        if (config?.alpha == null) {
            return 0
        }
        return nativeAlphaToPercent(config.alpha)
    }

    override fun formatValueTips(config: WaterMark?): String {
        return "${rebuildAlpha(config)}%"
    }

    override fun getSliderTitleRes(): Int = R.string.style_alpha

    companion object {
        const val TAG = "AlphaPbFragment"

        internal fun alphaPercentToNative(percent: Float): Int =
            (percent.coerceIn(0f, 100f) / 100f * 255f).roundToInt()

        internal fun nativeAlphaToPercent(alpha: Int): Int =
            (alpha.coerceIn(0, 255) / 255f * 100f).roundToInt()

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    AlphaPbFragment(),
                    TAG
                )
            }
        }
    }
}
