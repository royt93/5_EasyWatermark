package com.mckimquyen.watermark.ui.dlg

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.databinding.FComparePreviewBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import kotlinx.coroutines.launch

/**
 * FEAT-18 AC2: xem so sánh trước/sau cho 1 ảnh NGAY từ grid preview batch — KHÔNG cần export thật.
 * [com.mckimquyen.watermark.ui.MainViewModel.generateCompareBitmaps] decode + vẽ watermark 1 lần
 * (không ghi MediaStore), trả về 2 bitmap độc lập. Dùng `View.clipBounds` để lộ dần `ivWatermarked`
 * đè lên `ivOriginal` (2 ImageView cùng kích thước, cùng scaleType, xếp chồng) thay vì tự vẽ canvas
 * — đơn giản hơn, không cần custom View riêng.
 */
class ComparePreviewBottomSheetFragment : BaseBindBSDFragment<FComparePreviewBottomSheetBinding>() {

    private var revealFraction = 1f

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FComparePreviewBottomSheetBinding {
        return FComparePreviewBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uriString = arguments?.getString(ARG_URI)
        val index = arguments?.getInt(ARG_INDEX) ?: 0
        val uri = uriString?.let(Uri::parse)
        if (uri == null) {
            dismissAllowingStateLoss()
            return
        }

        binding.slCompare.addOnChangeListener { _, value, fromUser ->
            binding.tvValue.text = getString(R.string.position_anchor_margin_value, value.toInt())
            if (fromUser) applyReveal(value / 100f)
        }

        val imageInfo = shareViewModel.imageList.value?.first?.find { it.uri == uri } ?: ImageInfo(uri)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = shareViewModel.generateCompareBitmaps(requireActivity().contentResolver, imageInfo, index)
            binding.progress.isVisible = false
            if (result == null) {
                binding.tvError.isVisible = true
                return@launch
            }
            binding.ivOriginal.setImageBitmap(result.original)
            binding.ivWatermarked.setImageBitmap(result.watermarked)
            applyReveal(1f)
        }
    }

    private fun applyReveal(fraction: Float) {
        revealFraction = fraction
        val ivWatermarked = binding.ivWatermarked
        val w = ivWatermarked.width
        val h = ivWatermarked.height
        if (w == 0 || h == 0) {
            ivWatermarked.post { applyReveal(fraction) }
            return
        }
        ivWatermarked.clipBounds = Rect(0, 0, (w * fraction).toInt(), h)
    }

    companion object {
        const val TAG = "ComparePreviewBottomSheetFragment"
        private const val ARG_URI = "arg_uri"
        private const val ARG_INDEX = "arg_index"

        fun safetyShow(manager: FragmentManager, uri: Uri, index: Int) {
            try {
                val f = ComparePreviewBottomSheetFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_URI, uri.toString())
                        putInt(ARG_INDEX, index)
                    }
                }
                f.show(manager, TAG)
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
