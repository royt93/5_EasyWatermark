package com.mckimquyen.watermark.ui.dlg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.databinding.DlgExifBorderBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment

class ExifPbFragment : BaseBindBSDFragment<DlgExifBorderBinding>() {

    private val styleButtons by lazy {
        mapOf(
            ExifFrameStyle.CLASSIC to binding.btnStyleClassic,
            ExifFrameStyle.POLAROID to binding.btnStylePolaroid,
            ExifFrameStyle.FILM_STRIP to binding.btnStyleFilmStrip,
            ExifFrameStyle.MINIMAL to binding.btnStyleMinimal
        )
    }

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
            highlightStyle(ExifFrameStyle.obtain(config.exifFrameStyle))
        }

        binding.swExif.setOnCheckedChangeListener { buttonView, isChecked ->
            binding.groupFrameStyle.isVisible = isChecked
            if (buttonView.isPressed) {
                shareViewModel.toggleExifBorder()
            }
        }
    }

    private fun highlightStyle(selected: ExifFrameStyle) {
        ExifFrameStyleHighlighter.apply(styleButtons, selected)
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
