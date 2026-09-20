package com.mckimquyen.watermark.ui.panel

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.google.android.material.slider.Slider
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.base.BasePBFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation

class DegreePbFragment : BasePBFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.slideContentSize?.valueFrom = 0f
        binding?.slideContentSize?.valueTo = WaterMarkRepository.MAX_DEGREE
    }

    override fun doOnChange(slider: Slider, value: Float, fromUser: Boolean) {
        shareViewModel.updateDegree(value)
    }

    override fun formatValue(config: WaterMark?): Float {
        return (config?.degree ?: 0f).coerceAtLeast(0f).coerceAtMost(WaterMarkRepository.MAX_DEGREE)
    }

    override fun formatValueTips(config: WaterMark?): String {
        return "${config?.degree?.toInt() ?: 0}°"
    }

    override fun getSliderTitleRes(): Int = R.string.title_text_rotate

    companion object {
        const val TAG = "DegreePbFragment"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    DegreePbFragment(),
                    TAG
                )
            }
        }
    }
}
