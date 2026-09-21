package com.mckimquyen.watermark.data.repo

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Regression cho lỗ zip-slip tìm được ở code review (2026-09-16):
 * [SignatureRepository.importSignatureBytes] nhận `fileName` trực tiếp từ tên entry trong file
 * zip backup do user chọn qua SAF (không tin cậy) — nếu không sanitize, entry tên
 * `../../../../shared_prefs/evil.xml` có thể ghi đè file ngoài thư mục signature.
 *
 * Gộp mọi case vào 1 `@Test` duy nhất (thay vì tách nhỏ): `FileProvider.getUriForFile()` (gọi bên
 * trong mỗi `importSignatureBytes()`, xem [SignatureRepository.fileToContentUri]) cache
 * `PathStrategy` theo authority ở static field riêng của thư viện AndroidX — cache này SỐNG SÓT
 * qua ranh giới Application/Context của từng `@Test` method dưới Robolectric (mỗi method có
 * `context.filesDir` khác nhau, nhưng cache root vẫn trỏ về `filesDir` của method ĐẦU TIÊN chạy
 * trong JVM fork). Tách nhiều `@Test` riêng sẽ khiến mọi test SAU test đầu tiên fail sai (không
 * phải bug thật, cùng lớp với deadlock DataStore đã fix — xem `testutil/TestDataStores.kt`).
 */
@RunWith(RobolectricTestRunner::class)
class SignatureRepositoryImportRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repo = SignatureRepository(context)
    private val signatureDir = File(context.filesDir, "signatures")

    /** Bytes WEBP thật (không phải giả) — dùng cho mọi case cần "ảnh hợp lệ" kể từ ENH-31. */
    private fun realWebpBytes(pixel: Int = 1): ByteArray {
        val bitmap = Bitmap.createBitmap(pixel, pixel, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    @Test
    fun importSignatureBytes_sanitizesFileNameAndHandlesCollisions() = runBlocking {
        // 1. Tên file bình thường — ghi bytes nguyên vẹn.
        val normalBytes = realWebpBytes()
        val normal = repo.importSignatureBytes("signature_1.webp", normalBytes)
        assertThat(normal).isNotNull()
        assertThat(normal!!.file.readBytes()).isEqualTo(normalBytes)

        // 2. Path traversal ("../../...") — File(fileName).name chỉ lấy phần tên cuối, không ghi
        // ra ngoài signatureDir.
        val outsideFile = File(context.filesDir, "definitely_not_a_signature.xml")
        val traversal = repo.importSignatureBytes("../../definitely_not_a_signature.xml", realWebpBytes())
        assertThat(outsideFile.exists()).isFalse()
        assertThat(traversal).isNotNull()
        assertThat(traversal!!.file.parentFile).isEqualTo(signatureDir)
        assertThat(traversal.file.name).isEqualTo("definitely_not_a_signature.xml")

        // 3. Đường dẫn tuyệt đối — cũng chỉ lấy phần tên cuối.
        val absolute = repo.importSignatureBytes("/etc/evil.webp", realWebpBytes())
        assertThat(absolute).isNotNull()
        assertThat(absolute!!.file.parentFile).isEqualTo(signatureDir)
        assertThat(absolute.file.name).isEqualTo("evil.webp")

        // 4. Trùng tên file — thêm hậu tố số, không ghi đè file cũ.
        val dup1Bytes = realWebpBytes(1)
        val dup2Bytes = realWebpBytes(2)
        val dup1 = repo.importSignatureBytes("dup.webp", dup1Bytes)
        val dup2 = repo.importSignatureBytes("dup.webp", dup2Bytes)
        assertThat(dup1!!.file.readBytes()).isEqualTo(dup1Bytes)
        assertThat(dup2!!.file.readBytes()).isEqualTo(dup2Bytes)
        assertThat(dup2.file.name).isNotEqualTo(dup1.file.name)
    }

    /**
     * ENH-31: ảnh hợp lệ (bytes WEBP thật) phải qua được validate bounds-only mới ghi ra đĩa —
     * case "bytes rác bị từ chối" verify bằng instrumentation test thật trên device
     * ([com.mckimquyen.watermark.data.repo.SignatureRepositoryImportIntegrationTest]), không phải
     * ở đây: Robolectric decode native mode không mô phỏng đúng hành vi decode-failure thật của
     * Skia cho bytes hoàn toàn không phải ảnh (cùng lớp lý do `BitmapUtilsDecodeFailureIntegrationTest`
     * đã là androidTest thay vì Robolectric).
     */
    @Test
    fun importSignatureBytes_validImageBytes_isAcceptedByBoundsCheck() {
        assertThat(SignatureRepository.isValidImageBytes(realWebpBytes())).isTrue()
    }
}
