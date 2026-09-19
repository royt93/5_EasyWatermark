package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.ui.MainViewModel

class CompressImageDialogFragment : DialogFragment() {

    private var btnCompress: MaterialButton? = null
    private var btnCancel: MaterialButton? = null
    private var cpbCompress: ContentLoadingProgressBar? = null
    private var tvCompressTips: TextView? = null
    private val shareViewModel: MainViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            isCancelable = false
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.dlg_compress_img, container, false)
        with(root) {
            cpbCompress = findViewById(R.id.cpbCompress)
            tvCompressTips = findViewById(R.id.tvCompressTips)
            btnCancel = findViewById<MaterialButton>(R.id.btnCancel).apply {
                setOnClickListener {
                    shareViewModel.cancelCompressJob()
                    dismissAllowingStateLoss()
                }
            }
            btnCompress = findViewById(R.id.btnCompress)
        }
        setTupState()
        shareViewModel.compressedResult.observe(
            viewLifecycleOwner
        ) {
            setTupState(it)
        }

        return root
    }

    private fun setTupState(result: Result<*>? = shareViewModel.compressedResult.value) {
        when (result?.code) {
            MainViewModel.TYPE_COMPRESSING -> {
                cpbCompress?.apply {
                    isVisible = true
                    show()
                }
                tvCompressTips?.text = getString(R.string.tips_compressing)
                btnCompress?.apply {
                    isEnabled = false
                    text = context.getString(R.string.tips_compress_images)
                }
            }

            MainViewModel.TYPE_COMPRESS_OK -> {
                cpbCompress?.apply {
                    isVisible = false
                    hide()
                }
                tvCompressTips?.text = getString(R.string.tips_compress_ok)
                btnCompress?.apply {
                    text = context.getString(R.string.tips_ok)
                    isEnabled = true
                    setOnClickListener {
                        dismissAllowingStateLoss()
                    }
                }
            }

            MainViewModel.TYPE_COMPRESS_ERROR -> {
                tvCompressTips?.text =
                    getString(R.string.tips_compress_create_uri_failed, result.message)
                cpbCompress?.apply {
                    isVisible = false
                    hide()
                }
                btnCompress?.apply {
                    isEnabled = true
                    text = context.getString(R.string.tips_compress_images)
                    setOnClickListener {
                        shareViewModel.compressImg(requireActivity())
                    }
                }
            }

            else -> {
                cpbCompress?.apply {
                    isVisible = false
                    hide()
                }
                tvCompressTips?.setText(R.string.tips_need_compress_img)
                btnCompress?.apply {
                    isEnabled = true
                    text = context.getString(R.string.tips_compress_images)
                    setOnClickListener {
                        shareViewModel.compressImg(requireActivity())
                    }
                }
            }
        }
    }

    companion object {

        private const val TAG = "CompressImageDialogFragment"

        private fun newInstance(): CompressImageDialogFragment {
            return CompressImageDialogFragment()
        }

//        fun safetyHide(manager: FragmentManager) {
//            kotlin.runCatching {
//                (manager.findFragmentByTag(TAG) as? CompressImageDialogFragment)?.dismissAllowingStateLoss()
//            }
//        }

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? CompressImageDialogFragment
                when {
                    f == null -> {
                        newInstance().show(manager, TAG)
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
