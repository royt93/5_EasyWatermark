package com.mckimquyen.watermark.utils.textdetection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-14 — test QUAN TRỌNG NHẤT của phần phát hiện text: chạy ML Kit Text Recognition THẬT
 * (native, không mô phỏng được trong Robolectric — xem [SensitiveTextSource]), chứng minh cả 2 nửa
 * của pipeline hoạt động đúng: OCR đọc được chữ THẬT trong ảnh, và bộ lọc nhạy cảm lọc đúng dòng.
 */
@RunWith(AndroidJUnit4::class)
class MlKitSensitiveTextSourceIntegrationTest {

    private val source = MlKitSensitiveTextSource()

    /** Ảnh trắng có 1 dòng chữ đen rõ nét — đủ để ML Kit OCR đọc chính xác. */
    private fun bitmapWithText(text: String, width: Int = 600, height: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText(text, 20f, height / 2f, paint)
        return bitmap
    }

    @Test
    fun detectSensitiveRegions_emailThatIsVisible_isDetected() = runBlocking {
        val bitmap = bitmapWithText("roy.studio@gmail.com")
        try {
            val regions = source.detectSensitiveRegions(bitmap)
            assertThat(regions).isNotEmpty()
            regions.forEach { rect ->
                assertThat(rect.left).isAtLeast(0f)
                assertThat(rect.top).isAtLeast(0f)
                assertThat(rect.right).isAtMost(1f)
                assertThat(rect.bottom).isAtMost(1f)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectSensitiveRegions_phoneNumberThatIsVisible_isDetected() = runBlocking {
        val bitmap = bitmapWithText("0912345678")
        try {
            val regions = source.detectSensitiveRegions(bitmap)
            assertThat(regions).isNotEmpty()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectSensitiveRegions_ordinaryTextWithoutEmailOrPhone_isNotDetected() = runBlocking {
        val bitmap = bitmapWithText("Watermark Creator")
        try {
            val regions = source.detectSensitiveRegions(bitmap)
            assertThat(regions).isEmpty()
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectSensitiveRegions_blankImage_returnsEmptyList_noCrash() = runBlocking {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        try {
            val regions = source.detectSensitiveRegions(bitmap)
            assertThat(regions).isEmpty()
        } finally {
            bitmap.recycle()
        }
    }

    // BUG phát hiện qua smoke test thật trên màn hình hẹp (720px, xem `doc/task/done/IDEA-14-...md`):
    // email dài bị UI TỰ NGẮT DÒNG giữa chừng (vd danh sách tài khoản trong Settings) — không dòng
    // nào riêng lẻ là email hợp lệ, ban đầu bị bỏ sót hoàn toàn. Đã fix bằng fallback ghép cả block
    // trong [MlKitSensitiveTextSource]. KHÔNG viết được test JVM/androidTest tái hiện đáng tin cậy
    // cho case này — đã thử dựng 3 dòng text liền kề trên canvas tổng hợp, nhưng heuristic gộp
    // block của chính ML Kit (dựa trên bố cục UI thật, không kiểm soát được từ code test) không gộp
    // các dòng vẽ tay rời rạc thành 1 block như trên màn hình thật, khiến test giả không đại diện
    // đúng hành vi cần verify. Giới hạn môi trường tương tự đã ghi nhận nhiều lần trong repo (vd
    // `CropActivityRoboTest` không test được case decode lỗi) — verify bằng smoke test thật thay vì
    // ép 1 test không phản ánh đúng thực tế.
}
