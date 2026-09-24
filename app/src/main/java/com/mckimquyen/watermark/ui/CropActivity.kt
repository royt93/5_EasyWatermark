package com.mckimquyen.watermark.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.ActivityCropBinding
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * FEAT-16: crop tỉ lệ chuẩn + straighten trước khi watermark — full-screen (mirror
 * [SignatureActivity]), trả kết quả qua [ActivityResultContracts.StartActivityForResult] giống
 * `signatureLauncher` trong `MainActivity`. Chỉ nhận [EXTRA_IMAGE_URI], luôn bắt đầu từ trạng
 * thái Free/0° (không khôi phục crop/rotation cũ nếu mở lại — xem ghi chú trong ticket).
 */
@AndroidEntryPoint
class CropActivity : BaseActivity() {

    private lateinit var binding: ActivityCropBinding
    private var imageUri: Uri = Uri.EMPTY

    private data class RatioOption(val labelResId: Int, val ratio: Float?)

    private val ratioOptions = listOf(
        RatioOption(R.string.crop_ratio_free, null),
        RatioOption(R.string.crop_ratio_square, 1f / 1f),
        RatioOption(R.string.crop_ratio_4_5, 4f / 5f),
        RatioOption(R.string.crop_ratio_16_9, 16f / 9f),
        RatioOption(R.string.crop_ratio_9_16, 9f / 16f),
        RatioOption(R.string.crop_ratio_3_4, 3f / 4f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }
        imageUri = Uri.parse(uriString)

        applyInsets()
        setupToolbar()
        setupRatioChips()
        setupStraightenSlider()
        setupApplyButton()
        // BUG phát hiện qua audit: loadImage() decode bất đồng bộ — nếu user bấm Áp dụng TRƯỚC
        // khi decode xong, CropOverlayView chưa có bitmap nên computeCropRect()/rotationDegrees
        // âm thầm trả về null/0f (không crop/không xoay) dù user đã chọn tỉ lệ/góc xoay, KHÔNG
        // có thông báo lỗi nào — mất đúng mục đích tính năng. Khoá nút tới khi ảnh sẵn sàng.
        binding.btnApplyCrop.isEnabled = false
        loadImage()
    }

    private fun applyInsets() {
        val baseBottomPadding = binding.llBottomControls.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBars = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.toolbar.setPadding(0, statusBars.top, 0, 0)
            binding.llBottomControls.setPadding(
                binding.llBottomControls.paddingLeft,
                binding.llBottomControls.paddingTop,
                binding.llBottomControls.paddingRight,
                baseBottomPadding + navBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRatioChips() {
        // ChipGroup theo dõi chip đang chọn qua `id` — PHẢI gán id riêng biệt (View.generateViewId())
        // cho từng chip trước khi add, nếu không mọi chip đều View.NO_ID khiến checkedIds sai.
        ratioOptions.forEachIndexed { index, option ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(option.labelResId)
                isCheckable = true
                isChecked = index == 0
                setEnsureMinTouchTargetSize(true)
            }
            binding.cgRatio.addView(chip)
        }
        binding.cgRatio.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedIndex = checkedIds.firstOrNull()?.let { group.indexOfChild(group.findViewById(it)) } ?: 0
            binding.cropOverlayView.setAspectRatio(ratioOptions.getOrNull(checkedIndex)?.ratio)
        }
    }

    private fun setupStraightenSlider() {
        binding.sliderStraighten.addOnChangeListener { _, value, _ ->
            binding.cropOverlayView.setRotationDegrees(value)
        }
    }

    private fun setupApplyButton() {
        binding.btnApplyCrop.setOnClickListener {
            val cropRect: RectF? = binding.cropOverlayView.computeCropRect()
            val rotationDegrees = binding.cropOverlayView.computeRotationDegrees()
            val resultIntent = Intent().apply {
                putExtra(EXTRA_RESULT_URI, imageUri.toString())
                putExtra(EXTRA_RESULT_CROP_RECT, cropRect)
                putExtra(EXTRA_RESULT_ROTATION, rotationDegrees)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadImage() {
        lifecycleScope.launch {
            val result = decodeBitmapFromUri(this@CropActivity, contentResolver, imageUri, EDIT_DECODE_MAX_LONG_EDGE)
            val bitmap = result.data?.bitmap
            if (result.isFailure() || bitmap == null) {
                finish()
                return@launch
            }
            binding.cropOverlayView.setImageBitmap(bitmap)
            binding.btnApplyCrop.isEnabled = true
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_RESULT_URI = "extra_result_uri"
        const val EXTRA_RESULT_CROP_RECT = "extra_result_crop_rect"
        const val EXTRA_RESULT_ROTATION = "extra_result_rotation"
        private const val EDIT_DECODE_MAX_LONG_EDGE = 2048

        fun createIntent(context: Context, uri: Uri): Intent =
            Intent(context, CropActivity::class.java).putExtra(EXTRA_IMAGE_URI, uri.toString())
    }
}
