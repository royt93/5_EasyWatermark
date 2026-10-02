package com.mckimquyen.watermark.ui.dlg

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.databinding.DlgDualPresetPickerBinding
import com.mckimquyen.watermark.ui.adapter.DualPresetAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.ktx.toast

/**
 * FEAT-26: Bottom Sheet cho phép người dùng chọn nhanh mẫu dấu kép 1-chạm (Dual Watermark Presets).
 */
class DualPresetPickerBSDFragment : BaseBindBSDFragment<DlgDualPresetPickerBinding>() {

    override fun bindView(layoutInflater: LayoutInflater, container: ViewGroup?): DlgDualPresetPickerBinding =
        DlgDualPresetPickerBinding.inflate(layoutInflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DualPresetAdapter { preset ->
            onPresetSelected(preset)
        }
        binding.rvDualPresets.adapter = adapter
    }

    private fun onPresetSelected(preset: DualWatermarkPreset) {
        shareViewModel.applyDualPreset(preset)

        val currentIconUri = shareViewModel.waterMark.value?.iconUri
        if (currentIconUri == null || currentIconUri == Uri.EMPTY) {
            toast(R.string.dual_preset_pick_logo_prompt)
        } else {
            toast(R.string.dual_preset_applied_toast)
        }
        dismiss()
    }

    companion object {
        const val TAG = "DualPresetPickerBSDFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? DualPresetPickerBSDFragment
                when {
                    f == null -> DualPresetPickerBSDFragment().show(manager, TAG)
                    !f.isAdded -> f.show(manager, TAG)
                }
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
