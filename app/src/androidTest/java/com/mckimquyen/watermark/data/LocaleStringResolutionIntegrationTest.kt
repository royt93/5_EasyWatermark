package com.mckimquyen.watermark.data

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Self-audit 2026-09-27: 10/13 locale thiếu 272 key dịch trong `values-xx/strings.xml` (nguồn),
 * đã bổ sung + phủ bằng [com.mckimquyen.watermark.StringLocalizationParityTest] (JVM, đọc trực
 * tiếp file XML nguồn) và `GalleryFragmentPluralsLocalizationRoboTest` (Robolectric, giả lập
 * locale). Cả 2 test trên đọc resource ở mức source/Robolectric shadow — KHÔNG chứng minh bản
 * dịch còn nguyên vẹn sau khi resource được đóng gói thật vào APK (aapt2 có thể strip locale nếu
 * `resConfigs` giới hạn — app này hiện không cấu hình vậy, nhưng verify trực tiếp trên binary cài
 * thật trên device, không suy đoán từ `build.gradle.kts`).
 *
 * Dùng key `share_zip_failed` vì đây là 1 trong các key MỚI thêm ở đợt fix này (trước đó thiếu ở
 * 10/13 locale) — so lệch với bản `en` chứng minh đúng bản dịch mới được đóng gói, không phải
 * trùng hợp 2 chuỗi giống hệt từ trước.
 */
@RunWith(AndroidJUnit4::class)
class LocaleStringResolutionIntegrationTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private fun resourcesFor(languageTag: String) = appContext
        .createConfigurationContext(
            Configuration(appContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        )
        .resources

    @Test
    fun russianApk_newlyAddedStringAndPlurals_areTranslated_notEnglishFallback() {
        val ru = resourcesFor("ru")
        val en = resourcesFor("en")

        assertThat(ru.getString(R.string.share_zip_failed)).isNotEqualTo(en.getString(R.string.share_zip_failed))
        for (n in intArrayOf(1, 2, 5)) {
            assertThat(ru.getQuantityString(R.plurals.gallery_select_photo_count, n, n))
                .isNotEqualTo(en.getQuantityString(R.plurals.gallery_select_photo_count, n, n))
            assertThat(ru.getQuantityString(R.plurals.gallery_selected_count, n, n))
                .isNotEqualTo(en.getQuantityString(R.plurals.gallery_selected_count, n, n))
            assertThat(ru.getQuantityString(R.plurals.batch_caption_subtitle, n, n))
                .isNotEqualTo(en.getQuantityString(R.plurals.batch_caption_subtitle, n, n))
        }
    }

    @Test
    fun japaneseApk_newlyAddedString_isTranslated_notEnglishFallback() {
        val ja = resourcesFor("ja")
        val en = resourcesFor("en")

        assertThat(ja.getString(R.string.share_zip_failed)).isNotEqualTo(en.getString(R.string.share_zip_failed))
        assertThat(ja.getQuantityString(R.plurals.gallery_selected_count, 1, 1))
            .isNotEqualTo(en.getQuantityString(R.plurals.gallery_selected_count, 1, 1))
    }
}
