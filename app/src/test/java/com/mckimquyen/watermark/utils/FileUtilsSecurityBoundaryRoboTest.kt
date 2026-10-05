package com.mckimquyen.watermark.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * BUG-74: `ContentResolver.getType()` ném `SecurityException` khi provider từ chối quyền (clipboard
 * chứa `content://` không cấp quyền, hoặc quyền SAF bị thu hồi). `FileUtils.getFileTypeFromUri` khai
 * `@Throws(SecurityException)` và không guard nên ngoại lệ thoát lên main coroutine/click handler →
 * crash. Phải trả giá trị an toàn (null/false) tại trust boundary.
 */
@RunWith(RobolectricTestRunner::class)
class FileUtilsSecurityBoundaryRoboTest {

    class DenyingProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = throw SecurityException("Permission Denial: reading provider")
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? = null
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun deniedUri(authority: String): Uri {
        Robolectric.setupContentProvider(DenyingProvider::class.java, authority)
        return Uri.parse("content://$authority/picture")
    }

    @Test
    fun getFileTypeFromUri_providerDeniesPermission_returnsNullInsteadOfThrowing() {
        val uri = deniedUri("wm.deny.type")

        val type = FileUtils.getFileTypeFromUri(context.contentResolver, uri)

        assertThat(type).isNull()
    }

    @Test
    fun isImage_providerDeniesPermission_returnsFalseInsteadOfThrowing() {
        val uri = deniedUri("wm.deny.isimage")

        assertThat(FileUtils.isImage(context.contentResolver, uri)).isFalse()
    }
}
