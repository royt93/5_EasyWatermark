package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.databinding.DlgExifBorderBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment

class ExifPbFragment : BaseBindBSDFragment<DlgExifBorderBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgExifBorderBinding {
        return DlgExifBorderBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shareViewModel.waterMark.observe(viewLifecycleOwner) { config ->
            if (config == null) return@observe
            if (binding.swExif.isChecked != config.enableExif) {
                binding.swExif.isChecked = config.enableExif
            }
        }

        binding.swExif.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                shareViewModel.toggleExifBorder()
            }
        }
    }

    companion object {
        private const val TAG = "ExifPbFragment"

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
