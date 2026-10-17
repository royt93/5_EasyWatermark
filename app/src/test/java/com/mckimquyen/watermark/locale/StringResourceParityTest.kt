package com.mckimquyen.watermark.locale

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceParityTest {
    @Test
    fun everyLocaleHasTranslatableKeysAndMatchingFormatArguments() {
        val resources = File("src/main/res")
        val base = parseStrings(File(resources, "values/strings.xml"))
        val translatable = base.filterValues { it.translatable }.keys
        val localeFiles = resources.listFiles { dir -> dir.isDirectory && dir.name.startsWith("values-") }
            .orEmpty().map { File(it, "strings.xml") }.filter(File::isFile)

        assertThat(localeFiles).hasSize(13)
        localeFiles.forEach { file ->
            val localized = parseStrings(file)
            assertThat(localized.keys).containsAtLeastElementsIn(translatable)
            translatable.forEach { key ->
                assertWithMessage("$key in ${file.parentFile?.name}")
                    .that(formatArguments(localized.getValue(key).text))
                    .containsExactlyElementsIn(formatArguments(base.getValue(key).text))
            }
        }
    }

    @Test
    fun auditedTranslations_keepCorrectLanguageAndOpacityMeaning() {
        val resources = File("src/main/res")
        val japanese = parseStrings(File(resources, "values-ja/strings.xml"))
        val russian = parseStrings(File(resources, "values-ru/strings.xml"))
        val nynorsk = parseStrings(File(resources, "values-nn/strings.xml"))
        val italian = parseStrings(File(resources, "values-it/strings.xml"))
        val french = parseStrings(File(resources, "values-fr/strings.xml"))

        // Lỗi từng thấy: tiếng Ý trong bản Nhật, nút Lưu dịch thành Ẩn, tăng opacity thành tăng transparency.
        assertThat(japanese.getValue("tile_mode_title_decal").text).isEqualTo("単一")
        assertThat(nynorsk.getValue("action_save").text).isEqualTo("Lagre")
        assertThat(russian.getValue("survivability_fix_increase_opacity").text).isEqualTo("Увеличьте непрозрачность")
        assertThat(russian.getValue("brand_compliance_issue_opacity").text).isEqualTo("Слишком низкая непрозрачность")
        assertThat(italian.getValue("redaction_title").text).isEqualTo("Oscuramento intelligente")
        assertThat(french.getValue("store_not_found").text).doesNotContain("Not Store")
        listOf("values-es", "values-pt", "values-pt-rBR").forEach { locale ->
            val strings = parseStrings(File(resources, "$locale/strings.xml"))
            assertThat(strings.getValue("pick_via_system").text).doesNotContain("Choose more")
        }
        listOf("values-pt", "values-pt-rBR").forEach { locale ->
            val strings = parseStrings(File(resources, "$locale/strings.xml"))
            assertThat(strings.getValue("style_alpha").text).isEqualTo("Alfa")
        }
    }

    private data class StringEntry(val text: String, val translatable: Boolean)

    private fun parseStrings(file: File): Map<String, StringEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        return (0 until root.childNodes.length).mapNotNull { index ->
            val node = root.childNodes.item(index) as? Element ?: return@mapNotNull null
            if (node.tagName != "string") return@mapNotNull null
            val name = node.getAttribute("name")
            name to StringEntry(node.textContent, node.getAttribute("translatable") != "false")
        }.toMap()
    }

    private fun formatArguments(value: String): List<String> =
        Regex("%(?:[0-9]+\\$)?[-#+ 0,(<]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z%]")
            .findAll(value)
            .map { it.value }
            .toList()
}
