package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.AuthenticityKeyStore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * IDEA-03 — test QUAN TRỌNG NHẤT của ticket: chạy Android Keystore thật + Skia thật + androidx
 * `ExifInterface` thật, chứng minh 3 tính chất mà thiết kế dựa vào:
 *
 * 1. Ảnh xuất ra rồi đọc lại → con dấu hợp lệ, hash khớp, đúng khoá thiết bị này.
 * 2. Sửa ảnh (vẽ thêm rồi nén lại) → hash lệch → báo KHÔNG nguyên vẹn.
 * 3. Ghi đè EXIF Copyright sau khi đã đóng dấu → VẪN nguyên vẹn. Đây là lý do
 *    [JpegImageDigest] bỏ qua segment APPn thay vì hash cả file; test này khoá tính chất đó lại.
 *
 * Robolectric không mô phỏng `AndroidKeyStore` nên phần ký/verify bằng khoá thật chỉ kiểm được ở đây.
 */
@RunWith(AndroidJUnit4::class)
class AuthenticityStampIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exportNaming = ExportNaming()

    private fun writeJpeg(name: String, paintExtra: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(40, 90, 160))
            if (paintExtra) {
                drawCircle(100f, 100f, 60f, Paint().apply { color = Color.YELLOW })
            }
        }
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun stampOf(file: File): AuthenticityStamp.Stamp? =
        AuthenticityStamp.parse(ExifInterface(file.absolutePath).getAttribute(ExifInterface.TAG_USER_COMMENT))

    private fun evaluate(file: File): AuthenticityVerifier.Result.Stamped {
        val stamp = requireNotNull(stampOf(file)) { "Không đọc được con dấu trong ${file.name}" }
        val currentHash = requireNotNull(file.inputStream().use { JpegImageDigest.hashImageData(it) })
        return AuthenticityVerifier.evaluate(stamp, currentHash)
    }

    @Test
    fun anhVuaDongDau_docLaiThiNguyenVen_vaDungKhoaMayNay() {
        val file = writeJpeg("idea03_intact.jpg")
        try {
            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy Studio", Bitmap.CompressFormat.JPEG)

            val result = evaluate(file)
            assertThat(result.signatureValid).isTrue()
            assertThat(result.hashMatches).isTrue()
            assertThat(result.intact).isTrue()
            assertThat(result.owner).isEqualTo("Roy Studio")
            assertThat(result.signedByThisDevice).isTrue()
            assertThat(result.keyFingerprint).isNotEmpty()
        } finally {
            file.delete()
        }
    }

    @Test
    fun suaAnhRoiNenLai_thiBaoKhongNguyenVen() {
        val file = writeJpeg("idea03_tampered.jpg")
        try {
            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.JPEG)
            val stamp = requireNotNull(stampOf(file))

            // Sửa ảnh: vẽ thêm hình rồi nén lại, nhưng GIỮ nguyên con dấu cũ trong EXIF — đúng kịch
            // bản kẻ sửa ảnh muốn giữ vẻ "đã xác thực".
            val edited = writeJpeg("idea03_tampered_edited.jpg", paintExtra = true)
            try {
                val editedHash = requireNotNull(edited.inputStream().use { JpegImageDigest.hashImageData(it) })
                val result = AuthenticityVerifier.evaluate(stamp, editedHash)

                assertThat(result.hashMatches).isFalse()
                assertThat(result.intact).isFalse()
                // Chữ ký vẫn đúng (con dấu không bị đụng) — chỉ hash lệch. Phân biệt được 2 ca này.
                assertThat(result.signatureValid).isTrue()
            } finally {
                edited.delete()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun ghiDeExifCopyrightSauKhiDongDau_vanNguyenVen() {
        // Đây là lý do tồn tại của JpegImageDigest: metadata đổi KHÔNG được phá hash.
        val file = writeJpeg("idea03_exif_rewrite.jpg")
        try {
            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.JPEG)
            assertThat(evaluate(file).intact).isTrue()

            exportNaming.applyCopyrightExif(
                file.absolutePath,
                "© 2026 Ai Đó Khác Hoàn Toàn, chuỗi này dài ra để APP1 đổi kích thước",
                Bitmap.CompressFormat.JPEG
            )

            val result = evaluate(file)
            assertThat(result.hashMatches).isTrue()
            assertThat(result.intact).isTrue()
        } finally {
            file.delete()
        }
    }

    @Test
    fun suaNoiDungConDau_thiChuKyKhongConHopLe() {
        val file = writeJpeg("idea03_forged.jpg")
        try {
            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.JPEG)
            val genuine = requireNotNull(stampOf(file))

            // Kẻ giả mạo đổi tên chủ sở hữu nhưng giữ nguyên chữ ký cũ.
            val forged = genuine.copy(owner = "Người Khác")
            val currentHash = requireNotNull(file.inputStream().use { JpegImageDigest.hashImageData(it) })
            val result = AuthenticityVerifier.evaluate(forged, currentHash)

            assertThat(result.hashMatches).isTrue()
            assertThat(result.signatureValid).isFalse()
            assertThat(result.intact).isFalse()
        } finally {
            file.delete()
        }
    }

    /**
     * Đọc con dấu qua InputStream — ĐÚNG đường mà [AuthenticityVerifier.verify] dùng thật (nó mở
     * Uri qua ContentResolver), khác với các test trên đọc bằng đường filePath. Ghi một đường, đọc
     * một đường khác là chỗ bug dễ lọt nhất.
     */
    @Test
    fun docConDauQuaInputStream_giongDuongVerifyThat() {
        val file = writeJpeg("idea03_stream_read.jpg")
        try {
            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.JPEG)

            val viaStream = file.inputStream().use {
                ExifInterface(it).getAttribute(ExifInterface.TAG_USER_COMMENT)
            }
            val viaPath = ExifInterface(file.absolutePath).getAttribute(ExifInterface.TAG_USER_COMMENT)

            assertThat(viaPath).isNotNull()
            assertThat(viaStream).isEqualTo(viaPath)
            assertThat(AuthenticityStamp.parse(viaStream)).isNotNull()
        } finally {
            file.delete()
        }
    }

    /** Toàn bộ [AuthenticityVerifier.verify] chạy thật trên Uri file — sát luồng UI nhất. */
    @Test
    fun verifyQuaUri_traVeNguyenVen() {
        // Ghi vào cacheDir/camera/ vì đó là thư mục FileProvider được khai trong res/xml/filepaths.xml.
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "idea03_verify_uri.jpg")
        try {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.rgb(40, 90, 160))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()

            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.JPEG)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val result = AuthenticityVerifier.verify(context.contentResolver, uri)

            assertThat(result).isInstanceOf(AuthenticityVerifier.Result.Stamped::class.java)
            assertThat((result as AuthenticityVerifier.Result.Stamped).intact).isTrue()
        } finally {
            file.delete()
        }
    }

    @Test
    fun anhKhongCoConDau_thiKhongDocRaGi() {
        val file = writeJpeg("idea03_no_stamp.jpg")
        try {
            assertThat(stampOf(file)).isNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun anhPng_khongDongDau_vaKhongNem() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)
        val file = File(context.cacheDir, "idea03_skip.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            exportNaming.applyAuthenticityExif(file.absolutePath, "Roy", Bitmap.CompressFormat.PNG)

            // Không con dấu, và quan trọng hơn: không ném exception làm đổ export.
            assertThat(stampOf(file)).isNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun khoaThietBiOnDinhGiuaCacLanGoi() {
        // Nếu mỗi lần gọi lại sinh khoá mới thì con dấu cũ sẽ mất vế "đúng khoá máy này".
        val first = AuthenticityKeyStore.publicKeyEncoded()
        val second = AuthenticityKeyStore.publicKeyEncoded()

        assertThat(first).isNotNull()
        assertThat(second).isEqualTo(first)
        assertThat(AuthenticityKeyStore.isOwnKey(requireNotNull(first))).isTrue()
    }

    @Test
    fun kyRoiVerify_bangKhoaThat_thiKhop() {
        val payload = "IDEA-03 payload thử".toByteArray()
        val signature = requireNotNull(AuthenticityKeyStore.sign(payload))
        val publicKey = requireNotNull(AuthenticityKeyStore.publicKeyEncoded())

        assertThat(AuthenticityKeyStore.verify(payload, signature, publicKey)).isTrue()
        // Đổi 1 byte payload là chữ ký phải hỏng.
        assertThat(AuthenticityKeyStore.verify("IDEA-03 payload thứ".toByteArray(), signature, publicKey)).isFalse()
    }
}
