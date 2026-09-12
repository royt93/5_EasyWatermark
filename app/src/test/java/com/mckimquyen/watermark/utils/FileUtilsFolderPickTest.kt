package com.mckimquyen.watermark.utils

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-08: [FileUtils.filterImageUris] là logic lọc thật đứng sau [FileUtils.listImagesInTree] —
 * tách riêng để test được mà không cần dựng cả 1 `DocumentsProvider` SAF thật. Mock [DocumentFile]
 * bằng mockk thay vì chạy qua `DocumentFile.fromTreeUri()` (cần ContentProvider thật, không có
 * trong JVM test).
 */
@RunWith(RobolectricTestRunner::class)
class FileUtilsFolderPickTest {

    private fun fakeChild(uriStr: String, isFile: Boolean, mimeType: String?): DocumentFile {
        val doc = mockk<DocumentFile>()
        every { doc.isFile } returns isFile
        every { doc.type } returns mimeType
        every { doc.uri } returns Uri.parse(uriStr)
        return doc
    }

    @Test
    fun filterImageUris_emptyList_returnsEmpty() {
        assertThat(FileUtils.filterImageUris(emptyList())).isEmpty()
    }

    @Test
    fun filterImageUris_onlyImageFiles_keepsAll() {
        val children = listOf(
            fakeChild("content://tree/1", isFile = true, mimeType = "image/jpeg"),
            fakeChild("content://tree/2", isFile = true, mimeType = "image/png")
        )

        val result = FileUtils.filterImageUris(children)

        assertThat(result).containsExactly(Uri.parse("content://tree/1"), Uri.parse("content://tree/2"))
    }

    @Test
    fun filterImageUris_nonImageFile_isExcluded() {
        val children = listOf(
            fakeChild("content://tree/1", isFile = true, mimeType = "image/jpeg"),
            fakeChild("content://tree/notes", isFile = true, mimeType = "text/plain"),
            fakeChild("content://tree/video", isFile = true, mimeType = "video/mp4")
        )

        val result = FileUtils.filterImageUris(children)

        assertThat(result).containsExactly(Uri.parse("content://tree/1"))
    }

    /** AC: "không đệ quy subfolder" — thư mục con (isFile=false) phải bị loại, dù mimeType null như thư mục thật. */
    @Test
    fun filterImageUris_subfolder_isExcluded_notRecursed() {
        val children = listOf(
            fakeChild("content://tree/1", isFile = true, mimeType = "image/jpeg"),
            fakeChild("content://tree/subfolder", isFile = false, mimeType = null)
        )

        val result = FileUtils.filterImageUris(children)

        assertThat(result).containsExactly(Uri.parse("content://tree/1"))
    }

    @Test
    fun filterImageUris_nullMimeTypeFile_isExcluded() {
        val children = listOf(
            fakeChild("content://tree/unknown", isFile = true, mimeType = null)
        )

        val result = FileUtils.filterImageUris(children)

        assertThat(result).isEmpty()
    }

    @Test
    fun listImagesInTree_invalidTreeUri_returnsEmptyList_doesNotThrow() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        val result = FileUtils.listImagesInTree(context, Uri.parse("content://does.not.exist/tree/x"))

        assertThat(result).isEmpty()
    }
}
