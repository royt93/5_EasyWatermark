package com.mckimquyen.watermark.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * FEAT-22: Unit test cho CameraCaptureHelper (tạo file tạm, URI, dọn file rác, kiểm tra camera).
 */
@RunWith(RobolectricTestRunner::class)
class CameraCaptureHelperTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var createdFile: File? = null

    @Before
    fun setUp() {
        resetFileProviderCache()
        File(context.cacheDir, "camera").deleteRecursively()
    }

    @After
    fun tearDown() {
        createdFile?.delete()
        File(context.cacheDir, "camera").deleteRecursively()
        resetFileProviderCache()
    }

    private fun resetFileProviderCache() {
        try {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? java.util.Map<*, *>)?.clear()
        } catch (_: Exception) {}
    }

    @Test
    fun createPhotoFile_createsFileInCameraCacheDirWithCorrectPrefixAndSuffix() {
        val file = CameraCaptureHelper.createPhotoFile(context)
        createdFile = file

        assertThat(file.parentFile?.name).isEqualTo("camera")
        assertThat(file.name).startsWith("camera_photo_")
        assertThat(file.name).endsWith(".jpg")
    }

    @Test
    fun getPhotoUri_returnsValidFileProviderUri() {
        val file = CameraCaptureHelper.createPhotoFile(context).apply {
            createNewFile()
        }
        createdFile = file

        val uri = CameraCaptureHelper.getPhotoUri(context, file)
        assertThat(uri).isNotNull()
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${context.packageName}.fileprovider")
    }

    @Test
    fun cleanupPhotoFile_withZeroByteFile_deletesFileSuccessfully() {
        val file = CameraCaptureHelper.createPhotoFile(context).apply {
            createNewFile()
        }
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isEqualTo(0L)

        val deleted = CameraCaptureHelper.cleanupPhotoFile(file)
        assertThat(deleted).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun cleanupPhotoFile_withNonEmptyFile_preservesFile() {
        val file = CameraCaptureHelper.createPhotoFile(context).apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        createdFile = file
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)

        val deleted = CameraCaptureHelper.cleanupPhotoFile(file)
        assertThat(deleted).isFalse()
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun cleanupPhotoFile_withNullOrNonExistentFile_returnsFalseSafely() {
        assertThat(CameraCaptureHelper.cleanupPhotoFile(null)).isFalse()

        val nonExistent = File(context.cacheDir, "camera/non_existent.jpg")
        assertThat(CameraCaptureHelper.cleanupPhotoFile(nonExistent)).isFalse()
    }

    @Test
    fun isCameraAvailable_doesNotThrowException() {
        val available = CameraCaptureHelper.isCameraAvailable(context)
        // Trong môi trường Robolectric mặc định, queryIntentActivities trả về danh sách dựa theo manifest
        assertThat(available).isNotNull()
    }

    /**
     * ENH-44: ảnh chụp camera cũ (quá hạn) không được tích luỹ vô hạn trong `cacheDir/camera/` —
     * mỗi lần tạo file mới phải dọn file `camera_photo_*` quá 24h, giữ file mới.
     */
    @Test
    fun createPhotoFile_prunesStaleCameraPhotos_keepsRecentOnes() {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val stale = File(cameraDir, "camera_photo_1.jpg").apply {
            writeText("old")
            setLastModified(now - 48L * 60 * 60 * 1000)
        }
        val recent = File(cameraDir, "camera_photo_2.jpg").apply {
            writeText("new")
            setLastModified(now - 60_000L)
        }

        createdFile = CameraCaptureHelper.createPhotoFile(context)

        assertThat(stale.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    /** ENH-44: file lạ không thuộc prefix camera_photo_ trong cùng thư mục không bị đụng tới. */
    @Test
    fun createPhotoFile_doesNotTouchUnrelatedFiles() {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val other = File(cameraDir, "other.dat").apply {
            writeText("keep")
            setLastModified(System.currentTimeMillis() - 72L * 60 * 60 * 1000)
        }

        createdFile = CameraCaptureHelper.createPhotoFile(context)

        assertThat(other.exists()).isTrue()
    }
}
