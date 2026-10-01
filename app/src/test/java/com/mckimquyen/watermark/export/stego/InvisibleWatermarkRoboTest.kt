package com.mckimquyen.watermark.export.stego

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ENH-41: unit test (JVM qua Robolectric, vì `Bitmap`/`ColorSpace` bị stub rỗng trong plain JUnit —
 * xem ghi chú `returnDefaultValues` trong memory) cho nhánh giữ `ColorSpace` của `embed()`.
 *
 * `InvisibleWatermarkIntegrationTest` (androidTest) đã bao phủ round-trip embed/extract qua codec
 * Skia thật; test này tập trung riêng vào đúng hành vi ENH-41 sửa — không lặp lại phần đó.
 */
@RunWith(RobolectricTestRunner::class)
class InvisibleWatermarkRoboTest {

    private val owner = 0xC0FFEE

    private fun texturedBitmap(colorSpace: ColorSpace?): Bitmap {
        val width = 64
        val height = 64
        val bitmap = if (colorSpace != null) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true, colorSpace)
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        // Ảnh phẳng không đủ capacity cho TOTAL_BITS nếu quá nhỏ hoặc quá đồng nhất — dùng pixel
        // không đồng nhất để chắc chắn vượt ngưỡng `StegoCodec.capacityBits` (64x64 = TOTAL_BITS=64).
        val pixels = IntArray(width * height) { i -> Color.rgb(i % 256, (i * 3) % 256, (i * 7) % 256) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    @Config(sdk = [26])
    @Test
    fun `API26 tro len - embed giu dung ColorSpace Display P3 cua anh nguon`() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val source = texturedBitmap(p3)

        val stamped = InvisibleWatermark.embed(source, owner)

        assertThat(stamped).isNotNull()
        assertThat(stamped!!.colorSpace).isEqualTo(p3)
    }

    /**
     * Finding review: chỉ assert `.colorSpace` không chứng minh payload còn nguyên sau khi
     * `getPixels()`/`setPixels()` đi qua bitmap gắn ColorSpace khác sRGB — round-trip `extract()`
     * phải vẫn ra đúng ownerId, không lệch/vỡ do nhánh dựng bitmap mới.
     */
    @Config(sdk = [26])
    @Test
    fun `API26 tro len - anh Display P3 van extract dung ownerId sau khi giu ColorSpace`() {
        val source = texturedBitmap(ColorSpace.get(ColorSpace.Named.DISPLAY_P3))

        val stamped = InvisibleWatermark.embed(source, owner)
        val result = InvisibleWatermark.extract(stamped!!)

        assertThat(result).isNotNull()
        assertThat(result!!.ownerId).isEqualTo(owner)
    }

    /**
     * Finding review: ảnh sRGB là trường hợp phổ biến nhất — PHẢI đi nhánh cũ (không alloc bitmap
     * mới + setPixels() thừa), không chỉ "không đổi `.colorSpace`". Xác nhận bằng kết quả giống hệt
     * nhánh cũ: cùng kích thước, extract vẫn đúng, `.colorSpace` vẫn sRGB như trước ENH-41.
     */
    @Config(sdk = [26])
    @Test
    fun `API26 tro len - anh sRGB di nhanh cu, khong doi hanh vi`() {
        val source = texturedBitmap(ColorSpace.get(ColorSpace.Named.SRGB))

        val stamped = InvisibleWatermark.embed(source, owner)

        assertThat(stamped).isNotNull()
        assertThat(stamped!!.colorSpace).isEqualTo(ColorSpace.get(ColorSpace.Named.SRGB))
        assertThat(InvisibleWatermark.extract(stamped)?.ownerId).isEqualTo(owner)
    }

    // Review finding (/code-review --level high sau khi push): ColorSpace.Rgb dựng từ hàm transfer
    // tuỳ ý (getTransferParameters() == null, ví dụ profile ProPhoto RGB/scanner/Photoshop export
    // thật) khiến Bitmap.createBitmap(w,h,config,alpha,cs) ném IllegalArgumentException trên Android
    // THẬT. KHÔNG test được bằng Robolectric ở đây: shadow của Robolectric cho overload này không
    // validate transferParameters như native Android — test từng viết ở đây PASS cả khi chưa có
    // try/catch fallback (false-negative, không phát hiện được lỗi thật). Bằng chứng thật nằm ở
    // `InvisibleWatermarkIntegrationTest#colorSpaceLutBased...` (androidTest, chạy trên Skia thật).

    @Config(sdk = [24])
    @Test
    fun `API duoi 26 - khong co overload ColorSpace, hanh vi cu giu nguyen khong crash`() {
        // API 24-25 không có `source.colorSpace`/overload nhận ColorSpace — xác nhận nhánh fallback
        // cũ chạy đúng, không ném NoSuchMethodError (regression ENH-41 từng mắc phải lúc code review).
        check(Build.VERSION.SDK_INT == 24)
        val source = texturedBitmap(colorSpace = null)

        val stamped = InvisibleWatermark.embed(source, owner)

        assertThat(stamped).isNotNull()
        assertThat(stamped!!.width).isEqualTo(source.width)
        assertThat(stamped.height).isEqualTo(source.height)
        assertThat(InvisibleWatermark.extract(stamped)?.ownerId).isEqualTo(owner)
    }
}
