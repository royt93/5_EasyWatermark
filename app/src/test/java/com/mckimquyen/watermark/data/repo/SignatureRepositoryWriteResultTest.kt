package com.mckimquyen.watermark.data.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * BUG-34: `Bitmap.compress()` trả `Boolean` báo thành công/thất bại thật — `resolveWriteResult()`
 * phải xoá file rỗng/hỏng và báo thất bại khi `compress()` trả `false`, thay vì để lại file rác +
 * báo thành công giả (signature không render được khi dùng làm watermark icon).
 */
class SignatureRepositoryWriteResultTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun resolveWriteResult_compressFailed_deletesFileAndReturnsFalse() {
        val file = tmp.newFile("signature_bad.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val result = SignatureRepository.resolveWriteResult(file, compressSucceeded = false)

        assertThat(result).isFalse()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun resolveWriteResult_compressSucceeded_keepsFileAndReturnsTrue() {
        val file = tmp.newFile("signature_ok.webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val result = SignatureRepository.resolveWriteResult(file, compressSucceeded = true)

        assertThat(result).isTrue()
        assertThat(file.exists()).isTrue()
    }
}
