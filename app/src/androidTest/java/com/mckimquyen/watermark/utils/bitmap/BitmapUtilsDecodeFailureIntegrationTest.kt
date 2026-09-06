package com.mckimquyen.watermark.utils.bitmap

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test (instrumented, ContentResolver thật trên thiết bị): kiểm chứng
 * luồng thật `decodeSampledBitmapFromResource -> BitmapCache.addToCache` không crash
 * khi decode ảnh lỗi (URI không tồn tại) — BUG-02 (trước fix: NPE từ
 * `LruCache.put(info, null)` vì `cacheValue` là null trong trường hợp này).
 */
@RunWith(AndroidJUnit4::class)
class BitmapUtilsDecodeFailureIntegrationTest {

    @Test
    fun decodeSampledBitmapFromResource_nonExistentUri_returnsFailureInsteadOfCrashing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nonExistentUri = Uri.parse("content://com.mckimquyen.watermark.doesnotexist/missing.jpg")

        // Trước fix BUG-02: dòng dưới ném NullPointerException (LruCache.put value null).
        val result = decodeSampledBitmapFromResource(
            context = context,
            resolver = context.contentResolver,
            uri = nonExistentUri,
            reqWidth = 100,
            reqHeight = 100
        )

        assertThat(result.data).isNull()

        // Gọi lại lần 2 với cùng URI để xác nhận cache không bị "kẹt" ở trạng thái lỗi
        // và vẫn không crash khi tra cứu/ghi lại.
        val secondResult = decodeSampledBitmapFromResource(
            context = context,
            resolver = context.contentResolver,
            uri = nonExistentUri,
            reqWidth = 100,
            reqHeight = 100
        )
        assertThat(secondResult.data).isNull()
    }
}
