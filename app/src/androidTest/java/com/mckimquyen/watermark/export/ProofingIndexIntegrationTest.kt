package com.mckimquyen.watermark.export

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * IDEA-13 integration test trên thiết bị thật: kiểm tra sinh file proof_index.html thật qua DocumentFile.
 */
@RunWith(AndroidJUnit4::class)
class ProofingIndexIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writeIntoDocumentTree_createsProofIndexFile_withValidContent() {
        val testDir = File(context.cacheDir, "proofing_test_dir").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val root = DocumentFile.fromFile(testDir)

        val entries = listOf(
            ProofingMode.Entry(1, "photo_001.jpg"),
            ProofingMode.Entry(3, "photo_003.jpg")
        )

        val indexUri = ProofingMode.writeIntoDocumentTree(root, context.contentResolver, entries)

        assertThat(indexUri).isNotNull()
        // DocumentFile.createFile() có thể điều chỉnh tên theo MIME type; dùng đúng Uri trả về.
        val indexFile = File(indexUri!!.path!!)
        assertThat(indexFile.exists()).isTrue()

        val htmlContent = indexFile.readText()
        assertThat(htmlContent).contains("Client Proofs")
        assertThat(htmlContent).contains("#001")
        assertThat(htmlContent).contains("#003")
        assertThat(htmlContent).doesNotContain("#002")
        assertThat(htmlContent).contains("src=\"photo_001.jpg\"")
        assertThat(htmlContent).contains("src=\"photo_003.jpg\"")

        testDir.deleteRecursively()
    }

    /**
     * BUG-61: decode Skia thật (Robolectric không mô phỏng đúng decode-failure cho bytes rác) — 1 ảnh
     * hỏng giữ path tương đối, ảnh tốt vẫn nhúng thumbnail, không làm hỏng cả batch.
     */
    @Test
    fun embedImages_undecodableImage_keepsRelativeName_whileGoodImageIsEmbedded() {
        val dir = File(context.cacheDir, "proofing_embed_test").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val bad = File(dir, "bad.jpg").apply { writeText("not an image at all") }
        val good = File(dir, "good.jpg")
        good.outputStream().use {
            android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
        }

        val result = ProofingMode.embedImages(
            context.contentResolver,
            listOf(
                ProofingMode.Entry(1, "bad.jpg", uri = android.net.Uri.fromFile(bad)),
                ProofingMode.Entry(2, "good.jpg", uri = android.net.Uri.fromFile(good))
            )
        )

        assertThat(result[0].imageSrc).isEqualTo("bad.jpg")
        assertThat(result[1].imageSrc).startsWith("data:image/jpeg;base64,")
        // Thumbnail nhúng nhỏ hơn hẳn ảnh gốc (không nhúng full-res).
        val embeddedBytes = android.util.Base64.decode(result[1].imageSrc.substringAfter("base64,"), android.util.Base64.NO_WRAP)
        assertThat(embeddedBytes.size.toLong()).isLessThan(good.length())
        dir.deleteRecursively()
    }
}
