package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.BatchCaptionParser
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.databinding.FBatchCaptionBottomSheetBinding
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment

/**
 * FEAT-13: nhập/dán danh sách caption nhiều dòng (1 dòng = 1 ảnh, đúng thứ tự batch hiện tại) —
 * mỗi caption ghi đè watermark text CHUNG chỉ cho đúng ảnh tương ứng lúc export (xem
 * `BatchExportEngine.generateImage`). Để trống input (xoá hết) = xoá mọi caption riêng, quay lại
 * dùng watermark text chung như cũ ([BatchCaptionParser.Validation.Disabled]).
 */
class BatchCaptionBSDialogFragment : BaseBindBSDFragment<FBatchCaptionBottomSheetBinding>() {

    private val imageList: List<ImageInfo>
        get() = (requireContext() as MainActivity).getImageList()

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FBatchCaptionBottomSheetBinding {
        return FBatchCaptionBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initialImageList = imageList
        binding.tvSubtitle.text = resources.getQuantityString(
            R.plurals.batch_caption_subtitle,
            initialImageList.size,
            initialImageList.size
        )
        binding.etCaptions.setText(BatchCaptionParser.toInput(initialImageList))

        binding.btnApplyCaptions.setOnClickListener {
            // Đọc lại imageList.size LIVE ngay lúc bấm Apply (không dùng lại giá trị snapshot lúc
            // mở dialog) — WaterMarkRepository.updateImageCaptions() cũng áp caption theo
            // imageInfoList hiện tại tại thời điểm này, không phải lúc dialog mở. Nếu 2 giá trị
            // lệch nhau (list đổi khi dialog đang mở), validate() phải fail đúng ngay tại đây thay
            // vì âm thầm gán sai caption cho ảnh khác.
            val expectedCount = imageList.size
            when (val result = BatchCaptionParser.validate(binding.etCaptions.text?.toString().orEmpty(), expectedCount)) {
                is BatchCaptionParser.Validation.Disabled -> {
                    shareViewModel.updateBatchCaptions(List(expectedCount) { null })
                    dismissAllowingStateLoss()
                }

                is BatchCaptionParser.Validation.Valid -> {
                    shareViewModel.updateBatchCaptions(result.captions)
                    dismissAllowingStateLoss()
                }

                is BatchCaptionParser.Validation.CountMismatch -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.batch_caption_count_mismatch, result.actual, result.expected),
                        Toast.LENGTH_LONG
                    ).show()
                }

                is BatchCaptionParser.Validation.InvalidCsv -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.batch_caption_invalid_csv, result.lineNumber),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    companion object {
        const val TAG = "BatchCaptionBSDialogFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? BatchCaptionBSDialogFragment
                when {
                    f == null -> {
                        BatchCaptionBSDialogFragment().show(manager, TAG)
                    }

                    !f.isAdded -> {
                        f.show(manager, TAG)
                    }
                }
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
