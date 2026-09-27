package com.mckimquyen.watermark.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.RedactionSuggestion
import com.mckimquyen.watermark.databinding.ActivitySmartRedactionBinding
import com.mckimquyen.watermark.utils.bitmap.applyCropAndRotate
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import com.mckimquyen.watermark.utils.ktx.toast
import com.mckimquyen.watermark.utils.textdetection.SensitiveTextSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * IDEA-14: phát hiện email/SĐT + mặt người trong ảnh, đề xuất khoanh vùng, user tap bật/tắt từng
 * vùng rồi Apply → trả danh sách vùng CONFIRMED cho `MainActivity` để nhúng mosaic lúc export.
 *
 * Mirror cấu trúc [CropActivity]: full-screen, `@AndroidEntryPoint`, trả kết quả qua
 * `ActivityResultContracts.StartActivityForResult`, luôn bắt đầu lại từ đầu (không khôi phục lựa
 * chọn cũ nếu mở lại — cùng lý do đơn giản hoá với Crop).
 */
@AndroidEntryPoint
class SmartRedactionActivity : BaseActivity() {

    private lateinit var binding: ActivitySmartRedactionBinding
    private var imageUri: Uri = Uri.EMPTY

    @Inject
    lateinit var faceDetectionSource: FaceDetectionSource

    @Inject
    lateinit var sensitiveTextSource: SensitiveTextSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartRedactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }
        imageUri = Uri.parse(uriString)
        val cropRect = IntentCompat.getParcelableExtra(intent, EXTRA_CROP_RECT, RectF::class.java)
        val rotationDegrees = intent.getFloatExtra(EXTRA_ROTATION, 0f)

        applyInsets()
        setupToolbar()
        setupApplyButton()
        loadImageAndDetect(cropRect, rotationDegrees)
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

    private fun setupApplyButton() {
        binding.btnApplyRedaction.setOnClickListener {
            val confirmedRects = ArrayList(
                binding.redactionOverlayView.currentSuggestions
                    .filter { it.confirmed }
                    .map { it.rect }
            )
            val resultIntent = Intent().apply {
                putExtra(EXTRA_RESULT_URI, imageUri.toString())
                putParcelableArrayListExtra(EXTRA_RESULT_REDACTION_RECTS, confirmedRects)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun loadImageAndDetect(cropRect: RectF?, rotationDegrees: Float) {
        lifecycleScope.launch {
            // BUG phát hiện qua smoke test thật: ảnh nhận qua ACTION_SEND (chia sẻ từ app khác) chỉ
            // mang quyền đọc URI TẠM THỜI, gắn với Activity/Intent ĐẦU TIÊN nhận nó (MainActivity)
            // — không tự động chuyển sang Activity MỚI (ở đây) dù cùng truyền uri qua Intent extra.
            // `ContentResolver.openInputStream()` ném `SecurityException` thẳng, không được
            // `decodeBitmapFromUri` bắt (hàm dùng chung với CropActivity, có cùng lỗ hổng — ngoài
            // phạm vi sửa ở đây, chỉ chặn crash cho đúng màn hình mới này). Không crash, coi như
            // decode lỗi + thông báo, giống hệt nhánh "không mở được ảnh" bên dưới.
            val decodeResult = try {
                decodeBitmapFromUri(this@SmartRedactionActivity, contentResolver, imageUri, EDIT_DECODE_MAX_LONG_EDGE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                AppLog.w(TAG, "loadImageAndDetect: no permission to read $imageUri", e)
                toast(R.string.error_file_not_found)
                finish()
                return@launch
            }
            val decodedBitmap = decodeResult.data?.bitmap
            if (decodeResult.isFailure() || decodedBitmap == null) {
                toast(R.string.error_file_not_found)
                finish()
                return@launch
            }
            // Áp ĐÚNG crop/rotation user đã chỉnh (FEAT-16) trước khi detect, để toạ độ vùng đề
            // xuất khớp với khung ảnh sẽ thật sự export — không phải khung ảnh gốc chưa chỉnh.
            val bitmap = applyCropAndRotate(decodedBitmap, rotationDegrees, cropRect)
            binding.redactionOverlayView.setImageBitmap(bitmap)

            val faceRegions = async(Dispatchers.Default) {
                runCatchingDetection { faceDetectionSource.detectFaces(bitmap) }
            }
            val textRegions = async(Dispatchers.Default) {
                runCatchingDetection { sensitiveTextSource.detectSensitiveRegions(bitmap) }
            }

            val suggestions = faceRegions.await().map { RedactionSuggestion(it, RedactionSuggestion.Source.FACE) } +
                textRegions.await().map { RedactionSuggestion(it, RedactionSuggestion.Source.TEXT) }

            binding.redactionOverlayView.setSuggestions(suggestions)
            binding.pbScanning.isVisible = false
            binding.btnApplyRedaction.isEnabled = true
            binding.tvStatus.text = if (suggestions.isEmpty()) {
                getString(R.string.redaction_no_regions_found)
            } else {
                getString(R.string.redaction_regions_found, suggestions.size)
            }
        }
    }

    /** 1 nguồn lỗi (face hoặc text) không được chặn nguồn còn lại — coi như rỗng, không crash màn hình. */
    private suspend fun <T> runCatchingDetection(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLog.w(TAG, "Smart redaction detection failed", e)
        emptyList()
    }

    companion object {
        private const val TAG = "SmartRedactionActivity"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_CROP_RECT = "extra_crop_rect"
        const val EXTRA_ROTATION = "extra_rotation"
        const val EXTRA_RESULT_URI = "extra_result_uri"
        const val EXTRA_RESULT_REDACTION_RECTS = "extra_result_redaction_rects"
        private const val EDIT_DECODE_MAX_LONG_EDGE = 2048

        fun createIntent(context: Context, uri: Uri, cropRect: RectF?, rotationDegrees: Float): Intent =
            Intent(context, SmartRedactionActivity::class.java)
                .putExtra(EXTRA_IMAGE_URI, uri.toString())
                .putExtra(EXTRA_CROP_RECT, cropRect)
                .putExtra(EXTRA_ROTATION, rotationDegrees)
    }
}
