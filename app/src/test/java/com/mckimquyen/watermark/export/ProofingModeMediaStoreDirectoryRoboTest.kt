package com.mckimquyen.watermark.export

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.FileUtils
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * P1 review pass 8 (sửa lại sau smoke test thật trên TECNO KJ7): bản fix ĐẦU TIÊN (đổi
 * `RELATIVE_PATH` từ "Documents/..." sang "Pictures/..." để khớp thư mục ảnh) SAI — Android Q+ cấm
 * `MediaStore.Files.insert()` file không phải media (HTML) vào "Pictures/", chỉ cho
 * `[Download, Documents]` (log thật: "Primary directory Pictures not allowed for
 * content://media/external_primary/file"). Robolectric không mô phỏng giới hạn này nên test cũ
 * (assert RELATIVE_PATH == "Pictures/...") pass giả — trên máy thật, `insert()` ném exception,
 * `writeIndex()` catch rồi trả `null`, KHÔNG sinh được file nào (còn tệ hơn trước khi fix).
 *
 * Fix thật: giữ nguyên `Documents/` (thư mục ĐƯỢC PHÉP) cho `writeMediaStore()`, nhúng ảnh base64
 * thẳng vào HTML (`ProofingMode.embedImages`) thay vì path tương đối — tự chứa, không phụ thuộc
 * thư mục ảnh nằm ở đâu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ProofingModeMediaStoreDirectoryRoboTest {

    @Test
    fun writeIndex_onQPlus_insertsIntoAllowedDocumentsDirectory_notPictures() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val entries = listOf(ProofingMode.Entry(1, "ewm_1.jpg"))

        val uri = runBlocking { ProofingMode.writeIndex(context, entries, outputDirectoryUri = null) }

        // Regression guard: PHẢI thành công (khác bản fix sai trước — insert() vào "Pictures/" bị
        // Android từ chối, writeIndex() trả null, không sinh được file nào).
        assertThat(uri).isNotNull()
        val insertStatement = shadowOf(context.contentResolver).insertStatements
            .last { it.contentValues.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) == ProofingMode.INDEX_FILE_NAME }
        assertThat(insertStatement.contentValues.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            .isEqualTo("Documents/${FileUtils.outPutFolderName}/")
    }

    @Test
    fun embedImages_entryWithUri_replacesImageSrcWithBase64DataUri() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageUri = Uri.parse("content://media/external/images/media/999")
        val fakeBytes = "FAKE_JPEG_BYTES".toByteArray()
        shadowOf(context.contentResolver).registerInputStream(imageUri, java.io.ByteArrayInputStream(fakeBytes))

        val result = ProofingMode.embedImages(
            context.contentResolver,
            listOf(ProofingMode.Entry(1, "ewm_1.jpg", uri = imageUri))
        )

        val expectedBase64 = android.util.Base64.encodeToString(fakeBytes, android.util.Base64.NO_WRAP)
        assertThat(result.single().imageSrc).isEqualTo("data:image/jpeg;base64,$expectedBase64")
        // fileName (dùng cho caption) không đổi — chỉ imageSrc (dùng cho src=) bị thay.
        assertThat(result.single().fileName).isEqualTo("ewm_1.jpg")
    }

    @Test
    fun embedImages_entryWithoutUri_keepsRelativeFileNameAsImageSrc() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val result = ProofingMode.embedImages(context.contentResolver, listOf(ProofingMode.Entry(1, "ewm_1.jpg")))

        // SAF/legacy không truyền uri -> giữ hành vi cũ (path tương đối), không đổi gì.
        assertThat(result.single().imageSrc).isEqualTo("ewm_1.jpg")
    }

    @Test
    fun buildHtml_usesImageSrc_notFileName_forImgTag() {
        val html = ProofingMode.buildHtml(
            listOf(ProofingMode.Entry(sequence = 1, fileName = "real_name.jpg", imageSrc = "data:image/jpeg;base64,QUJD"))
        )

        assertThat(html).contains("src=\"data:image/jpeg;base64,QUJD\"")
        // Caption vẫn hiện tên file thật (người đọc cần biết tên), không phải base64.
        assertThat(html).contains("real_name.jpg")
        assertThat(html).doesNotContain("src=\"real_name.jpg\"")
    }
}
