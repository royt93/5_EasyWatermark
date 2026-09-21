package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Integration test (instrumented, Skia decode thật trên thiết bị): ENH-31 — zip backup giả mạo
 * có thể chứa bytes bất kỳ đặt tên `.webp` cho signature. `SignatureRepository.isValidImageBytes()`
 * chỉ decode bounds (không decode pixel), verify bằng thiết bị thật thay vì Robolectric vì shadow
 * decode của Robolectric không mô phỏng đúng hành vi decode-failure thật của Skia cho bytes hoàn
 * toàn không phải ảnh (cùng lớp lý do [com.mckimquyen.watermark.utils.bitmap.BitmapUtilsDecodeFailureIntegrationTest]
 * là androidTest thay vì Robolectric).
 */
@RunWith(AndroidJUnit4::class)
class SignatureRepositoryImportIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = SignatureRepository(context)
    private val signatureDir = File(context.filesDir, "signatures")

    private fun realWebpBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    @Test
    fun importSignatureBytes_rejectsFakeImageBytes_butStillImportsValidEntriesInSameRestore() = runBlocking {
        val fakeBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(SignatureRepository.isValidImageBytes(fakeBytes)).isFalse()

        val fakeResult = repo.importSignatureBytes("fake_signature.webp", fakeBytes)
        assertThat(fakeResult).isNull()
        assertThat(File(signatureDir, "fake_signature.webp").exists()).isFalse()

        // Entry hợp lệ khác trong cùng lượt restore (đến ngay sau entry giả) vẫn phải import đúng.
        val validBytes = realWebpBytes()
        assertThat(SignatureRepository.isValidImageBytes(validBytes)).isTrue()
        val validResult = repo.importSignatureBytes("real_signature.webp", validBytes)
        assertThat(validResult).isNotNull()
        assertThat(validResult!!.file.readBytes()).isEqualTo(validBytes)
    }
}
