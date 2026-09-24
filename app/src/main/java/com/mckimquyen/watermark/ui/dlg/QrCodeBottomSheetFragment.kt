package com.mckimquyen.watermark.ui.dlg

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.databinding.FQrCodeBottomSheetBinding
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.FileUtils
import com.mckimquyen.watermark.utils.QrCodeGenerator
import com.mckimquyen.watermark.utils.ktx.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bottom sheet nhập nội dung → sinh QR → đẩy vào luồng Image watermark (reuse [MainViewModel.updateIcon]).
 *
 * IDEA-07: khi bật `swQrDynamic`, `etContent` đổi vai trò thành TEMPLATE (token `{hash}` `{date}`
 * `{portfolio_link}`...) thay vì nội dung QR trực tiếp — preview resolve template này với ảnh đang
 * chọn trong editor ([shareViewModel.selectedImage]) qua [ExportNaming.resolveQrContent], còn
 * BATCH EXPORT thật resolve lại cho TỪNG ảnh riêng trong `BatchExportEngine.generateImage()`.
 */
class QrCodeBottomSheetFragment : BaseBindBSDFragment<FQrCodeBottomSheetBinding>() {

    private var previewBitmap: Bitmap? = null
    private var refreshJob: Job? = null
    private val exportNaming = ExportNaming()

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FQrCodeBottomSheetBinding {
        return FQrCodeBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshPreview(s?.toString().orEmpty())
            }
        })

        binding.etPortfolioLink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (binding.swQrDynamic.isChecked) refreshPreview(binding.etContent.text?.toString().orEmpty())
            }
        })

        binding.swQrDynamic.setOnCheckedChangeListener { _, isChecked ->
            binding.tilPortfolioLink.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.tilContent.hint = getString(if (isChecked) R.string.qr_code_template_hint else R.string.qr_code_hint)
            if (isChecked && binding.etContent.text.isNullOrBlank()) {
                binding.etContent.setText(ExportNaming.DEFAULT_QR_CONTENT_TEMPLATE)
            } else {
                refreshPreview(binding.etContent.text?.toString().orEmpty())
            }
        }

        restoreFromCurrentConfig()

        binding.btnUseQrCode.setOnClickListener {
            val content = binding.etContent.text?.toString().orEmpty().trim()
            val bitmap = previewBitmap
            AppLog.d(LOG_TAG, "[QR] btnUse clicked: content='$content' previewBitmap=${bitmap != null}")
            if (content.isEmpty() || bitmap == null) {
                Log.w(LOG_TAG, "[QR] abort: content empty or bitmap null")
                toast(R.string.qr_code_empty)
                return@setOnClickListener
            }
            val uri = saveBitmapToCache(bitmap)
            AppLog.d(LOG_TAG, "[QR] saveBitmapToCache -> uri=$uri")
            if (uri == null) {
                toast(R.string.save_failed)
                return@setOnClickListener
            }
            if (binding.swQrDynamic.isChecked) {
                val portfolioLink = binding.etPortfolioLink.text?.toString().orEmpty().trim()
                shareViewModel.updateQrDynamicConfig(uri, content, portfolioLink)
                AppLog.d(LOG_TAG, "[QR] updateQrDynamicConfig called, dismissing")
            } else {
                shareViewModel.updateIcon(uri)
                AppLog.d(LOG_TAG, "[QR] updateIcon called, dismissing")
            }
            dismissAllowingStateLoss()
        }
    }

    /** IDEA-07: mở lại sheet sau khi đã bật QR động trước đó → khôi phục switch/template/link đang lưu. */
    private fun restoreFromCurrentConfig() {
        val current = shareViewModel.waterMark.value ?: return
        if (!current.qrDynamicEnabled) return
        binding.swQrDynamic.isChecked = true
        binding.etPortfolioLink.setText(current.qrPortfolioLink)
        binding.etContent.setText(current.qrContentTemplate.ifBlank { ExportNaming.DEFAULT_QR_CONTENT_TEMPLATE })
    }

    /** [rawInput] là nội dung QR trực tiếp (mode tĩnh) hoặc template token (mode động, [ExportNaming.resolveQrContent] resolve trước khi generate). */
    private fun refreshPreview(rawInput: String) {
        refreshJob?.cancel()
        if (rawInput.isBlank()) {
            previewBitmap = null
            binding.ivPreview.setImageBitmap(null)
            return
        }
        val isDynamic = binding.swQrDynamic.isChecked
        val portfolioLink = binding.etPortfolioLink.text?.toString().orEmpty().trim()
        val currentImage = shareViewModel.selectedImage.value ?: ImageInfo.empty()
        val contentResolver = requireContext().contentResolver
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(QR_REFRESH_DEBOUNCE_MS)
            val bitmap = withContext(Dispatchers.Default) {
                val content = if (isDynamic) {
                    exportNaming.resolveQrContent(
                        template = rawInput,
                        imageInfo = currentImage,
                        contentResolver = contentResolver,
                        index = 0,
                        portfolioLink = portfolioLink
                    )
                } else {
                    rawInput
                }
                QrCodeGenerator.generate(content, size = QrCodeGenerator.DEFAULT_SIZE)
            }
            previewBitmap = bitmap
            binding.ivPreview.setImageBitmap(bitmap)
            AppLog.d(LOG_TAG, "[QR] refreshPreview: dynamic=$isDynamic rawInput.len=${rawInput.length} bitmap=${bitmap != null}")
        }
    }

    internal fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        // ENH-29: Dọn dẹp các file QR tạm cũ (giữ tối đa 3 file gần nhất, xoá file > 24h)
        FileUtils.cleanOldTempFiles(File(requireContext().cacheDir, "qrcodes"), maxRetainedFiles = 3)
        return QrCodeGenerator.saveToCache(requireContext(), bitmap, prefix = "qr_temp_")
    }

    companion object {
        const val TAG = "QrCodeBottomSheetFragment"
        private const val QR_REFRESH_DEBOUNCE_MS = 250L

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? QrCodeBottomSheetFragment
                when {
                    f == null -> {
                        QrCodeBottomSheetFragment().show(manager, TAG)
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
