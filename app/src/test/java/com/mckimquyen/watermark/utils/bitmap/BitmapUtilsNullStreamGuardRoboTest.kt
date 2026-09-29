package com.mckimquyen.watermark.utils.bitmap

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * BUG-AUDIT-2026-09-29 (review pass): sau khi gộp nhánh reqLongEdge<=0 vào chung đường code với
 * reqLongEdge>0, bước decode bounds (`inJustDecodeBounds=true`) dùng chung 1 `resolver
 * .openInputStream(uri).use { ... }` cho MỌI reqLongEdge — nếu provider trả `openAssetFile=null`
 * (quyền bị thu hồi giữa chừng / provider lỗi), phải trả `Result.failure` sạch sẽ như bước decode
 * pixel thật phía dưới đã làm, KHÔNG được gọi `BitmapFactory.decodeStream(null, ...)`.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsNullStreamGuardRoboTest {

    /** Provider giả lập "mất quyền/lỗi provider" — luôn trả null cho asset file. */
    class NullStreamProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = null
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun decodeBitmapFromUri_nullInputStreamAtBoundsStep_returnsFailure_notCrash() = runBlocking {
        Robolectric.setupContentProvider(NullStreamProvider::class.java, "wm.null.stream.original")
        val uri = Uri.parse("content://wm.null.stream.original/test.jpg")

        // reqLongEdge=0 ("Original") — nhánh mới gộp chung, đây chính là bước dễ vỡ nếu thiếu guard.
        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 0)

        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun decodeBitmapFromUri_nullInputStreamAtBoundsStep_resizeRequested_returnsFailure_notCrash() = runBlocking {
        Robolectric.setupContentProvider(NullStreamProvider::class.java, "wm.null.stream.resize")
        val uri = Uri.parse("content://wm.null.stream.resize/test.jpg")

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 1080)

        assertThat(result.isFailure()).isTrue()
    }
}
