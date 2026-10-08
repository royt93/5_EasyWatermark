package com.mckimquyen.watermark.ui.dlg

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.BatchCaptionParser
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.databinding.FBatchCaptionBottomSheetBinding
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.AppOpenSuppressor
import com.mckimquyen.watermark.utils.ktx.toast

/**
 * FEAT-13: nhập/dán danh sách caption nhiều dòng (1 dòng = 1 ảnh, đúng thứ tự batch hiện tại) —
 * mỗi caption ghi đè watermark text CHUNG chỉ cho đúng ảnh tương ứng lúc export (xem
 * `BatchExportEngine.generateImage`). Để trống input (xoá hết) = xoá mọi caption riêng, quay lại
 * dùng watermark text chung như cũ ([BatchCaptionParser.Validation.Disabled]).
 */
class BatchCaptionBSDialogFragment : BaseBindBSDFragment<FBatchCaptionBottomSheetBinding>() {

    private val imageList: List<ImageInfo>
        get() = (requireContext() as MainActivity).getImageList()

    /**
     * IDEA-17: dòng nhận kết quả giọng nói, CHỐT ngay lúc bấm micro. Không đọc lại `selectionStart`
     * lúc callback về vì dialog nghe của hệ thống đã lấy mất focus, con trỏ có thể đã khác.
     */
    private var voiceTargetLine = 0

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@registerForActivityResult

            val currentInput = binding.etCaptions.text?.toString().orEmpty()
            val (newInput, newCursor) = BatchCaptionParser.replaceLine(currentInput, voiceTargetLine, spoken)
            binding.etCaptions.setText(newInput)
            binding.etCaptions.setSelection(newCursor)
        }

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
        setupVoiceInput()

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
                    toast(
                        getString(R.string.batch_caption_count_mismatch, result.actual, result.expected),
                        long = true
                    )
                }

                is BatchCaptionParser.Validation.InvalidCsv -> {
                    toast(getString(R.string.batch_caption_invalid_csv, result.lineNumber), long = true)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    /**
     * IDEA-17: nút micro điền kết quả nhận dạng vào ĐÚNG dòng đang đặt con trỏ (một dòng = một ảnh).
     * Dùng [RecognizerIntent] thay vì `SpeechRecognizer`: app nhận dạng của hệ thống tự xin quyền ghi
     * âm của nó nên app này không cần `RECORD_AUDIO`, và không có recognizer nào phải `destroy()`.
     */
    private fun setupVoiceInput() {
        val hasRecognizer = buildRecognizeIntent().resolveActivity(requireContext().packageManager) != null
        binding.btnVoice.isVisible = hasRecognizer
        if (!hasRecognizer) {
            binding.tvVoiceTarget.isVisible = false
            return
        }

        updateVoiceTargetLabel()
        // ponytail: chỉ bám theo lần gõ và lần chạm vào ô, KHÔNG bắt được di chuyển con trỏ bằng phím
        // mũi tên bàn phím cứng — nhãn có thể trễ một nhịp trong trường hợp đó. Nâng cấp bằng subclass
        // EditText override onSelectionChanged nếu thật sự có người dùng bàn phím cứng phản ánh.
        // Không ảnh hưởng đúng/sai: dòng đích chốt bằng selectionStart THẬT lúc bấm micro.
        binding.etCaptions.doAfterTextChanged { updateVoiceTargetLabel() }
        binding.etCaptions.setOnClickListener { updateVoiceTargetLabel() }

        binding.btnVoice.setOnClickListener {
            val input = binding.etCaptions.text?.toString().orEmpty()
            voiceTargetLine = BatchCaptionParser.lineIndexAt(input, binding.etCaptions.selectionStart)
            try {
                AppOpenSuppressor.around { voiceLauncher.launch(buildRecognizeIntent()) }
            } catch (anfe: ActivityNotFoundException) {
                // Máy khai báo có RecognitionService nhưng không mở được (ROM gỡ app Google).
                anfe.printStackTrace()
                toast(getString(R.string.batch_caption_voice_unavailable), long = true)
                binding.btnVoice.isVisible = false
            }
        }
    }

    private fun buildRecognizeIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Để trống ngôn ngữ = dùng locale hiện tại của máy, khớp ngôn ngữ người dùng đang gõ.
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.batch_caption_voice))
        }

    private fun updateVoiceTargetLabel() {
        val total = imageList.size
        if (total <= 0) {
            binding.tvVoiceTarget.isVisible = false
            return
        }
        val input = binding.etCaptions.text?.toString().orEmpty()
        // Con trỏ có thể trỏ quá số ảnh (người dùng gõ thừa dòng) — kẹp lại để nhãn không hiện "4/3".
        val line = BatchCaptionParser.lineIndexAt(input, binding.etCaptions.selectionStart)
            .coerceAtMost(total - 1)
        binding.tvVoiceTarget.isVisible = true
        binding.tvVoiceTarget.text = getString(R.string.batch_caption_voice_target, line + 1, total)
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
