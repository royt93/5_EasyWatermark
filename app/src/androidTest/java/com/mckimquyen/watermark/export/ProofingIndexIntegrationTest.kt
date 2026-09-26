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
}
