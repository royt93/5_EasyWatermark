package com.mckimquyen.watermark.export.stego

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * IDEA-02 — test QUAN TRỌNG NHẤT của ticket: chạy codec JPEG THẬT của Android (Skia), chứng minh
 * lớp watermark ẩn sống sót qua đúng thứ mà nó sinh ra để chống lại.
 *
 * Unit test trên JVM dùng [JpegQuantizationSimulator] mô phỏng bước lượng tử hoá; test này xác nhận
 * lại bằng bộ nén thật trên thiết bị, vì đó mới là thứ chạy trong sản phẩm.
 */
@RunWith(AndroidJUnit4::class)
class InvisibleWatermarkIntegrationTest {

    private val owner = "Roy Studio"

    /** Ảnh có kết cấu thật (gradient + hình khối), không phải nền phẳng vốn quá dễ. */
    private fun texturedBitmap(width: Int = 512, height: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(7)
        for (y in 0 until height step 16) {
            val shade = 40 + (y * 150 / height)
            canvas.drawRect(
                0f,
                y.toFloat(),
                width.toFloat(),
                (y + 16).toFloat(),
                Paint().apply { color = Color.rgb(shade, shade * 2 / 3, 200 - shade / 2) }
            )
        }
        repeat(40) {
            canvas.drawCircle(
                random.nextInt(width).toFloat(),
                random.nextInt(height).toFloat(),
                random.nextInt(10, 50).toFloat(),
                Paint().apply {
                    color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    alpha = 120
                }
            )
        }
        return bitmap
    }

