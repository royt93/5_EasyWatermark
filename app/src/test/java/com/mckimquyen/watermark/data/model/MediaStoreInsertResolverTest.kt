package com.mckimquyen.watermark.data.model

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test (Robolectric, cần Uri thật) cho [MediaStoreInsertResolver] — trực tiếp phòng
 * regression BUG-04 (`contentResolver.insert()!!` force-unwrap crash khi MediaStore trả null).
 */
@RunWith(RobolectricTestRunner::class)
class MediaStoreInsertResolverTest {

    @Test
    fun resolve_nonNullUri_returnsSuccessWithSameUri() {
        val uri = Uri.parse("content://media/external/images/media/123")

        val result = MediaStoreInsertResolver.resolve(uri, errorCode = "unused")

        assertThat(result.isFailure()).isFalse()
        assertThat(result.data).isEqualTo(uri)
    }

    @Test
    fun resolve_nullUri_returnsFailureWithGivenErrorCode() {
        val result = MediaStoreInsertResolver.resolve(null, errorCode = "type_error_save_mediastore_insert")

        assertThat(result.isFailure()).isTrue()
        assertThat(result.data).isNull()
        assertThat(result.code).isEqualTo("type_error_save_mediastore_insert")
    }
}
