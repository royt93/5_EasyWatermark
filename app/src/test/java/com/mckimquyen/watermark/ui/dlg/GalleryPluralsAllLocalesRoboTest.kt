package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File
import java.util.Locale

/**
 * Mở rộng bằng chứng của [GalleryFragmentPluralsLocalizationRoboTest] (chỉ chứng minh riêng `ru`
 * qua Fragment thật) ra CẢ 13 locale hiện có — quét động y hệt
 * [com.mckimquyen.watermark.StringLocalizationParityTest] để tự cover locale mới thêm sau này.
 *
 * Dùng `createConfigurationContext()` (không phải `RuntimeEnvironment.setQualifiers()`) vì API đó
 * đụng `ShadowDisplayManager` → `NoClassDefFoundError: DisplayManagerGlobal` trên bản Robolectric
 * đang dùng — giới hạn môi trường, không phải bug code.
 *
 * Không gõ tay bản dịch kỳ vọng cho 13 ngôn ngữ (không khả thi) — thay vào đó verify locale KHÁC
 * bản `en` mặc định ở 3 quantity đại diện (1/2/5, đủ bắt lại bug gốc: cả `<plurals>` bị thiếu hẳn
 * ở 1 locale khiến MỌI quantity fallback về `values/strings.xml` tiếng Anh).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class GalleryPluralsAllLocalesRoboTest(private val locale: String) {

    companion object {
        private val QUANTITIES_TO_CHECK = intArrayOf(1, 2, 5)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "locale={0}")
        fun locales(): List<String> {
            val rootDir = File("").absoluteFile
            val resDir = if (File(rootDir, "src/main/res").exists()) {
                File(rootDir, "src/main/res")
            } else {
                File(rootDir, "app/src/main/res")
            }
            return resDir.listFiles { f ->
                f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() &&
                    !f.name.startsWith("values-night") && !f.name.matches(Regex("values-v\\d+"))
            }?.map { it.name.removePrefix("values-") }?.sorted().orEmpty()
        }
    }

    // "de-rDE" (định dạng qualifier thư mục Android) → "de-DE" (BCP-47 hợp lệ cho Locale.forLanguageTag).
    private fun resourcesFor(languageTag: String): android.content.res.Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bcp47Tag = languageTag.replace("-r", "-")
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(bcp47Tag)) }
        return context.createConfigurationContext(config).resources
    }

    @Test
    fun locale_pluralsResolveDifferentlyFromEnglishBaseline() {
        val enResources = resourcesFor("en")
        val enValues = QUANTITIES_TO_CHECK.associateWith { n ->
            Triple(
                enResources.getQuantityString(R.plurals.gallery_select_photo_count, n, n),
                enResources.getQuantityString(R.plurals.gallery_selected_count, n, n),
                enResources.getQuantityString(R.plurals.batch_caption_subtitle, n, n)
            )
        }

        val localeResources = resourcesFor(locale)

        for (n in QUANTITIES_TO_CHECK) {
            val (enGallery, enSelected, enCaption) = enValues.getValue(n)

            assertWithMessage("locale=$locale n=$n gallery_select_photo_count vẫn = bản English → nghi fallback")
                .that(localeResources.getQuantityString(R.plurals.gallery_select_photo_count, n, n))
                .isNotEqualTo(enGallery)
            assertWithMessage("locale=$locale n=$n gallery_selected_count vẫn = bản English → nghi fallback")
                .that(localeResources.getQuantityString(R.plurals.gallery_selected_count, n, n))
                .isNotEqualTo(enSelected)
            assertWithMessage("locale=$locale n=$n batch_caption_subtitle vẫn = bản English → nghi fallback")
                .that(localeResources.getQuantityString(R.plurals.batch_caption_subtitle, n, n))
                .isNotEqualTo(enCaption)
        }
    }
}
