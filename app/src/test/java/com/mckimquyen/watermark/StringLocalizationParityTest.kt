package com.mckimquyen.watermark

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit test verifying 100% 1-to-1 parity between base strings.xml and Vietnamese values-vi/strings.xml.
 * Ensures no missing translations and matching format specifiers (%s, %d, %1$s, etc.).
 */
class StringLocalizationParityTest {

    private val formatSpecifierRegex = Pattern.compile("%(\\d+\\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]")

    @Test
    fun allEnglishStrings_existInVietnameseWithMatchingFormatSpecifiers() {
        val rootDir = File("").absoluteFile
        val resDir = if (File(rootDir, "src/main/res").exists()) {
            File(rootDir, "src/main/res")
        } else {
            File(rootDir, "app/src/main/res")
        }

        val enFile = File(resDir, "values/strings.xml")
        val viFile = File(resDir, "values-vi/strings.xml")

        assertThat(enFile.exists()).isTrue()
        assertThat(viFile.exists()).isTrue()

        val enStrings = parseStringsXml(enFile)
        val viStrings = parseStringsXml(viFile)

        // 1. Key set equality (ignoring non-translatable strings)
        val missingInVi = enStrings.keys - viStrings.keys
        assertThat(missingInVi).isEmpty()

        // 2. Format specifier equality
        for ((key, enValue) in enStrings) {
            val viValue = viStrings[key] ?: continue
            val enSpecifiers = extractSpecifiers(enValue)
            val viSpecifiers = extractSpecifiers(viValue)
            assertWithMessage("Format specifiers mismatch for key: $key")
                .that(viSpecifiers)
                .containsExactlyElementsIn(enSpecifiers)
        }
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                val translatable = node.getAttribute("translatable")
                if (translatable == "false") continue
                val name = node.getAttribute("name")
                val text = node.textContent
                result[name] = text
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
