package com.mckimquyen.watermark.ui.dlg

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.databinding.DlgSaveFileBinding
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.SaveImageListAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
import com.mckimquyen.watermark.utils.ktx.preCheckStoragePermission

class SaveImageBSDialogFragment : BaseBindBSDFragment<DlgSaveFileBinding>() {
    private val imageList: List<ImageInfo>
        get() = (requireContext() as MainActivity).getImageList()

    private val popArray = arrayOf("JPEG", "PNG", "WEBP")

    @Suppress("DEPRECATION")
    private val formatByIndex = arrayOf(
        Bitmap.CompressFormat.JPEG,
        Bitmap.CompressFormat.PNG,
        Bitmap.CompressFormat.WEBP
    )

    // Resize cạnh dài: nhãn ↔ giá trị px (0 = giữ nguyên) — nguồn từ OutputImageUtils.resizePresets
    // (gồm preset px thuần + preset đặt tên theo nền tảng mạng xã hội, xem FEAT-09).
    private val resizeArray = OutputImageUtils.resizePresets.map { it.label }.toTypedArray()
    private val resizeValues = OutputImageUtils.resizePresets.map { it.maxLongEdge }.toIntArray()

    /** PNG là lossless nên ẩn slider chất lượng; JPEG/WEBP có dùng. */
    private fun supportsQuality(format: Bitmap.CompressFormat): Boolean =
        format != Bitmap.CompressFormat.PNG

