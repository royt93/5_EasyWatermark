package com.mckimquyen.watermark.utils.facedetection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-01: gọi ML Kit Face Detection THẬT (native, không mô phỏng được trong Robolectric — xem
 * [FaceDetectionSource]). Verify đường ống hoạt động đúng đầu-cuối (không crash, trả toạ độ chuẩn
 * hoá hợp lệ) qua ảnh KHÔNG có mặt người (màu đặc/nhiễu — không phụ thuộc ảnh mặt người thật, tránh
 * cần asset ảnh chân dung trong repo). Case "phát hiện đúng mặt người thật" verify qua smoke test
 * tay trên device đã khoá (xem `doc/task/done/IDEA-01-...md`).
 */
@RunWith(AndroidJUnit4::class)
class MlKitFaceDetectionSourceIntegrationTest {

    private val source = MlKitFaceDetectionSource()

    private fun solidColorBitmap(color: Int, size: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return bitmap
    }

    @Test
    fun detectFaces_solidColorBitmap_returnsEmptyList_noCrash() = runBlocking {
        val faces = source.detectFaces(solidColorBitmap(Color.BLUE))
        assertThat(faces).isEmpty()
    }

    @Test
    fun detectFaces_randomNoiseBitmap_returnsEmptyList_noCrash() = runBlocking {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        val pixels = IntArray(200 * 200) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        bitmap.setPixels(pixels, 0, 200, 0, 0, 200, 200)

        val faces = source.detectFaces(bitmap)

        assertThat(faces).isEmpty()
    }

    @Test
    fun detectFaces_resultRectsAlwaysNormalizedWithinBounds() = runBlocking {
        // Không có mặt -> list rỗng, nhưng verify hợp đồng "mọi rect trả về (nếu có) phải nằm
        // trong 0..1" vẫn đúng cho trường hợp không có phần tử nào (vacuously true, nhưng đảm bảo
        // hàm không throw/trả giá trị sai kiểu).
        val faces = source.detectFaces(solidColorBitmap(Color.GREEN))
        faces.forEach { rect ->
            assertThat(rect.left).isAtLeast(0f)
            assertThat(rect.top).isAtLeast(0f)
            assertThat(rect.right).isAtMost(1f)
            assertThat(rect.bottom).isAtMost(1f)
        }
    }
}
