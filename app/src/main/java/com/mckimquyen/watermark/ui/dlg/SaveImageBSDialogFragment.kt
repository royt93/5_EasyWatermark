package com.mckimquyen.watermark.ui.dlg

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.databinding.DlgSaveFileBinding
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.SaveImageListAdapter
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import com.mckimquyen.watermark.utils.ExportZipHelper
import com.mckimquyen.watermark.utils.FileUtils
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
import com.mckimquyen.watermark.utils.ktx.preCheckStoragePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    // FEAT-19: Chính sách xử lý trùng tên file
    private val conflictPolicyValues = arrayOf(
        com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH,
        com.mckimquyen.watermark.data.model.ConflictPolicy.RENAME_VERSION,
        com.mckimquyen.watermark.data.model.ConflictPolicy.OVERWRITE,
        com.mckimquyen.watermark.data.model.ConflictPolicy.SKIP
    )

    /** PNG là lossless nên ẩn slider chất lượng; JPEG/WEBP có dùng. */
    private fun supportsQuality(format: Bitmap.CompressFormat): Boolean =
        format != Bitmap.CompressFormat.PNG

    /**
     * ENH-13: hiển thị rõ số ảnh lỗi khi có, giữ nguyên format "X/Y" cũ khi mọi ảnh đều thành
     * công (không thêm nhiễu UI khi không cần).
     */
    private fun exportCountText(adapter: SaveImageListAdapter): String {
        val successCount = adapter.finishCount
        val failCount = adapter.failCount
        val countArg = if (failCount > 0) {
            getString(
                R.string.dialog_save_export_count_with_failures,
                successCount,
                adapter.itemCount,
                failCount
            )
        } else {
            "$successCount/${adapter.itemCount}"
        }
        return getString(R.string.dialog_save_export_list_title, countArg)
    }

    private fun labelOf(format: Bitmap.CompressFormat): String =
        popArray[formatByIndex.indexOf(format).coerceAtLeast(0)]

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): DlgSaveFileBinding {
        val root = DlgSaveFileBinding.inflate(layoutInflater, container, false)
        val isSaving = shareViewModel.saveResult.value?.code == MainViewModel.TYPE_SAVING
        AppLog.d(TAG, "bindView: isSaving $isSaving")
        with(root) {
            btnSave.apply {
                setOnClickListener {
                    when (shareViewModel.saveResult.value?.code) {
                        MainViewModel.TYPE_JOB_FINISH -> openShare()
                        // ENH-01 AC2: đang export → nút chuyển sang Cancel, huỷ batch qua WorkManager.
                        MainViewModel.TYPE_SAVING -> shareViewModel.cancelSaveImage()
                        else -> {
                            shareViewModel.saveCopyright(etCopyright.text?.toString().orEmpty().trim())
                            // BUG-32: Lưu pattern tên file ngay khi bấm Export (không phụ thuộc blur/mất focus của etOutputName)
                            shareViewModel.saveOutputNamePattern(etOutputName.text?.toString().orEmpty().trim())
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
            }

            btnOpenGallery.apply {
                this.isInvisible = true
                setOnClickListener {
                    openGallery()
                }
            }

            btnShareZip.apply {
                this.isInvisible = true
                setOnClickListener {
                    openShareZip()
                }
            }

            btnBatchCaptions.setOnClickListener {
                BatchCaptionBSDialogFragment.safetyShow(childFragmentManager)
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

            etOutputName.setText(shareViewModel.outputNamePattern)
            etOutputName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    shareViewModel.saveOutputNamePattern(etOutputName.text?.toString().orEmpty().trim())
                }
            }

            atvConflictPolicy.also {
                val conflictPolicyLabels = arrayOf(
                    getString(R.string.conflict_policy_keep_both),
                    getString(R.string.conflict_policy_rename_version),
                    getString(R.string.conflict_policy_overwrite),
                    getString(R.string.conflict_policy_skip)
                )
                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.simple_dropdown_item_1line,
                    conflictPolicyLabels
                )
                it.setAdapter(adapter)
                it.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.bg_dropdown_popup))
                val curIdx = conflictPolicyValues.indexOf(shareViewModel.conflictPolicy).coerceAtLeast(0)
                it.setText(conflictPolicyLabels[curIdx], false)
                it.setOnItemClickListener { _, _, index, _ ->
                    val selectedPolicy = conflictPolicyValues.getOrElse(index) { com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH }
                    shareViewModel.saveConflictPolicy(selectedPolicy)
                }
            }

            flQuality.isVisible = supportsQuality(shareViewModel.outputFormat)
            slideQuality.isVisible = supportsQuality(shareViewModel.outputFormat)

            rvResult.apply {
                adapter = SaveImageListAdapter(
                    context = requireContext(),
                    scope = viewLifecycleOwner.lifecycleScope,
                    generatePreview = { imageInfo, index ->
                        shareViewModel.generateExportPreview(requireActivity().contentResolver, imageInfo, index)
                    },
                    estimateOutput = { width, height ->
                        shareViewModel.estimateExportOutput(width, height)
                    }
                ).also {
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

            tvResult.text = exportCountText(theAdapter)

            slideQuality.apply {
                val activeColor = com.google.android.material.color.MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorPrimary,
                    android.graphics.Color.BLACK
                )
                val inactiveColor = com.google.android.material.color.MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    android.graphics.Color.LTGRAY
                )
                trackActiveTintList = android.content.res.ColorStateList.valueOf(activeColor)
                trackInactiveTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
                thumbTintList = android.content.res.ColorStateList.valueOf(activeColor)
                value = compressLevel
                val baseDesc = getString(R.string.dialog_save_config_quality)
                contentDescription = "$baseDesc: ${compressLevel.toInt()}"
                addOnChangeListener { _, value, _ ->
                    shareViewModel.saveOutput(shareViewModel.outputFormat, value.toInt())
                    tvQualityValue.text = value.toInt().toString()
                    contentDescription = "$baseDesc: ${value.toInt()}"
                }
            }

            shareViewModel.saveProcess.observe(viewLifecycleOwner) {
                theAdapter.updateJobState(it)
                if (it?.jobState is JobState.Success || it?.jobState is JobState.Failure) {
                    tvResult.text = exportCountText(theAdapter)
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
        // ENH-01 AC1: app có thể đã bị kill giữa lúc export (batch vẫn sống sót qua WorkManager) —
        // bắt lại đúng trạng thái đang chạy nếu có, trước khi đọc saveResult hiện tại.
        shareViewModel.reattachExportWorkIfRunning()
        setUpLoadingView(shareViewModel.saveResult.value)
    }

    private fun setUpLoadingView(
        saveResult: Result<*>?
    ) {
        when (saveResult?.code) {
            MainViewModel.TYPE_SAVING -> {
                binding.btnSave.apply {
                    // ENH-01 AC2: vẫn bấm được — dùng để huỷ batch export giữa chừng.
                    isEnabled = true
                    text = getString(R.string.dialog_save_cancel)
                }
                binding.btnOpenGallery.isInvisible = true
                binding.btnShareZip.isInvisible = true
                binding.atvFormat.isEnabled = false
                binding.slideQuality.isEnabled = false
                binding.menuFormat.isEnabled = false
                (dialog as BottomSheetDialog).behavior.isDraggable = false
                isCancelable = false
            }

            MainViewModel.TYPE_JOB_FINISH -> {
                // BUG-33: Kiểm tra xem có ít nhất 1 ảnh thành công (shareUri != null) hay không
                val successfulList = shareViewModel.imageList.value?.first?.filter { it.shareUri != null } ?: emptyList()
                val hasSuccess = successfulList.isNotEmpty()
                binding.btnSave.apply {
                    isEnabled = hasSuccess
                    text = getString(R.string.share)
                }
                TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
                binding.btnOpenGallery.isInvisible = !hasSuccess
                binding.btnShareZip.isInvisible = !hasSuccess
                binding.btnShareZip.isEnabled = hasSuccess
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
                binding.btnShareZip.isInvisible = true
                binding.atvFormat.isEnabled = true
                binding.slideQuality.isEnabled = true
                binding.menuFormat.isEnabled = true
                (dialog as BottomSheetDialog).behavior.isDraggable = true
                isCancelable = true
                val theAdapter = binding.rvResult.adapter as SaveImageListAdapter
                binding.tvResult.text = exportCountText(theAdapter)
            }
        }
    }

    private fun openGallery() {
        // BUG-33: Tìm ảnh thành công ĐẦU TIÊN (có shareUri != null), không lấy index 0 thô
        val list = shareViewModel.imageList.value?.first ?: return
        val successfulItem = list.firstOrNull { it.shareUri != null } ?: run {
            Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val outputUri = successfulItem.shareUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(outputUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), R.string.share_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openShare() {
        // BUG-33: Chỉ lấy các ảnh có shareUri hợp lệ; nếu toàn bộ batch fail thì không mở intent rỗng
        val list = shareViewModel.imageList.value?.first ?: emptyList()
        val successfulUris = ArrayList(list.mapNotNull { it.shareUri })
        if (successfulUris.isEmpty()) {
            Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent().apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (successfulUris.size == 1) {
            val outputUri = successfulUris.first()
            intent.apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, outputUri)
                clipData = android.content.ClipData.newUri(requireContext().contentResolver, "Image", outputUri)
            }
        } else {
            intent.apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, successfulUris)
                val clipData = android.content.ClipData("Images", arrayOf("image/*"), android.content.ClipData.Item(successfulUris[0]))
                for (i in 1 until successfulUris.size) {
                    clipData.addItem(android.content.ClipData.Item(successfulUris[i]))
                }
                this.clipData = clipData
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                getString(R.string.share_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openShareZip() {
        val list = shareViewModel.imageList.value?.first ?: emptyList()
        val successfulUris = list.mapNotNull { it.shareUri }
        if (successfulUris.isEmpty()) {
            Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnShareZip.isEnabled = false
        Toast.makeText(requireContext(), R.string.zipping_images, Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val context = requireContext().applicationContext
            val zipDir = ExportZipHelper.getZipCacheDir(context)
            FileUtils.cleanOldTempFiles(zipDir, maxRetainedFiles = 1, maxAgeMs = 30 * 60 * 1000L)
            val zipFile = File(zipDir, "watermark_export_temp_${System.currentTimeMillis()}.zip")
            val resultFile = ExportZipHelper.createZipArchive(context.contentResolver, successfulUris, zipFile)

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    binding.btnShareZip.isEnabled = true
                }
                if (resultFile != null && resultFile.exists()) {
                    try {
                        val zipUri = ExportZipHelper.getShareableZipUri(context, resultFile)
                        val shareIntent = ExportZipHelper.createShareZipIntent(context, zipUri)
                        startActivity(shareIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            context,
                            getString(R.string.share_error, e.message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(context, R.string.share_zip_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun performOpenGallery() = openGallery()

    @androidx.annotation.VisibleForTesting
    internal fun performOpenShare() = openShare()

    @androidx.annotation.VisibleForTesting
    internal fun performOpenShareZip(zipFileOverride: File? = null) {
        val list = shareViewModel.imageList.value?.first ?: emptyList()
        val successfulUris = list.mapNotNull { it.shareUri }
        if (successfulUris.isEmpty()) {
            Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val context = requireContext().applicationContext
        val zipDir = ExportZipHelper.getZipCacheDir(context)
        val zipFile = zipFileOverride ?: File(zipDir, "watermark_export_temp_${System.currentTimeMillis()}.zip")
        val resultFile = if (zipFile.exists() && zipFileOverride != null) {
            zipFile
        } else {
            ExportZipHelper.createZipArchive(context.contentResolver, successfulUris, zipFile)
        }
        if (resultFile != null && resultFile.exists()) {
            try {
                val zipUri = ExportZipHelper.getShareableZipUri(context, resultFile)
                val shareIntent = ExportZipHelper.createShareZipIntent(context, zipUri)
                startActivity(shareIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    getString(R.string.share_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(context, R.string.share_zip_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun performSetUpLoadingView(result: Result<*>?) = setUpLoadingView(result)

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
