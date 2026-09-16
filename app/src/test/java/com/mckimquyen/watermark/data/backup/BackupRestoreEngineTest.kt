package com.mckimquyen.watermark.data.backup

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Template
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    /**
     * BUG-31: file zip đến từ SAF (input không tin cậy) có thể chứa entry nén nhỏ nhưng giải nén
     * ra khổng lồ (zip-bomb) — `readBackup()` phải từ chối entry vượt ngưỡng thay vì `readBytes()`
     * đọc hết vào RAM. Dùng nội dung lặp lại (rất dễ nén) nên entry "giả 100MB" vẫn nhỏ gọn trong
     * file zip test, nhưng giải nén ra đúng 100MB — mô phỏng trung thực hành vi zip-bomb thật.
     */
    @Test
    fun readBackup_oversizedEntry_isSkipped_validEntriesStillRestored() {
        val hugeContent = ByteArray(100 * 1024 * 1024) { 0 } // 100MB toàn số 0 — nén cực nhỏ

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("signatures/huge.bin"))
            zip.write(hugeContent)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("templates/0.txt"))
            zip.write("1000\n2000\nvalid template".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val restored = BackupRestoreEngine.readBackup(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.signatureFiles).isEmpty()
        assertThat(restored.templates).hasSize(1)
        assertThat(restored.templates.single().content).isEqualTo("valid template")
    }

    @Test
    fun readBackup_entryCountExceedsLimit_stopsWithoutCrashing() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(510) { index ->
                zip.putNextEntry(ZipEntry("templates/$index.txt"))
                zip.write("0\n0\ncontent$index".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        val restored = BackupRestoreEngine.readBackup(ByteArrayInputStream(output.toByteArray()))

        assertThat(restored.templates.size).isAtMost(500)
    }
}
