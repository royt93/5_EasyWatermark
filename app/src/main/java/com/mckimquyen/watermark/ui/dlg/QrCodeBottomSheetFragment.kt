package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FQrCodeBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Bottom sheet nhập nội dung → sinh QR → đẩy vào luồng Image watermark (reuse [MainViewModel.updateIcon]).
 */
class QrCodeBottomSheetFragment : BaseBindBSDFragment<FQrCodeBottomSheetBinding>() {

    private var previewBitmap: Bitmap? = null
    private var refreshJob: Job? = null

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

        binding.btnUseQrCode.setOnClickListener {
            val content = binding.etContent.text?.toString().orEmpty().trim()
            val bitmap = previewBitmap
            AppLog.d(LOG_TAG, "[QR] btnUse clicked: content='$content' previewBitmap=${bitmap != null}")
            if (content.isEmpty() || bitmap == null) {
                Log.w(LOG_TAG, "[QR] abort: content empty or bitmap null")
                Toast.makeText(requireContext(), R.string.qr_code_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = saveBitmapToCache(bitmap)
            AppLog.d(LOG_TAG, "[QR] saveBitmapToCache -> uri=$uri")
            if (uri != null) {
                shareViewModel.updateIcon(uri)
                AppLog.d(LOG_TAG, "[QR] updateIcon called, dismissing")
                dismissAllowingStateLoss()
            } else {
                Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshPreview(content: String) {
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(QR_REFRESH_DEBOUNCE_MS)
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeGenerator.generate(content, size = QrCodeGenerator.DEFAULT_SIZE)
            }
            previewBitmap = bitmap
            binding.ivPreview.setImageBitmap(bitmap)
            AppLog.d(LOG_TAG, "[QR] refreshPreview: content.len=${content.length} bitmap=${bitmap != null}")
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(requireContext().cacheDir, "qrcodes")
            cachePath.mkdirs()
            val file = File(cachePath, "qr_temp_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
