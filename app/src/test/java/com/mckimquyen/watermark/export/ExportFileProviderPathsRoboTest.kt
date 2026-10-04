package com.mckimquyen.watermark.export

import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.utils.FileUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * BUG-57: `filepaths.xml` khai root `Pictures/EasyWaterMark/` (tên cũ trước rebrand) nhưng thư mục
 * xuất thật là `Pictures/<FileUtils.outPutFolderName>` — nhánh legacy (API<29) của
 * `BatchExportEngine` gọi `FileProvider.getUriForFile` cho file ở đó và ném
 * `IllegalArgumentException: Failed to find configured root` dù file đã ghi xong.
 */
@RunWith(RobolectricTestRunner::class)
class ExportFileProviderPathsRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun getUriForFile_fileInExportFolder_doesNotThrow() {
        // Robolectric đặt getExternalStoragePublicDirectory() ngoài getExternalStorageDirectory();
        // máy thật Pictures nằm DƯỚI root external-path, nên dựng đúng cấu trúc đó.
        val picturesDir = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES)
        val exportDir = File(picturesDir, FileUtils.outPutFolderName).apply { mkdirs() }
        val file = File(exportDir, "bug57_test.jpg").apply { writeText("x") }

        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        assertThat(uri.toString()).contains("bug57_test.jpg")
    }
}
