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
import java.io.FileNotFoundException

/**
 * BUG-54: provider ném SecurityException (quyền URI tạm của ACTION_SEND không chuyển sang Activity
 * mới) hoặc FileNotFoundException (ảnh bị xoá giữa chừng) khi `openInputStream()` — phải trả
 * `Result.failure`, KHÔNG được ném xuyên ra caller (CropActivity chạy trong lifecycleScope không
 * try/catch → crash app).
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsOpenStreamThrowsRoboTest {

    class SecurityThrowingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
            throw SecurityException("Permission Denial")
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

    class MissingFileProvider : ContentProvider() {
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? =
            throw FileNotFoundException("gone")
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
    fun decodeBitmapFromUri_securityException_returnsFailure_notThrow() = runBlocking {
        Robolectric.setupContentProvider(SecurityThrowingProvider::class.java, "wm.open.security")
        val uri = Uri.parse("content://wm.open.security/test.jpg")

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 0)

        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun decodeBitmapFromUri_fileNotFound_returnsFailure_notThrow() = runBlocking {
        Robolectric.setupContentProvider(MissingFileProvider::class.java, "wm.open.missing")
        val uri = Uri.parse("content://wm.open.missing/test.jpg")

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 1080)

        assertThat(result.isFailure()).isTrue()
    }
}
