package com.mckimquyen.watermark.data.repo

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-25: `WaterMarkRepository.waterMark` Flow không được crash khi DataStore chứa ordinal ngoài
 * range hợp lệ cho `KEY_TEXT_STYLE`/`KEY_TEXT_TYPEFACE` (dữ liệu hỏng, restore từ backup version
 * khác...) — trước fix, `TextPaintStyle.obtainSealedClass()`/`TextTypeface.obtainSealedClass()`
 * ném `IllegalArgumentException` bên trong `.map`, nằm SAU `.catch` trong chain nên không được
 * bắt, văng thẳng ra mọi collector (`MainViewModel`, `AboutViewModel`...).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryCorruptedOrdinalRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dataStore = newTestWaterMarkDataStore(context)
    private lateinit var repo: WaterMarkRepository

    private val keyTextStyle = intPreferencesKey(WaterMarkRepository.SP_KEY_TEXT_STYLE)
    private val keyTextTypeface = intPreferencesKey(WaterMarkRepository.SP_KEY_TEXT_TYPEFACE)

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, dataStore)
    }

    @Test
    fun waterMark_outOfRangeTextStyleOrdinal_doesNotCrash_fallsBackToFill() = runBlocking {
        dataStore.edit { it[keyTextStyle] = 999 }

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.textStyle).isEqualTo(TextPaintStyle.Fill)
    }

    @Test
    fun waterMark_outOfRangeTextTypefaceOrdinal_doesNotCrash_fallsBackToNormal() = runBlocking {
        dataStore.edit { it[keyTextTypeface] = -7 }

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.textTypeface).isEqualTo(TextTypeface.Normal)
    }

    @Test
    fun waterMark_validOrdinals_unaffectedByFix() = runBlocking {
        dataStore.edit {
            it[keyTextStyle] = 1
            it[keyTextTypeface] = 2
        }

        val waterMark = repo.waterMark.first()

        assertThat(waterMark.textStyle).isEqualTo(TextPaintStyle.Stroke)
        assertThat(waterMark.textTypeface).isEqualTo(TextTypeface.Bold)
    }
}
