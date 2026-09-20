package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import com.mckimquyen.watermark.databinding.FSignatureBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.FileUtils
import java.io.File
import java.io.FileOutputStream

class SignatureBottomSheetFragment : BaseBindBSDFragment<FSignatureBottomSheetBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FSignatureBottomSheetBinding {
        return FSignatureBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultInkColor = com.google.android.material.color.MaterialColors.getColor(
            binding.signatureView,
            com.google.android.material.R.attr.colorOnSurface,
            android.graphics.Color.BLACK
        )
        binding.signatureView.drawColor = defaultInkColor

        binding.btnClear.setOnClickListener {
            binding.signatureView.clear()
        }

        binding.btnSaveSignature.setOnClickListener {
            val bitmap = binding.signatureView.getSignatureBitmap()
            if (bitmap == null) {
                return@setOnClickListener
            }
            val uri = saveBitmapToCache(bitmap)
            if (uri != null) {
                shareViewModel.updateIcon(uri)
                dismissAllowingStateLoss()
            }
        }
    }

    internal fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(requireContext().cacheDir, "signatures")
            cachePath.mkdirs()
            // ENH-29: Dọn dẹp các file chữ ký tạm cũ (giữ tối đa 3 file gần nhất, xoá file > 24h)
            FileUtils.cleanOldTempFiles(cachePath, maxRetainedFiles = 3)
            val file = File(cachePath, "sig_temp_${System.currentTimeMillis()}.png")
            // ENH-28: Dùng .use {} để đảm bảo đóng FileOutputStream kể cả khi compress() ném Exception
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
        const val TAG = "SignatureBottomSheetFragment"

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? SignatureBottomSheetFragment
                when {
                    f == null -> {
                        SignatureBottomSheetFragment().show(manager, TAG)
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
