package com.mckimquyen.watermark.utils

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class ExportZipHelperTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        testTempDir = File(context.cacheDir, "test_zip_temp_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        testTempDir.deleteRecursively()
    }

    @Test
    fun sanitizeAndDeduplicateEntryName_handlesVariousInputsCorrectly() {
        val usedNames = mutableSetOf<String>()

        // 1. Tên bình thường
        val name1 = ExportZipHelper.sanitizeAndDeduplicateEntryName("photo.jpg", 1, usedNames)
        assertThat(name1).isEqualTo("photo.jpg")

        // 2. Trùng tên lần 2 -> thêm suffix _2
        val name2 = ExportZipHelper.sanitizeAndDeduplicateEntryName("photo.jpg", 2, usedNames)
        assertThat(name2).isEqualTo("photo_2.jpg")

        // 3. Trùng tên lần 3 -> thêm suffix _3
        val name3 = ExportZipHelper.sanitizeAndDeduplicateEntryName("photo.jpg", 3, usedNames)
        assertThat(name3).isEqualTo("photo_3.jpg")

        // 4. Path traversal (Zip Slip prevention)
        val evil = ExportZipHelper.sanitizeAndDeduplicateEntryName("../../etc/secret.png", 4, usedNames)
        assertThat(evil).isEqualTo("secret.png")

        // 5. Ký tự cấm trên file system
        val special = ExportZipHelper.sanitizeAndDeduplicateEntryName("a:b?c*d<e>f.jpg", 5, usedNames)
        assertThat(special).isEqualTo("a_b_c_d_e_f.jpg")

        // 6. Tên null hoặc rỗng -> fallback theo index
        val empty = ExportZipHelper.sanitizeAndDeduplicateEntryName("", 6, usedNames)
        assertThat(empty).isEqualTo("watermark_image_6.jpg")

        val nullName = ExportZipHelper.sanitizeAndDeduplicateEntryName(null, 7, usedNames)
        assertThat(nullName).isEqualTo("watermark_image_7.jpg")

        // 7. Tệp không có extension trùng lặp
        val noExt1 = ExportZipHelper.sanitizeAndDeduplicateEntryName("README", 8, usedNames)
        val noExt2 = ExportZipHelper.sanitizeAndDeduplicateEntryName("README", 9, usedNames)
        assertThat(noExt1).isEqualTo("README")
        assertThat(noExt2).isEqualTo("README_2")
    }

    @Test
    fun createZipArchive_withEmptyUris_returnsNull() {
        val destZip = File(testTempDir, "empty.zip")
        val result = ExportZipHelper.createZipArchive(context.contentResolver, emptyList(), destZip)
        assertThat(result).isNull()
        assertThat(destZip.exists()).isFalse()
    }

    @Test
    fun createZipArchive_packagesMultipleFilesWithCorrectContentAndNames() {
        // Tạo 3 tệp ảnh mẫu
        val file1 = File(testTempDir, "img_export_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val file2 = File(testTempDir, "img_export_2.jpg").apply { writeBytes(byteArrayOf(5, 6, 7, 8, 9)) }
        val file3 = File(testTempDir, "img_export_3.png").apply { writeBytes(byteArrayOf(10, 20, 30)) }

        val uris = listOf(Uri.fromFile(file1), Uri.fromFile(file2), Uri.fromFile(file3))
        val destZip = File(testTempDir, "output.zip")

        val result = ExportZipHelper.createZipArchive(context.contentResolver, uris, destZip)
        assertThat(result).isNotNull()
        assertThat(destZip.exists()).isTrue()
        assertThat(destZip.length()).isGreaterThan(0L)

        // Kiểm tra nội dung bên trong file zip
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(destZip)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries[entry.name] = zipIn.readBytes()
                entry = zipIn.nextEntry
            }
        }

        assertThat(entries.keys).containsExactly("img_export_1.jpg", "img_export_2.jpg", "img_export_3.png")
        assertThat(entries["img_export_1.jpg"]).isEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(entries["img_export_2.jpg"]).isEqualTo(byteArrayOf(5, 6, 7, 8, 9))
        assertThat(entries["img_export_3.png"]).isEqualTo(byteArrayOf(10, 20, 30))
    }

    @Test
    fun createZipArchive_whenOneUriFails_stillPackagesRemainingSuccessfulUris() {
        val validFile = File(testTempDir, "valid.jpg").apply { writeBytes(byteArrayOf(100, 101)) }
        val invalidUri = Uri.parse("file:///non_existent_directory/non_existent_file.jpg")

        val uris = listOf(Uri.fromFile(validFile), invalidUri)
        val destZip = File(testTempDir, "partial.zip")

        val result = ExportZipHelper.createZipArchive(context.contentResolver, uris, destZip)
        assertThat(result).isNotNull()

        val entries = mutableListOf<String>()
        ZipInputStream(FileInputStream(destZip)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zipIn.nextEntry
            }
        }

        assertThat(entries).containsExactly("valid.jpg")
    }

    @Test
    fun createShareZipIntent_hasProperActionTypeAndFlags() {
        val sampleZipUri = Uri.parse("content://com.mckimquyen.watermark.fileprovider/export_zip/test.zip")
        val intent = ExportZipHelper.createShareZipIntent(context, sampleZipUri)

        assertThat(intent.action).isEqualTo(android.content.Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("application/zip")
        assertThat(intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)).isEqualTo(sampleZipUri)
        assertThat(intent.clipData).isNotNull()
        assertThat(intent.clipData?.getItemAt(0)?.uri).isEqualTo(sampleZipUri)
        assertThat(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }
}