    /** ENH-39/40: nền phẳng tuyệt đối (solid color) — ảnh sản phẩm nền trắng, slide, vector art xuất PNG. */
    private fun flatBitmap(width: Int, height: Int, gray: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(gray, gray, gray))
        return bitmap
    }

    /** Nén rồi giải nén bằng chính Skia — đúng đường mà ảnh đi qua khi chia sẻ lên mạng. */
    private fun recompress(bitmap: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    @Test
    fun nhungRoiDocLaiNgay_traVeDungChuSoHuu() {
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))
        try {
            assertThat(stamped).isNotNull()
            val result = InvisibleWatermark.extract(stamped!!)

            assertThat(result).isNotNull()
            assertThat(result!!.ownerId).isEqualTo(StegoPayload.ownerIdOf(owner))
            assertThat(result.confidence).isAtLeast(StegoPayload.MIN_CONFIDENCE)
        } finally {
            source.recycle()
            stamped?.recycle()
        }
    }

    @Test
    fun songSotQuaNenJpegThat_q85_q75_q70() {
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))!!
        source.recycle()

        try {
            for (quality in intArrayOf(85, 75, 70)) {
                val compressed = recompress(stamped, quality)
                try {
                    val result = InvisibleWatermark.extract(compressed)
                    println("[IDEA-02] Skia JPEG q=$quality → ${if (result != null) "đọc được, confidence ${"%.3f".format(result.confidence)}" else "MẤT"}")
                    assertThat(result).isNotNull()
                    assertThat(result!!.ownerId).isEqualTo(StegoPayload.ownerIdOf(owner))
                } finally {
                    compressed.recycle()
                }
            }
        } finally {
            stamped.recycle()
        }
    }

    @Test
    fun songSotQuaHaiVongNenLienTiep() {
        // Ảnh được chia sẻ lại nhiều lần, mỗi lần là một vòng nén nữa.
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))!!
        source.recycle()

        val once = recompress(stamped, 85)
        val twice = recompress(once, 75)
        try {
            val result = InvisibleWatermark.extract(twice)
            println("[IDEA-02] Skia 2 vòng nén → ${if (result != null) "đọc được" else "MẤT"}")
            assertThat(result?.ownerId).isEqualTo(StegoPayload.ownerIdOf(owner))
        } finally {
            stamped.recycle()
            once.recycle()
            twice.recycle()
        }
    }

    @Test
    fun anhSach_khongBaoNhamCoWatermark() {
        // Tính chất sống còn: ảnh người lạ KHÔNG được sinh ra một chủ sở hữu.
        val clean = texturedBitmap()
        try {
            val result = InvisibleWatermark.extract(clean)
            println("[IDEA-02] Ảnh sạch → ${result?.ownerIdHex() ?: "không đọc ra gì (đúng)"}")
            assertThat(result).isNull()
        } finally {
            clean.recycle()
        }
    }

    @Test
    fun anhSachSauKhiNen_cungKhongBaoNham() {
        val clean = texturedBitmap()
        val compressed = recompress(clean, 75)
        try {
            assertThat(InvisibleWatermark.extract(compressed)).isNull()
        } finally {
            clean.recycle()
            compressed.recycle()
        }
    }

    @Test
    fun chuSoHuuKhacNhau_choIdKhacNhau_khongLanLon() {
        val a = texturedBitmap()
        val b = texturedBitmap()
        val stampedA = InvisibleWatermark.embed(a, StegoPayload.ownerIdOf("Roy Studio"))!!
        val stampedB = InvisibleWatermark.embed(b, StegoPayload.ownerIdOf("Ai Do Khac"))!!
        try {
            val readA = InvisibleWatermark.extract(stampedA)!!
            val readB = InvisibleWatermark.extract(stampedB)!!

            assertThat(readA.ownerId).isNotEqualTo(readB.ownerId)
            assertThat(readA.ownerId).isEqualTo(StegoPayload.ownerIdOf("Roy Studio"))
            assertThat(readB.ownerId).isEqualTo(StegoPayload.ownerIdOf("Ai Do Khac"))
        } finally {
            a.recycle(); b.recycle(); stampedA.recycle(); stampedB.recycle()
        }
    }

    @Test
    fun anhQuaNho_traVeNull_khongNem() {
        val tiny = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            assertThat(InvisibleWatermark.embed(tiny, 12345)).isNull()
            assertThat(InvisibleWatermark.extract(tiny)).isNull()
        } finally {
            tiny.recycle()
        }
    }

    @Test
    fun chatLuongAnh_matThuongKhongPhanBietDuoc() {
        val source = texturedBitmap(256, 256)
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))!!
        try {
            val width = source.width
            val height = source.height
            val before = IntArray(width * height).also { source.getPixels(it, 0, width, 0, 0, width, height) }
            val after = IntArray(width * height).also { stamped.getPixels(it, 0, width, 0, 0, width, height) }

            var sumSq = 0.0
            for (i in before.indices) {
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = ((before[i] shr shift) and 0xFF) - ((after[i] shr shift) and 0xFF)
                    sumSq += (d * d).toDouble()
                }
            }
            val mse = sumSq / (before.size * 3)
            val psnr = 10 * kotlin.math.log10(255.0 * 255.0 / mse)
            println("[IDEA-02] PSNR trên Skia thật: ${"%.1f".format(psnr)} dB")

            // >40 dB là ngưỡng quy ước "mắt thường không phân biệt được" — acceptance criteria #2.
            assertThat(psnr).isGreaterThan(40.0)
        } finally {
            source.recycle()
            stamped.recycle()
        }
    }

    /**
     * ENH-40: nền đen/trắng TUYỆT ĐỐI (RGB 0,0,0 / 255,255,255) qua nén Skia thật ở q=70 (Zalo) —
     * xác nhận lại bằng codec Android thật fix `StegoCodec.compensateRailClipping()` đo được trên
     * JVM simulator (`StegoRobustnessTest`). Case gốc (trước fix) mất watermark hoàn toàn ở mức nén
     * này dù `confidence` vẫn báo tự tin tuyệt đối.
     */
    @Test
    fun nenDenTrangTuyetDoi_vanDocDungQuaNenSkiaThat() {
        for (gray in intArrayOf(0, 255)) {
            val source = flatBitmap(512, 512, gray)
            val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))!!
            source.recycle()

            val compressed = recompress(stamped, 70)
            try {
                val result = InvisibleWatermark.extract(compressed)
                println("[ENH-40] gray=$gray, Skia JPEG q=70 → ${if (result != null) "đọc được" else "MẤT"}")
                assertThat(result).isNotNull()
                assertThat(result!!.ownerId).isEqualTo(StegoPayload.ownerIdOf(owner))
            } finally {
                stamped.recycle()
                compressed.recycle()
            }
        }
    }

    /**
     * Ca đáng giá nhất của cả ticket: nền tảng xoá sạch EXIF (con dấu IDEA-03 chết) nhưng lớp ẩn
     * trong pixel vẫn truy được chủ ảnh. Đây chính là lý do IDEA-02 tồn tại song song với IDEA-03.
     */
    @Test
    fun exifBiXoaSach_lopAnVanCon() {
        val source = texturedBitmap()
        val stamped = InvisibleWatermark.embed(source, StegoPayload.ownerIdOf(owner))!!
        source.recycle()

        // Nén lại = tái mã hoá hoàn toàn, mọi metadata EXIF biến mất.
        val republished = recompress(stamped, 80)
        try {
            val result = InvisibleWatermark.extract(republished)
            assertThat(result).isNotNull()
            assertThat(result!!.ownerId).isEqualTo(StegoPayload.ownerIdOf(owner))
            println("[IDEA-02] Sau khi mất sạch EXIF: vẫn đọc được ${result.ownerIdHex()}")
        } finally {
            stamped.recycle()
            republished.recycle()
        }
    }
}
