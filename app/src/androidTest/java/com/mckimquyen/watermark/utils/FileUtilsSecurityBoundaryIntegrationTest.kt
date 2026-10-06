package com.mckimquyen.watermark.utils

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-74 smoke máy thật: khi ContentProvider từ chối quyền hoặc tree URI SAF không có persistable grant,
 * [FileUtils.getFileTypeFromUri] và [FileUtils.listImagesInTree] không được ném SecurityException thoát ra ngoài.
 */
@RunWith(AndroidJUnit4::class)
class FileUtilsSecurityBoundaryIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun listImagesInTree_unauthorizedTreeUri_returnsEmpty_andDoesNotThrow() {
        val fakeDeniedTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANonExistentOrRevoked")
        val result = FileUtils.listImagesInTree(context, fakeDeniedTreeUri, includeSubfolders = false)
        assertThat(result).isEmpty()
    }

    @Test
    fun listImagesInTree_withSubfolders_unauthorizedTreeUri_returnsEmpty_andDoesNotThrow() {
        val fakeDeniedTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANonExistentOrRevoked")
        val result = FileUtils.listImagesInTree(context, fakeDeniedTreeUri, includeSubfolders = true)
        assertThat(result).isEmpty()
    }

    @Test
    fun getFileTypeFromUri_unauthorizedContentUri_doesNotThrow_andFallsBackToExtension() {
        val deniedWithJpg = Uri.parse("content://com.android.externalstorage.documents/document/denied%3Aphoto.jpg")
        val mime = FileUtils.getFileTypeFromUri(context.contentResolver, deniedWithJpg)
        // Fallback đọc từ đuôi file .jpg
        assertThat(mime).isEqualTo("image/jpeg")
    }

    @Test
    fun getFileTypeFromUri_nullUri_returnsNull() {
        assertThat(FileUtils.getFileTypeFromUri(context.contentResolver, null)).isNull()
    }
}
