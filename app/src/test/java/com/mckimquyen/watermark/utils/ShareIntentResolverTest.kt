package com.mckimquyen.watermark.utils

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-09: `ACTION_SEND` phải đọc URI từ `EXTRA_STREAM` (đường share sheet thật dùng),
 * không chỉ `intent.data`; và bỏ qua mọi action khác.
 */
@RunWith(RobolectricTestRunner::class)
class ShareIntentResolverTest {

    @Test
    fun actionSend_withExtraStream_returnsUri() {
        val uri = Uri.parse("content://media/external/images/media/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        assertThat(ShareIntentResolver.resolveSharedImageUri(intent)).isEqualTo(uri)
    }

    @Test
    fun actionSend_withDataOnly_returnsUri() {
        val uri = Uri.parse("content://media/external/images/media/2")
        val intent = Intent(Intent.ACTION_SEND).apply { data = uri }

        assertThat(ShareIntentResolver.resolveSharedImageUri(intent)).isEqualTo(uri)
    }

    @Test
    fun actionSend_dataTakesPrecedenceOverExtraStream() {
        val dataUri = Uri.parse("content://media/data")
        val streamUri = Uri.parse("content://media/stream")
        val intent = Intent(Intent.ACTION_SEND).apply {
            data = dataUri
            putExtra(Intent.EXTRA_STREAM, streamUri)
        }

        assertThat(ShareIntentResolver.resolveSharedImageUri(intent)).isEqualTo(dataUri)
    }

    @Test
    fun actionSend_noUriAtAll_returnsNull() {
        val intent = Intent(Intent.ACTION_SEND)

        assertThat(ShareIntentResolver.resolveSharedImageUri(intent)).isNull()
    }

    @Test
    fun nonSendAction_returnsNullEvenWithExtraStream() {
        val uri = Uri.parse("content://media/3")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        assertThat(ShareIntentResolver.resolveSharedImageUri(intent)).isNull()
    }

    @Test
    fun isSharedImageIntent_imageMimeAndUri_returnsTrue() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/image.jpg"))
        }

        assertThat(ShareIntentResolver.isSharedImageIntent(intent)).isTrue()
    }

    @Test
    fun isSharedImageIntent_nonImageMimeWithStream_returnsFalse() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/image.jpg"))
        }

        assertThat(ShareIntentResolver.isSharedImageIntent(intent)).isFalse()
    }

    @Test
    fun isSharedImageIntent_nonSendActionWithImageMime_returnsFalse() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/jpeg"
            data = Uri.parse("content://media/image.jpg")
        }

        assertThat(ShareIntentResolver.isSharedImageIntent(intent)).isFalse()
    }

    @Test
    fun isSharedImageIntent_sendWithoutUri_returnsFalse() {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg" }

        assertThat(ShareIntentResolver.isSharedImageIntent(intent)).isFalse()
    }

    @Test
    fun nullIntent_returnsNull() {
        assertThat(ShareIntentResolver.resolveSharedImageUri(null)).isNull()
    }
}
