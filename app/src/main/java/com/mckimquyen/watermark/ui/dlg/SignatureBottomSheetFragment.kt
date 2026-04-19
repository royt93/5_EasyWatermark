package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.watermark.databinding.FSignatureBottomSheetBinding
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import java.io.File
import java.io.FileOutputStream

class SignatureBottomSheetFragment : BaseBindBSDFragment<FSignatureBottomSheetBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?,
    ): FSignatureBottomSheetBinding {
        return FSignatureBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(requireContext().cacheDir, "signatures")
            cachePath.mkdirs()
            val file = File(cachePath, "sig_temp_${System.currentTimeMillis()}.png")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
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
