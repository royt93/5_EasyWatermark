package com.mckimquyen.watermark.ui.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

/**
 * IDEA-14: bug thật phát hiện qua smoke test trên device — `MainActivity.viewModel.selectedImage.observe`
 * gọi CẢ `ivPhoto.config = ...` LẪN `ivPhoto.updateUri(false, imageInfoMoi)` cạnh nhau khi user quay
 * lại từ `SmartRedactionActivity`. `config` setter dùng `curImageInfo` (biến CHIA SẺ, có thể đã bị
 * ghi bởi lần `applyNewConfig` TRƯỚC ĐÓ) để so geometryChanged, khiến vùng redaction MỚI bị nhận
 * nhầm là "không đổi" → mosaic bị bỏ qua hoàn toàn dù đã bấm Apply, không lỗi/không crash, chỉ ảnh
 * SAI (đúng loại bug nguy hiểm nhất: âm thầm sai, không ai biết mà báo). Test này khoá đúng bất biến:
 * đổi `redactionRectsNormalized` qua [WaterMarkImageView.updateUri] PHẢI luôn áp mosaic mới.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewRedactionRoboTest {

    class SimpleProvider : ContentProvider() {
        lateinit var file: File
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private fun setupRealImageUri(authority: String): Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Nền TRẮNG ĐẶC — mọi pixel giống hệt nhau trước khi che, nên "còn nguyên hay đã mosaic"
        // chỉ có thể phân biệt bằng cách so KÍCH THƯỚC bitmap thật (đã redact tạo bitmap MỚI) thay
        // vì màu sắc (ảnh nền phẳng mosaic ra vẫn cùng màu, không đủ để phân biệt qua pixel).
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // Vẽ 1 khối ĐEN góc trên-trái để có tín hiệu pixel rõ ràng phân biệt được redact hay chưa.
        canvas.drawRect(0f, 0f, 60f, 60f, android.graphics.Paint().apply { color = Color.BLACK })
        val file = File.createTempFile("wmiv_redaction_test", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        val provider = Robolectric.setupContentProvider(SimpleProvider::class.java, authority)
        provider.file = file
        return Uri.parse("content://$authority/test.jpg")
    }

    private fun newMeasuredView(): WaterMarkImageView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WaterMarkImageView(context)
        val spec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, 200, 200)
        return view
    }

    private fun textConfig(text: String = "wm") = WaterMark(
        text = text,
        textSize = 20f,
        textColor = Color.WHITE,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = Uri.EMPTY,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false
    )

    private fun waitUntilTrue(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun updateUri_voiRedactionRectsMoi_luonApMosaic_duCauHinhKhongDoi() {
        val uri = setupRealImageUri("wmiv.redaction.test")
        val view = newMeasuredView()
        val config = textConfig()

        // Bước 1: load lần đầu KHÔNG có redaction — mirror đúng thứ tự MainActivity thật
        // (updateUri trước khi config != null, rồi set config để kích hoạt decode đầu tiên).
        view.updateUri(true, ImageInfo(uri))
        view.config = config
        waitUntilTrue { (view.drawable as? BitmapDrawable)?.bitmap != null }

        val bitmapBeforeRedaction = (view.drawable as BitmapDrawable).bitmap
        assertThat(bitmapBeforeRedaction).isNotNull()

        // Bước 2: mirror ĐÚNG race đã gây bug thật trong MainActivity — set lại `config` bằng giá
        // trị dữ liệu HỆT NHAU (kích hoạt nhánh "SKIP, no change" của config setter) NGAY TRƯỚC khi
        // gọi updateUri với ImageInfo MỚI có redactionRectsNormalized.
        view.config = config.copy()
        val imageInfoWithRedaction = ImageInfo(uri, redactionRectsNormalized = listOf(RectF(0f, 0f, 0.3f, 0.3f)))
        view.updateUri(false, imageInfoWithRedaction)
        waitUntilTrue {
            val bmp = (view.drawable as? BitmapDrawable)?.bitmap
            bmp != null && bmp !== bitmapBeforeRedaction
        }

        val bitmapAfterRedaction = (view.drawable as BitmapDrawable).bitmap
        // Bất biến quan trọng nhất: PHẢI là bitmap MỚI (đã qua applyRedaction tạo bản sao) — nếu
        // bug tái diễn, đây vẫn là CHÍNH bitmapBeforeRedaction (bỏ qua áp mosaic hoàn toàn).
        assertThat(bitmapAfterRedaction).isNotSameInstanceAs(bitmapBeforeRedaction)
    }
}
