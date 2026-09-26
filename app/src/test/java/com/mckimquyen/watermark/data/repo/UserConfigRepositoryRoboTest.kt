package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-24: Kiểm thử UserConfigRepository đảm bảo các cấu hình (format, compress level,
 * max long edge, copyright, output name pattern) hoạt động chính xác và không còn
 * lưu trữ vô ích KEY_CHANGE_LOG.
 */
@RunWith(RobolectricTestRunner::class)
class UserConfigRepositoryRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = newTestUserDataStore(context)
    private lateinit var repo: UserConfigRepository

    @Before
    fun setUp() {
        runBlocking {
            dataStore.edit { it.clear() }
        }
        repo = UserConfigRepository(dataStore)
    }

    @Test
    fun testDefaultUserPreferences() = runBlocking {
        val prefs = repo.userPreferences.first()
        assertThat(prefs.outputFormat).isEqualTo(Bitmap.CompressFormat.JPEG)
        assertThat(prefs.compressLevel).isEqualTo(UserConfigRepository.DEFAULT_COMPRESS_LEVEL)
        assertThat(prefs.maxOutputLongEdge).isEqualTo(UserConfigRepository.DEFAULT_MAX_LONG_EDGE)
        assertThat(prefs.copyright).isEmpty()
        assertThat(prefs.outputNamePattern).isEmpty()
    }

    @Test
    fun testUpdateFormat() = runBlocking {
        repo.updateFormat(Bitmap.CompressFormat.PNG)
        val prefs = repo.userPreferences.first()
        assertThat(prefs.outputFormat).isEqualTo(Bitmap.CompressFormat.PNG)
    }

    @Test
    fun testUpdateCompressLevel() = runBlocking {
        repo.updateCompressLevel(60)
        val prefs = repo.userPreferences.first()
        assertThat(prefs.compressLevel).isEqualTo(60)
    }

    @Test
    fun testUpdateMaxLongEdge() = runBlocking {
        repo.updateMaxLongEdge(1920)
        val prefs = repo.userPreferences.first()
        assertThat(prefs.maxOutputLongEdge).isEqualTo(1920)
    }

    @Test
    fun testUpdateCopyright() = runBlocking {
        repo.updateCopyright("© 2026 SAIGON PHANTOM LABS")
        val prefs = repo.userPreferences.first()
        assertThat(prefs.copyright).isEqualTo("© 2026 SAIGON PHANTOM LABS")
    }

    @Test
    fun testUpdateOutputNamePattern() = runBlocking {
        repo.updateOutputNamePattern("{filename}_wm_{seq}")
        val prefs = repo.userPreferences.first()
        assertThat(prefs.outputNamePattern).isEqualTo("{filename}_wm_{seq}")
    }

    /** FEAT-15: mặc định null = xuất vào Pictures/WaterMarkCreator/ (không chọn thư mục riêng). */
    @Test
    fun testDefaultOutputDirectoryUri_isNull() = runBlocking {
        val prefs = repo.userPreferences.first()
        assertThat(prefs.outputDirectoryUri).isNull()
    }

    @Test
    fun testUpdateOutputDirectoryUri_persistsChosenFolder() = runBlocking {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMyExports")

        repo.updateOutputDirectoryUri(uri)

        val prefs = repo.userPreferences.first()
        assertThat(prefs.outputDirectoryUri).isEqualTo(uri)
    }

    @Test
    fun testUpdateOutputDirectoryUri_withNull_resetsToDefault() = runBlocking {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        repo.updateOutputDirectoryUri(uri)
        assertThat(repo.userPreferences.first().outputDirectoryUri).isEqualTo(uri)

        repo.updateOutputDirectoryUri(null)

        assertThat(repo.userPreferences.first().outputDirectoryUri).isNull()
    }

    /** IDEA-13: lưu cấu hình proofing mode (mặc định false). */
    @Test
    fun testProofingMode_persistsSetting() = runBlocking {
        assertThat(repo.userPreferences.first().proofingMode).isFalse()

        repo.updateProofingMode(true)
        assertThat(repo.userPreferences.first().proofingMode).isTrue()

        repo.updateProofingMode(false)
        assertThat(repo.userPreferences.first().proofingMode).isFalse()
    }
}
