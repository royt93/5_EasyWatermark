package com.mckimquyen.watermark.data.backup

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Template
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Đề xuất F (`doc/feat.md`): round-trip zip serialize thuần `java.util.zip` — JUnit thường, không
 * cần Robolectric (không đụng Android framework).
 */
class BackupRestoreEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeThenReadBackup_roundTripsTemplatesAndSignatures() {
        val templates = listOf(
            Template(id = 1, content = "© Brand 2026", creationDate = Date(1_000L), lastModifiedDate = Date(2_000L)),
            Template(id = 2, content = "line1\nline2\n{filename}", creationDate = Date(3_000L), lastModifiedDate = null)
        )
        val sigFile = tmp.newFile("signature_1.webp").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val output = ByteArrayOutputStream()
        BackupRestoreEngine.writeBackup(output, templates, listOf(sigFile))

        val restored = BackupRestoreEngine.readBackup(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.templates).hasSize(2)
        assertThat(restored.templates.map { it.content }).containsExactly("© Brand 2026", "line1\nline2\n{filename}")
        assertThat(restored.templates[0].creationDate).isEqualTo(Date(1_000L))
        assertThat(restored.templates[0].lastModifiedDate).isEqualTo(Date(2_000L))
        assertThat(restored.templates[1].lastModifiedDate).isNull()
        // id luôn reset về 0 — để Room autoGenerate PK mới khi insert lại, tránh đụng id cũ trên máy đích.
        assertThat(restored.templates.map { it.id }).containsExactly(0, 0)

        assertThat(restored.signatureFiles).hasSize(1)
        assertThat(restored.signatureFiles[0].first).isEqualTo("signature_1.webp")
        assertThat(restored.signatureFiles[0].second).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun writeThenReadBackup_emptyLists_producesEmptyResult() {
        val output = ByteArrayOutputStream()
        BackupRestoreEngine.writeBackup(output, emptyList(), emptyList())

        val restored = BackupRestoreEngine.readBackup(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.templates).isEmpty()
        assertThat(restored.signatureFiles).isEmpty()
    }

    @Test
    fun writeThenReadBackup_nullContent_becomesEmptyString() {
        val templates = listOf(Template(id = 1, content = null, creationDate = null, lastModifiedDate = null))

        val output = ByteArrayOutputStream()
        BackupRestoreEngine.writeBackup(output, templates, emptyList())
        val restored = BackupRestoreEngine.readBackup(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.templates.single().content).isEmpty()
        assertThat(restored.templates.single().creationDate).isNull()
    }
}
