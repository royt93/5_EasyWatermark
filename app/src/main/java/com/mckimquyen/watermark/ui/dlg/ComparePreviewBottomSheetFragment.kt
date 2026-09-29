package com.mckimquyen.watermark.ui.dlg

import android.graphics.Bitmap
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FEAT-18 AC2: xem so sánh trước/sau cho 1 ảnh NGAY từ grid preview batch — KHÔNG cần export thật.
 * [com.mckimquyen.watermark.ui.MainViewModel.generateCompareBitmaps] decode + vẽ watermark 1 lần
 * (không ghi MediaStore), trả về 2 bitmap ĐỘC LẬP (caller sở hữu, không chia sẻ buffer với
 * `BitmapCache`). Dùng `View.clipBounds` để lộ dần `ivWatermarked` đè lên `ivOriginal` (2 ImageView
 * cùng kích thước, cùng scaleType, xếp chồng) thay vì tự vẽ canvas — đơn giản hơn, không cần custom
 * View riêng.
 *
 * BUG-38: theo đúng pattern [SurvivabilityBottomSheetFragment] — giữ [renderJob] để cancel và giữ
 * 2 bitmap đang hiển thị để recycle trong [onDestroyView]; nếu job bị huỷ giữa chừng (user đóng
 * sheet sớm) thì recycle ngay bản mồ côi vừa sinh ra thay vì set vào view đã destroy.
 */
class ComparePreviewBottomSheetFragment : BaseBindBSDFragment<FComparePreviewBottomSheetBinding>() {

    private var revealFraction = 1f
    private var renderJob: Job? = null
    internal var originalBitmap: Bitmap? = null
        private set
    internal var watermarkedBitmap: Bitmap? = null
        private set

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
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            // Không huỷ giữa chừng tác vụ đã cấp phát bitmap — nếu job bị huỷ (user đóng sheet
            // sớm) thì recycle ngay bản mồ côi ở dưới thay vì để rò rỉ (BUG-38).
            val result = withContext(NonCancellable) {
                shareViewModel.generateCompareBitmaps(requireActivity().contentResolver, imageInfo, index)
            }
            // BUG-AUDIT-2026-09-29-DEADCODE: `view` không định danh (unqualified) ở đây phân giải
            // thành tham số `view: View` non-null của `onViewCreated()` (che khuất property
            // `Fragment.view` nullable) — compiler cảnh báo "Condition is always false", nhánh
            // `view == null` chưa từng chạy. Dùng `this@...view` để trỏ đúng property Fragment.
            if (!isActive || this@ComparePreviewBottomSheetFragment.view == null) {
                result?.original?.recycle()
                result?.watermarked?.recycle()
                return@launch
            }
            binding.progress.isVisible = false
            if (result == null) {
                binding.tvError.isVisible = true
                return@launch
            }
            originalBitmap = result.original
            watermarkedBitmap = result.watermarked
            binding.ivOriginal.setImageBitmap(result.original)
            binding.ivWatermarked.setImageBitmap(result.watermarked)
            applyReveal(1f)
        }
    }

    private fun applyReveal(fraction: Float) {
        revealFraction = fraction
        // BUG-AUDIT-2026-09-29: khi width/height còn 0 (view chưa layout xong), hàm tự post{} lặp
        // lại chính nó — nếu user đóng sheet TRƯỚC khi callback đó chạy, onDestroyView() đã set
        // binding null, callback trễ chạm vào `binding` sẽ NPE. Guard `view == null` (Fragment.view,
        // được framework tự null hoá trong onDestroyView) ở cả điểm vào lẫn trong callback trễ.
        if (view == null) return
        val ivWatermarked = binding.ivWatermarked
        val w = ivWatermarked.width
        val h = ivWatermarked.height
        if (w == 0 || h == 0) {
            ivWatermarked.post {
                if (view != null) applyReveal(fraction)
            }
            return
        }
        ivWatermarked.clipBounds = Rect(0, 0, (w * fraction).toInt(), h)
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        renderJob = null
        binding.ivOriginal.setImageDrawable(null)
        binding.ivWatermarked.setImageDrawable(null)
        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        watermarkedBitmap?.takeIf { !it.isRecycled }?.recycle()
        originalBitmap = null
        watermarkedBitmap = null
        super.onDestroyView()
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
