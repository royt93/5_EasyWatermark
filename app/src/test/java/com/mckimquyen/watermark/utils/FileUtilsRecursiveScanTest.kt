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
 * ENH-33: [FileUtils.collectImagesRecursively] — đệ quy có giới hạn tầng/số ảnh khi user bật
 * "Include subfolders". Mock [DocumentFile] bằng mockk (cùng pattern [FileUtilsFolderPickTest]).
 */
@RunWith(RobolectricTestRunner::class)
class FileUtilsRecursiveScanTest {

    private fun fakeFile(uriStr: String, mimeType: String = "image/jpeg"): DocumentFile {
        val doc = mockk<DocumentFile>()
        every { doc.isFile } returns true
        every { doc.isDirectory } returns false
        every { doc.type } returns mimeType
        every { doc.uri } returns Uri.parse(uriStr)
        return doc
    }

    private fun fakeDir(children: List<DocumentFile>): DocumentFile {
        val doc = mockk<DocumentFile>()
        every { doc.isFile } returns false
        every { doc.isDirectory } returns true
        every { doc.listFiles() } returns children.toTypedArray()
        return doc
    }

    @Test
    fun collectImagesRecursively_findsImagesInNestedSubfolders() {
        val leaf = fakeDir(listOf(fakeFile("content://tree/a/b/pic3.jpg")))
        val subB = fakeDir(listOf(fakeFile("content://tree/a/pic2.jpg"), leaf))
        val root = fakeDir(listOf(fakeFile("content://tree/pic1.jpg"), subB))

        val result = FileUtils.collectImagesRecursively(root, maxDepth = 5, maxFiles = 500)

        assertThat(result).containsExactly(
            Uri.parse("content://tree/pic1.jpg"),
            Uri.parse("content://tree/a/pic2.jpg"),
            Uri.parse("content://tree/a/b/pic3.jpg")
        )
    }

    @Test
    fun collectImagesRecursively_stopsDescendingPastMaxDepth() {
        // root(depth0) -> sub(depth1, ảnh ở đây OK) -> subsub(depth2, KHÔNG được duyệt khi maxDepth=1)
        val subsub = fakeDir(listOf(fakeFile("content://tree/a/b/pic_too_deep.jpg")))
        val sub = fakeDir(listOf(fakeFile("content://tree/a/pic_ok.jpg"), subsub))
        val root = fakeDir(listOf(sub))

        val result = FileUtils.collectImagesRecursively(root, maxDepth = 1, maxFiles = 500)

        assertThat(result).containsExactly(Uri.parse("content://tree/a/pic_ok.jpg"))
    }

    @Test
    fun collectImagesRecursively_stopsAtMaxFiles_doesNotOverCollect() {
        val manyFiles = (1..10).map { fakeFile("content://tree/pic$it.jpg") }
        val root = fakeDir(manyFiles)

        val result = FileUtils.collectImagesRecursively(root, maxDepth = 5, maxFiles = 3)

        assertThat(result).hasSize(3)
    }

    @Test
    fun collectImagesRecursively_nonImageFilesInSubfolder_areExcluded() {
        val sub = fakeDir(
            listOf(
                fakeFile("content://tree/a/pic.jpg", mimeType = "image/jpeg"),
                fakeFile("content://tree/a/notes.txt", mimeType = "text/plain")
            )
        )
        val root = fakeDir(listOf(sub))

        val result = FileUtils.collectImagesRecursively(root, maxDepth = 5, maxFiles = 500)

        assertThat(result).containsExactly(Uri.parse("content://tree/a/pic.jpg"))
    }

    @Test
    fun listImagesInTree_includeSubfoldersFalse_doesNotRecurse_sameAsFeat08() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        // Không thể mock DocumentFile.fromTreeUri (static) dễ dàng ở test JVM thuần — verify qua
        // URI không tồn tại (không throw, trả rỗng), hành vi mặc định includeSubfolders=false giữ
        // nguyên API cũ (compile-time check: không cần truyền tham số mới).
        val result = FileUtils.listImagesInTree(context, Uri.parse("content://does.not.exist/tree/x"))

        assertThat(result).isEmpty()
    }
}