    private fun labelOf(format: Bitmap.CompressFormat): String =
        popArray[formatByIndex.indexOf(format).coerceAtLeast(0)]

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgSaveFileBinding {
        val root = DlgSaveFileBinding.inflate(layoutInflater, container, false)
        val isSaving = shareViewModel.saveResult.value?.code == MainViewModel.TYPE_SAVING
        Log.d(TAG, "bindView: isSaving $isSaving")
        with(root) {
            btnSave.apply {
                setOnClickListener {
                    if (shareViewModel.saveResult.value?.code == MainViewModel.TYPE_JOB_FINISH) {
                        // share to other apps
                        openShare()
                    } else {
                        // saving jobs
                        shareViewModel.saveCopyright(etCopyright.text?.toString().orEmpty().trim())
                        requireActivity().preCheckStoragePermission {
                            shareViewModel.saveImage(
                                requireActivity().contentResolver,
                                (requireContext() as MainActivity).getImageViewInfo(),
                                (requireContext() as MainActivity).getImageList()
                            )
                        }
                    }
                }
            }

            btnOpenGallery.apply {
                this.isInvisible = true
                setOnClickListener {
                    openGallery()
                }
            }

            atvFormat.also {
                val adapter = ArrayAdapter(
                    /* context = */ requireContext(),
                    /* resource = */ R.layout.simple_dropdown_item_1line,
                    /* objects = */ popArray
                )
                it.setAdapter(adapter)
                it.setDropDownBackgroundDrawable(
                    requireContext().getDrawable(R.drawable.bg_dropdown_popup)
                )
                it.setText(labelOf(shareViewModel.outputFormat), false)
                it.setOnItemClickListener { _, _, index, _ ->
                    val targetFormat = formatByIndex.getOrElse(index) { Bitmap.CompressFormat.JPEG }
                    shareViewModel.saveOutput(targetFormat, slideQuality.value.toInt())
                    flQuality.isVisible = supportsQuality(targetFormat)
                    slideQuality.isVisible = supportsQuality(targetFormat)
                }
            }

            atvResize.also {
                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.simple_dropdown_item_1line,
                    resizeArray
                )
                it.setAdapter(adapter)
                it.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.bg_dropdown_popup))
                val curIdx = resizeValues.indexOf(shareViewModel.maxOutputLongEdge).coerceAtLeast(0)
                it.setText(resizeArray[curIdx], false)
                it.setOnItemClickListener { _, _, index, _ ->
                    shareViewModel.saveMaxLongEdge(resizeValues.getOrElse(index) { 0 })
                }
            }

            etCopyright.setText(shareViewModel.copyright)
            etCopyright.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    shareViewModel.saveCopyright(etCopyright.text?.toString().orEmpty().trim())
                }
            }

            flQuality.isVisible = supportsQuality(shareViewModel.outputFormat)
            slideQuality.isVisible = supportsQuality(shareViewModel.outputFormat)

            rvResult.apply {
                adapter = SaveImageListAdapter(requireContext()).also {
                    it.submitList(imageList)
                }
                itemAnimator = null
                val spanCount = when {
                    imageList.size < 5 -> imageList.size.coerceAtLeast(1)
                    imageList.size < 20 && imageList.size % 2 == 0 -> imageList.size / 2
                    imageList.size < 20 -> imageList.size / 2 + 1
                    else -> 10
                }
                scrollBarStyle
                layoutManager = GridLayoutManager(requireContext(), spanCount)
            }
            val compressLevel = shareViewModel.compressLevel.toFloat()

            val theAdapter = rvResult.adapter as SaveImageListAdapter

            tvQualityValue.text = compressLevel.toInt().toString()

            tvResult.text = requireContext().getString(
                R.string.dialog_save_export_list_title,
                "${theAdapter.data.count { it.jobState is JobState.Success }}/${theAdapter.itemCount}"
            )

            slideQuality.apply {
                value = compressLevel
                addOnChangeListener { _, value, _ ->
                    shareViewModel.saveOutput(shareViewModel.outputFormat, value.toInt())
                    tvQualityValue.text = value.toInt().toString()
                }
            }

            shareViewModel.saveProcess.observe(viewLifecycleOwner) {
                theAdapter.updateJobState(it)
                if (it?.jobState is JobState.Success) {
                    val count = theAdapter.finishCount
                    tvResult.text = requireContext().getString(
                        R.string.dialog_save_export_list_title,
                        "$count/${theAdapter.itemCount}"
                    )
                }
            }

            shareViewModel.saveResult.observe(viewLifecycleOwner) {
                setUpLoadingView(it)
            }

            shareViewModel.colorPalette.observe(viewLifecycleOwner) {
            }
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpLoadingView(shareViewModel.saveResult.value)
    }

    private fun setUpLoadingView(
        saveResult: Result<*>?
    ) {
        when (saveResult?.code) {
            MainViewModel.TYPE_SAVING -> {
                binding.btnSave.apply {
                    isEnabled = false
                    text = getString(R.string.dialog_save_exporting)
                }
                binding.btnOpenGallery.isInvisible = true
                binding.atvFormat.isEnabled = false
                binding.slideQuality.isEnabled = false
                binding.menuFormat.isEnabled = false
                (dialog as BottomSheetDialog).behavior.isDraggable = false
                isCancelable = false
            }

            MainViewModel.TYPE_JOB_FINISH -> {
                binding.btnSave.apply {
                    isEnabled = true
                    text = getString(R.string.share)
                }
                TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
                binding.btnOpenGallery.isInvisible = false
                binding.atvFormat.isEnabled = true
                binding.slideQuality.isEnabled = true
                binding.menuFormat.isEnabled = true
                (dialog as BottomSheetDialog).behavior.apply {
                    isDraggable = true
                    state = STATE_EXPANDED
                }
                isCancelable = true
            }

            else -> {
                binding.btnSave.apply {
                    isEnabled = true
                    text = getString(R.string.dialog_export_to_gallery)
                }
                binding.btnOpenGallery.isInvisible = true
                binding.atvFormat.isEnabled = true
                binding.slideQuality.isEnabled = true
                binding.menuFormat.isEnabled = true
                (dialog as BottomSheetDialog).behavior.isDraggable = true
                isCancelable = true
                val theAdapter = binding.rvResult.adapter as SaveImageListAdapter
                binding.tvResult.text = requireContext().getString(
                    R.string.dialog_save_export_list_title,
                    "${theAdapter.data.count { it.jobState is JobState.Success }}/${theAdapter.itemCount}"
                )
            }
        }
    }

    private fun openGallery() {
        val list = shareViewModel.imageList.value?.first
        if (list.isNullOrEmpty()) return
        val outputUri = list.first().shareUri
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(outputUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openShare() {
        val list = ArrayList(shareViewModel.imageList.value?.first ?: emptyList())
        if (list.isEmpty()) return
        val intent = Intent().apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (list.size == 1) {
            val outputUri = list.first().shareUri
            intent.apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, outputUri)
                clipData = android.content.ClipData.newUri(requireContext().contentResolver, "Image", outputUri)
            }
        } else {
            val uriList = ArrayList(list.mapNotNull { it.shareUri })
            if (uriList.isNotEmpty()) {
                intent.apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                    val clipData = android.content.ClipData("Images", arrayOf("image/*"), android.content.ClipData.Item(uriList[0]))
                    for (i in 1 until uriList.size) {
                        clipData.addItem(android.content.ClipData.Item(uriList[i]))
                    }
                    this.clipData = clipData
                }
            }
        }
        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Share error with ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    companion object {

        private const val TAG = "SaveImageBSDialogFragment"

        private fun newInstance(): SaveImageBSDialogFragment {
            return SaveImageBSDialogFragment()
        }

        fun safetyHide(manager: FragmentManager) {
            kotlin.runCatching {
                (manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment)?.dismissAllowingStateLoss()
            }
        }

        fun safetyShow(manager: FragmentManager) {
            try {
                val f = manager.findFragmentByTag(TAG) as? SaveImageBSDialogFragment
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
