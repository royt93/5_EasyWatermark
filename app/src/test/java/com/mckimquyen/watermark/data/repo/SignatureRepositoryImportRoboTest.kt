package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Test
    fun importSignatureBytes_sanitizesFileNameAndHandlesCollisions() = runBlocking {
        // 1. Tên file bình thường — ghi bytes nguyên vẹn.
        val normal = repo.importSignatureBytes("signature_1.webp", byteArrayOf(1, 2, 3, 4))
        assertThat(normal).isNotNull()
        assertThat(normal!!.file.readBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))

        // 2. Path traversal ("../../...") — File(fileName).name chỉ lấy phần tên cuối, không ghi
        // ra ngoài signatureDir.
        val outsideFile = File(context.filesDir, "definitely_not_a_signature.xml")
        val traversal = repo.importSignatureBytes("../../definitely_not_a_signature.xml", byteArrayOf(9))
        assertThat(outsideFile.exists()).isFalse()
        assertThat(traversal).isNotNull()
        assertThat(traversal!!.file.parentFile).isEqualTo(signatureDir)
        assertThat(traversal.file.name).isEqualTo("definitely_not_a_signature.xml")

        // 3. Đường dẫn tuyệt đối — cũng chỉ lấy phần tên cuối.
        val absolute = repo.importSignatureBytes("/etc/evil.webp", byteArrayOf(8))
        assertThat(absolute).isNotNull()
        assertThat(absolute!!.file.parentFile).isEqualTo(signatureDir)
        assertThat(absolute.file.name).isEqualTo("evil.webp")

        // 4. Trùng tên file — thêm hậu tố số, không ghi đè file cũ.
        val dup1 = repo.importSignatureBytes("dup.webp", byteArrayOf(1))
        val dup2 = repo.importSignatureBytes("dup.webp", byteArrayOf(2))
        assertThat(dup1!!.file.readBytes()).isEqualTo(byteArrayOf(1))
        assertThat(dup2!!.file.readBytes()).isEqualTo(byteArrayOf(2))
        assertThat(dup2.file.name).isNotEqualTo(dup1.file.name)
    }
}
