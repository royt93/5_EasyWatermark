package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.bitmap.BitmapRecycleGuard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * FEAT-15: [BatchExportEngine.writeIntoDocumentTree] — ghi ảnh vào thư mục SAF user tự chọn thay
 * vì cố định Pictures/WaterMarkCreator/. Instrumentation test (I/O file thật, decode ảnh thật) vì
 * đụng `ContentResolver.openOutputStream()`/`DocumentFile.createFile()` — dùng
 * `DocumentFile.fromFile()` trỏ 1 thư mục thật trong `cacheDir` thay vì cần user thao tác chọn
 * cây SAF thật qua `ACTION_OPEN_DOCUMENT_TREE` (không thể tự động hoá trong test). `fromFile()`
 * triển khai CÙNG interface `DocumentFile`, `ContentResolver.openOutputStream()` cũng hỗ trợ
 * `file://` uri thật (Android tự route qua `FileOutputStream`) — verify đúng logic conflict-policy
 * + ghi file thật, chỉ khác đường vào (không qua DocumentsProvider thật của 1 app khác).
 */
@RunWith(AndroidJUnit4::class)
class BatchExportEngineCustomDirectoryIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = BatchExportEngine(context, ExportNaming())

    private fun testBitmap(color: Int = android.graphics.Color.RED): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** DataStore cô lập (file tạm riêng) — [com.mckimquyen.watermark.testutil.TestDataStores] chỉ
     * ở source set `test`, không dùng chung được với `androidTest`; lặp lại pattern nhỏ ở đây. */
    private fun newIsolatedDataStore(): androidx.datastore.core.DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File.createTempFile("water_mark_androidtest_${UUID.randomUUID()}", ".preferences_pb", context.cacheDir).apply { deleteOnExit() } }
        )

    private fun settings(conflictPolicy: ConflictPolicy) = runBlocking {
        val waterMarkRepo = WaterMarkRepository(context, newIsolatedDataStore())
        BatchExportEngine.ExportSettings(
            config = waterMarkRepo.waterMark.first(),
            outputFormat = Bitmap.CompressFormat.PNG,
            compressLevel = 100,
            maxOutputLongEdge = 0,
            copyright = "",
            outputNamePattern = "{filename}",
            conflictPolicy = conflictPolicy
        )
    }

    private fun tempDir(name: String): File = File(context.cacheDir, "feat15_${name}_${System.currentTimeMillis()}").apply { mkdirs() }

    /** Bitmap + guard theo dõi CÙNG 1 instance — đúng cách [BatchExportEngine.generateImage] thật dùng. */
    private fun writeWithFreshBitmap(
        root: DocumentFile,
        rawOutputName: String,
        exportSettings: BatchExportEngine.ExportSettings
    ) = testBitmap().let { bitmap ->
        engine.writeIntoDocumentTree(root, context.contentResolver, bitmap, rawOutputName, exportSettings, BitmapRecycleGuard(bitmap))
    }

    @Test
    fun writeIntoDocumentTree_newFile_writesToChosenDirectory() {
        val dir = tempDir("new")
        val root = DocumentFile.fromFile(dir)

        val result = writeWithFreshBitmap(root, "photo.png", settings(ConflictPolicy.KEEP_BOTH))

        assertThat(result.isFailure()).isFalse()
        // Không giả định tên file thật trên đĩa (DocumentFile.createFile() có thể tự điều chỉnh
        // tên theo mimeType, khác hẳn tên yêu cầu) — verify qua chính Uri trả về (đúng cách SAF
        // hoạt động: caller không nên tự đoán tên file, chỉ dùng Uri).
        val writtenFile = File(result.data!!.path!!)
        assertThat(dir.listFiles()?.size).isEqualTo(1)
        assertThat(writtenFile.exists()).isTrue()
        assertThat(writtenFile.length()).isGreaterThan(0L)
        dir.deleteRecursively()
    }

    @Test
    fun writeIntoDocumentTree_keepBoth_existingFile_createsDistinctEntry_doesNotOverwrite() {
        val dir = tempDir("keepboth")
        File(dir, "photo.png").writeBytes(byteArrayOf(9, 9, 9)) // "ảnh cũ" giả lập đã tồn tại
        val root = DocumentFile.fromFile(dir)

        val result = writeWithFreshBitmap(root, "photo.png", settings(ConflictPolicy.KEEP_BOTH))

        assertThat(result.isFailure()).isFalse()
        // File cũ không bị đổi nội dung (KEEP_BOTH không đụng vào entry đã có).
        assertThat(File(dir, "photo.png").readBytes()).isEqualTo(byteArrayOf(9, 9, 9))
        // Có tổng cộng >= 2 file trong thư mục (file mới không bị chặn/gộp vào file cũ).
        assertThat(dir.listFiles()!!.size).isAtLeast(2)
        dir.deleteRecursively()
    }

    @Test
    fun writeIntoDocumentTree_skip_existingFile_returnsExistingWithoutWriting() {
        val dir = tempDir("skip")
        val existingBytes = byteArrayOf(1, 2, 3, 4)
        File(dir, "photo.png").writeBytes(existingBytes)
        val root = DocumentFile.fromFile(dir)

        val result = writeWithFreshBitmap(root, "photo.png", settings(ConflictPolicy.SKIP))

        assertThat(result.isFailure()).isFalse()
        assertThat(File(dir, "photo.png").readBytes()).isEqualTo(existingBytes)
        assertThat(dir.listFiles()!!.size).isEqualTo(1) // không tạo thêm entry nào
        dir.deleteRecursively()
    }

    @Test
    fun writeIntoDocumentTree_overwrite_existingFile_replacesContent() {
        val dir = tempDir("overwrite")
        File(dir, "photo.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        val root = DocumentFile.fromFile(dir)

        val result = writeWithFreshBitmap(root, "photo.png", settings(ConflictPolicy.OVERWRITE))

        assertThat(result.isFailure()).isFalse()
        val newBytes = File(dir, "photo.png").readBytes()
        assertThat(newBytes).isNotEqualTo(byteArrayOf(1, 2, 3, 4))
        assertThat(newBytes.size).isGreaterThan(0)
        assertThat(dir.listFiles()!!.size).isEqualTo(1) // ghi đè, không tạo thêm entry
        dir.deleteRecursively()
    }

    @Test
    fun writeIntoDocumentTree_renameVersion_existingFile_createsVersionedName() {
        val dir = tempDir("rename")
        File(dir, "photo.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        val root = DocumentFile.fromFile(dir)

        val result = writeWithFreshBitmap(root, "photo.png", settings(ConflictPolicy.RENAME_VERSION))

        assertThat(result.isFailure()).isFalse()
        assertThat(File(dir, "photo.png").readBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4)) // file gốc không đổi
        // File mới KHÔNG được ghi đè lên "photo.png" (đã assert ở trên) — phải là 1 entry riêng
        // biệt (tên thật do DocumentFile.createFile() tự quyết, không giả định "photo_v2.png").
        assertThat(dir.listFiles()?.size).isEqualTo(2)
        val newFile = File(result.data!!.path!!)
        assertThat(newFile.exists()).isTrue()
        assertThat(newFile.name).isNotEqualTo("photo.png")
        dir.deleteRecursively()
    }

    @Test
    fun writeIntoDocumentTree_appliesCopyrightExif_whenFormatSupportsIt() {
        val dir = tempDir("exif")
        val root = DocumentFile.fromFile(dir)
        val waterMarkRepo = WaterMarkRepository(context, newIsolatedDataStore())
        val settingsWithCopyright = runBlocking {
            BatchExportEngine.ExportSettings(
                config = waterMarkRepo.waterMark.first(),
                outputFormat = Bitmap.CompressFormat.JPEG,
                compressLevel = 90,
                maxOutputLongEdge = 0,
                copyright = "Test Studio 2026",
                outputNamePattern = "{filename}",
                conflictPolicy = ConflictPolicy.KEEP_BOTH
            )
        }

        val result = writeWithFreshBitmap(root, "photo.jpg", settingsWithCopyright)

        assertThat(result.isFailure()).isFalse()
        val exif = androidx.exifinterface.media.ExifInterface(File(result.data!!.path!!).absolutePath)
        assertThat(exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT)).isEqualTo("Test Studio 2026")
        dir.deleteRecursively()
    }
}
