package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-19: `openFileDescriptor()` có thể trả `null`, và `Bitmap.compress()` trả `Boolean` báo
 * thành công/thất bại — trước đây cả 2 bị bỏ qua ở nhánh ghi MediaStore, dẫn tới báo "thành
 * công" giả và để lại row `IS_PENDING` rác khi ghi thất bại. Test thuần (JVM) cho phần quyết
 * định logic, theo đúng pattern [MediaStoreInsertResolverTest] (BUG-04).
 */
class MediaStoreWriteResolverTest {

    private val errorCode = "type_error_save_mediastore_write"

    @Test
    fun resolve_fdNull_returnsFailure() {
        val result = MediaStoreWriteResolver.resolve(fdAvailable = false, compressSucceeded = true, errorCode = errorCode)

        assertThat(result.isFailure()).isTrue()
        assertThat(result.code).isEqualTo(errorCode)
    }

    @Test
    fun resolve_compressFalse_returnsFailure() {
        val result = MediaStoreWriteResolver.resolve(fdAvailable = true, compressSucceeded = false, errorCode = errorCode)

        assertThat(result.isFailure()).isTrue()
        assertThat(result.code).isEqualTo(errorCode)
    }

    @Test
    fun resolve_fdNullAndCompressFalse_returnsFailure_fdCheckedFirst() {
        val result = MediaStoreWriteResolver.resolve(fdAvailable = false, compressSucceeded = false, errorCode = errorCode)

        assertThat(result.isFailure()).isTrue()
        assertThat(result.message).contains("openFileDescriptor")
    }

    @Test
    fun resolve_fdAvailableAndCompressSucceeded_returnsSuccess() {
        val result = MediaStoreWriteResolver.resolve(fdAvailable = true, compressSucceeded = true, errorCode = errorCode)

        assertThat(result.isFailure()).isFalse()
    }
}
