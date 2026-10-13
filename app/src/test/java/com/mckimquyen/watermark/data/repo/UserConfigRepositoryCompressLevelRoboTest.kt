package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-46: bước/biên chất lượng nén là hằng có tên, dùng chung giữa repo và `Slider` ở dlg_save_file.xml. Lệch 1 bên
 * thì quality đã lưu bị coi là không hợp lệ và reset về mặc định → test khoá 2 phía khớp nhau.
 */
@RunWith(RobolectricTestRunner::class)
class UserConfigRepositoryCompressLevelRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStore = newTestUserDataStore(context)
    private val compressKey = intPreferencesKey(UserConfigRepository.SP_KEY_COMPRESS_LEVEL)
    private var repo: UserConfigRepository? = null

    @Before
    fun setUp() {
        runBlocking { dataStore.edit { it.clear() } }
        repo = UserConfigRepository(dataStore)
    }

    private fun storedLevel(raw: Int?): Int = runBlocking {
        dataStore.edit { if (raw == null) it.remove(compressKey) else it[compressKey] = raw }
        requireNotNull(repo).userPreferences.first().compressLevel
    }

    @Test
    fun constants_matchSliderInLayout() {
        val themed = ContextThemeWrapper(context, R.style.Theme_MyApp)
        val root = LayoutInflater.from(themed).inflate(R.layout.dlg_save_file, FrameLayout(themed), false)
        val slider = root.findViewById<Slider>(R.id.slideQuality)

        assertThat(slider.stepSize).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_STEP.toFloat())
        assertThat(slider.valueFrom).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MIN.toFloat())
        assertThat(slider.valueTo).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MAX.toFloat())
    }

    @Test
    fun defaultLevel_isOnTheStepGrid() {
        assertThat(UserConfigRepository.DEFAULT_COMPRESS_LEVEL % UserConfigRepository.COMPRESS_LEVEL_STEP).isEqualTo(0)
    }

    @Test
    fun validStepValue_isKept() {
        assertThat(storedLevel(60)).isEqualTo(60)
        assertThat(storedLevel(UserConfigRepository.COMPRESS_LEVEL_MIN)).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MIN)
        assertThat(storedLevel(UserConfigRepository.COMPRESS_LEVEL_MAX)).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MAX)
    }

    @Test
    fun offGridValue_resetsToDefault() {
        assertThat(storedLevel(65)).isEqualTo(UserConfigRepository.DEFAULT_COMPRESS_LEVEL)
    }

    @Test
    fun belowMin_isClampedToMin() {
        assertThat(storedLevel(0)).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MIN)
    }

    @Test
    fun aboveMax_isClampedToMax() {
        assertThat(storedLevel(500)).isEqualTo(UserConfigRepository.COMPRESS_LEVEL_MAX)
    }

    @Test
    fun missingValue_usesDefault() {
        assertThat(storedLevel(null)).isEqualTo(UserConfigRepository.DEFAULT_COMPRESS_LEVEL)
    }
}
