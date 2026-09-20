package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * ENH-29: [FileUtils.cleanOldTempFiles] dọn dẹp các file cache tạm (*_temp_*) để tránh
 * tích luỹ rác không giới hạn theo thời gian.
 */
class FileUtilsCleanTempFilesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cleanOldTempFiles_nonExistentDirectory_returnsZero() {
        val nonExistent = File(tempFolder.root, "does_not_exist")
        val deleted = FileUtils.cleanOldTempFiles(nonExistent)
        assertThat(deleted).isEqualTo(0)
    }

    @Test
    fun cleanOldTempFiles_emptyDirectory_returnsZero() {
        val dir = tempFolder.newFolder("empty_dir")
        val deleted = FileUtils.cleanOldTempFiles(dir)
        assertThat(deleted).isEqualTo(0)
    }

    @Test
    fun cleanOldTempFiles_ignoresNonTempFiles() {
        val dir = tempFolder.newFolder("mixed_dir")
        val permanentFile = File(dir, "permanent_sig.png").apply {
            writeText("permanent")
            setLastModified(1000L)
        }

        val deleted = FileUtils.cleanOldTempFiles(
            directory = dir,
            maxRetainedFiles = 1,
            maxAgeMs = 1000L,
            nowMs = 100_000L
        )

        assertThat(deleted).isEqualTo(0)
        assertThat(permanentFile.exists()).isTrue()
    }

    @Test
    fun cleanOldTempFiles_exceedsRetainedCount_deletesOldestFiles() {
        val dir = tempFolder.newFolder("qrcodes")
        val file1 = File(dir, "qr_temp_1.png").apply { writeText("1"); setLastModified(1000L) }
        val file2 = File(dir, "qr_temp_2.png").apply { writeText("2"); setLastModified(2000L) }
        val file3 = File(dir, "qr_temp_3.png").apply { writeText("3"); setLastModified(3000L) }
        val file4 = File(dir, "qr_temp_4.png").apply { writeText("4"); setLastModified(4000L) }
        val file5 = File(dir, "qr_temp_5.png").apply { writeText("5"); setLastModified(5000L) }

        // Giữ lại tối đa 3 file mới nhất (file5, file4, file3), file1 và file2 phải bị xoá
        val deleted = FileUtils.cleanOldTempFiles(
            directory = dir,
            maxRetainedFiles = 3,
            maxAgeMs = 100_000L,
            nowMs = 6000L
        )

        assertThat(deleted).isEqualTo(2)
        assertThat(file5.exists()).isTrue()
        assertThat(file4.exists()).isTrue()
        assertThat(file3.exists()).isTrue()
        assertThat(file2.exists()).isFalse()
        assertThat(file1.exists()).isFalse()
    }

    @Test
    fun cleanOldTempFiles_olderThanMaxAge_deletesEvenIfWithinRetainedCount() {
        val dir = tempFolder.newFolder("signatures")
        val oldFile = File(dir, "sig_temp_old.png").apply {
            writeText("old")
            setLastModified(1_000L)
        }
        val freshFile = File(dir, "sig_temp_fresh.png").apply {
            writeText("fresh")
            setLastModified(90_000L)
        }

        // maxAgeMs = 10_000L, nowMs = 100_000L
        // oldFile tuổi = 99_000L > 10_000L -> bị xoá dù tổng số file (2) < maxRetainedFiles (3)
        // freshFile tuổi = 10_000L <= 10_000L -> giữ lại
        val deleted = FileUtils.cleanOldTempFiles(
            directory = dir,
            maxRetainedFiles = 3,
            maxAgeMs = 10_000L,
            nowMs = 100_000L
        )

        assertThat(deleted).isEqualTo(1)
        assertThat(oldFile.exists()).isFalse()
        assertThat(freshFile.exists()).isTrue()
    }
}
