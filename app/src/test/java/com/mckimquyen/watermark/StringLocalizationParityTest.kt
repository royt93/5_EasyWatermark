package com.mckimquyen.watermark

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit test verifying 100% 1-to-1 parity between base `values/strings.xml` và MỌI locale
 * `values-xx/strings.xml` hiện có trong repo (2026-09-27: mở rộng từ chỉ `vi` sang tất cả 13 locale
 * sau đợt audit phát hiện 10 locale thiếu 272/422 key, `ru` thiếu 353/422 key — xem
 * `doc/task/BACKLOG.md` mục "Self-audit 2026-09-27").
 *
 * Danh sách locale lấy ĐỘNG bằng cách quét thư mục `values-*` có chứa `strings.xml` — tự động
 * cover locale mới thêm sau này mà không cần sửa test.
 */
@RunWith(Parameterized::class)
class StringLocalizationParityTest(private val locale: String) {

    companion object {
        private fun resDir(): File {
            val rootDir = File("").absoluteFile
            return if (File(rootDir, "src/main/res").exists()) {
                File(rootDir, "src/main/res")
            } else {
                File(rootDir, "app/src/main/res")
            }
        }

        @JvmStatic
        @Parameterized.Parameters(name = "locale={0}")
        fun locales(): List<String> = resDir().listFiles { f ->
            f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() &&
                // values-night*/values-v23../values-v29../values-v30../values-v31../values-v35 là
                // resource qualifier theme/API, KHÔNG phải locale dịch — loại khỏi phạm vi test này.
                !f.name.startsWith("values-night") && !f.name.matches(Regex("values-v\\d+"))
        }?.map { it.name.removePrefix("values-") }?.sorted().orEmpty()
    }

    private val formatSpecifierRegex = Pattern.compile("%(\\d+\\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]")

    @Test
    fun allEnglishStrings_existInLocaleWithMatchingFormatSpecifiers() {
        val resDir = resDir()
        val enFile = File(resDir, "values/strings.xml")
        val localeFile = File(resDir, "values-$locale/strings.xml")

        assertThat(enFile.exists()).isTrue()
        assertThat(localeFile.exists()).isTrue()

        val enStrings = parseStringsXml(enFile)
        val localeStrings = parseStringsXml(localeFile)

        // 1. Key set: locale phải có ÍT NHẤT đủ key của baseline (thiếu key = fallback English im
        // lặng, đúng lỗi đã audit ra ở 2026-09-27).
        val missingInLocale = enStrings.keys - localeStrings.keys
        assertWithMessage("Locale '$locale' thiếu ${missingInLocale.size} key so với baseline")
            .that(missingInLocale)
            .isEmpty()

        // 2. Format specifier equality — sai thứ tự/loại placeholder crash runtime
        // (IllegalFormatException) chứ không chỉ hiển thị sai.
        for ((key, enValue) in enStrings) {
            val localeValue = localeStrings[key] ?: continue
            val enSpecifiers = extractSpecifiers(enValue)
            val localeSpecifiers = extractSpecifiers(localeValue)
            assertWithMessage("Format specifiers mismatch for key '$key' ở locale '$locale'")
                .that(localeSpecifiers)
                .containsExactlyElementsIn(enSpecifiers)
        }
    }

    @Test
    fun localeStrings_doNotContainUntranslatableBaseKeys() {
        // Lint `Untranslatable` (audit 2026-09-28): 6 key base đánh dấu `translatable="false"`
        // (license text, email subject cố định...) từng bị dịch nhầm sang 12/13 locale. Unit test
        // parity cũ bỏ qua translatable=false ở CẢ 2 phía nên không bắt được — khoá lại bằng test
        // riêng: base đánh dấu translatable=false thì KHÔNG locale nào được có key đó.
        val resDir = resDir()
        val enFile = File(resDir, "values/strings.xml")
        val localeFile = File(resDir, "values-$locale/strings.xml")

        val untranslatableKeys = parseKeyNames(enFile, onlyUntranslatable = true)
        val localeKeys = parseKeyNames(localeFile, onlyUntranslatable = false)

        val leaked = untranslatableKeys intersect localeKeys
        assertWithMessage(
            "Locale '$locale' chứa ${leaked.size} key base đánh dấu translatable=\"false\" " +
                "lẽ ra không nên dịch: $leaked"
        ).that(leaked).isEmpty()
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(file).apply { documentElement.normalize() }

    /** Tên (`name`) mọi `<string>`/`<plurals>` trong file; `onlyUntranslatable` lọc còn key
     * `translatable="false"`. */
    private fun parseKeyNames(file: File, onlyUntranslatable: Boolean): Set<String> {
        val doc = parseXml(file)
        val names = mutableSetOf<String>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node !is Element) continue
                if (onlyUntranslatable && node.getAttribute("translatable") != "false") continue
                names += node.getAttribute("name")
            }
        }
        return names
    }

    /**
     * Parse cả `<string>` VÀ `<plurals>` (bug thật phát hiện lúc audit 2026-09-27: `<plurals>`
     * dùng tag khác `<string-array>` nên lần quét trước đó bỏ sót — `gallery_select_photo_count`,
     * `gallery_selected_count`, `batch_caption_subtitle` thiếu ở 12/13 locale, chỉ Android Lint
     * `MissingTranslation` bắt được, unit test cũ (chỉ đọc `<string>`) không hề phát hiện).
     * Đại diện giá trị `<plurals>` bằng item `quantity="other"` — CLDR luôn có category này ở mọi
     * locale, đủ để so khớp format specifier.
     */
    private fun parseStringsXml(file: File): Map<String, String> {
        val doc = parseXml(file)

        val result = mutableMapOf<String, String>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                if (node.getAttribute("translatable") == "false") continue
                result[node.getAttribute("name")] = node.textContent
            }
        }

        val pluralsNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralsNodes.length) {
            val node = pluralsNodes.item(i)
            if (node is Element) {
                if (node.getAttribute("translatable") == "false") continue
                val otherItem = (0 until node.childNodes.length)
                    .map { node.childNodes.item(it) }
                    .filterIsInstance<Element>()
                    .firstOrNull { it.getAttribute("quantity") == "other" }
                result[node.getAttribute("name")] = otherItem?.textContent.orEmpty()
            }
        }

        return result
    }

    private fun extractSpecifiers(text: String): List<String> {
        val matcher = formatSpecifierRegex.matcher(text)
        val list = mutableListOf<String>()
        while (matcher.find()) {
            list.add(matcher.group())
        }
        return list
    }
}
