package com.mckimquyen.watermark.ui.panel

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.google.android.material.slider.Slider
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.base.BasePBFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation

class HorizonPbFragment : BasePBFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.slideContentSize?.valueFrom = 0f
        binding?.slideContentSize?.valueTo = WaterMarkRepository.MAX_HORIZON_GAP.toFloat()
    }

    override fun doOnChange(slider: Slider, value: Float, fromUser: Boolean) {
        shareViewModel.updateHorizon(value.toInt())
    }

    override fun formatValue(config: WaterMark?): Float {
        return (config?.hGap?.toFloat() ?: 1f).coerceAtLeast(0f)
            .coerceAtMost(WaterMarkRepository.MAX_HORIZON_GAP.toFloat())
    }

    override fun formatValueTips(config: WaterMark?): String {
        return "${config?.hGap ?: 0} px"
    }

    companion object {
        const val TAG = "HorizonPbFragment"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    /* containerViewId = */ containerId,
                    /* fragment = */ HorizonPbFragment(),
                    /* tag = */ TAG
                )
            }
        }
    }
}
