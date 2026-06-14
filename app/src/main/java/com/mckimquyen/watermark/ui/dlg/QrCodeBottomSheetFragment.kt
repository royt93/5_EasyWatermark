package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FQrCodeBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.QrCodeGenerator
import java.io.File
import java.io.FileOutputStream

/**
 * Bottom sheet nhập nội dung → sinh QR → đẩy vào luồng Image watermark (reuse [MainViewModel.updateIcon]).
 */
class QrCodeBottomSheetFragment : BaseBindBSDFragment<FQrCodeBottomSheetBinding>() {

    private var previewBitmap: Bitmap? = null

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
            if (content.isEmpty() || bitmap == null) {
                Toast.makeText(requireContext(), R.string.qr_code_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = saveBitmapToCache(bitmap)
            if (uri != null) {
                shareViewModel.updateIcon(uri)
                dismissAllowingStateLoss()
            } else {
                Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshPreview(content: String) {
        val bitmap = QrCodeGenerator.generate(content, size = QrCodeGenerator.DEFAULT_SIZE)
        previewBitmap = bitmap
        binding.ivPreview.setImageBitmap(bitmap)
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setOnShowListener {
                val bottomSheet = (this as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    val behavior = BottomSheetBehavior.from(it)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
        }
    }

    companion object {
        const val TAG = "QrCodeBottomSheetFragment"

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
