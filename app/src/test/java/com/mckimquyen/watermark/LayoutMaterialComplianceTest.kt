package com.mckimquyen.watermark

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit test verifying Material You compliance across all layout XML files.
 * Checks for absence of obsolete raster drawables and hardcoded text colors in layouts.
 */
class LayoutMaterialComplianceTest {

    @Test
    fun allLayouts_doNotReferenceObsoleteDrawablesOrHardcodedTextColors() {
        val rootDir = File("").absoluteFile
        val layoutDir = if (File(rootDir, "src/main/res/layout").exists()) {
            File(rootDir, "src/main/res/layout")
        } else {
            File(rootDir, "app/src/main/res/layout")
        }

        assertThat(layoutDir.exists()).isTrue()
        val xmlFiles = layoutDir.listFiles { _, name -> name.endsWith(".xml") } ?: emptyArray()
        assertThat(xmlFiles.size).isAtLeast(30)

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        for (file in xmlFiles) {
            val content = file.readText()

            // 1. Check no references to baseline_cancel_24 (replaced by ic_close / modern vector)
            assertWithMessage("File ${file.name} still references obsolete baseline_cancel_24")
                .that(content)
                .doesNotContain("baseline_cancel_24")

            // 2. Check no hardcoded legacy textColor="#1C1B1F"
            assertWithMessage("File ${file.name} contains hardcoded #1C1B1F instead of ?attr/colorOnSurface")
                .that(content)
                .doesNotContain("#1C1B1F")

            // 3. Check XML is well-formed
            val doc = dBuilder.parse(file)
            assertThat(doc.documentElement).isNotNull()
        }
    }
}
