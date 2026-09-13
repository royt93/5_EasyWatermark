package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatchCaptionParserTest {

    @Test
    fun validate_emptyInput_returnsDisabled() {
        val result = BatchCaptionParser.validate("", expectedCount = 3)
        assertThat(result).isEqualTo(BatchCaptionParser.Validation.Disabled)
    }

    @Test
    fun validate_matchingCount_returnsValid() {
        val input = "Line 1\nLine 2\nLine 3"
        val result = BatchCaptionParser.validate(input, expectedCount = 3)
        assertThat(result).isInstanceOf(BatchCaptionParser.Validation.Valid::class.java)
        val valid = result as BatchCaptionParser.Validation.Valid
        assertThat(valid.captions).containsExactly("Line 1", "Line 2", "Line 3").inOrder()
    }

    @Test
    fun validate_countMismatch_returnsCountMismatch() {
        val input = "Line 1\nLine 2"
        val result = BatchCaptionParser.validate(input, expectedCount = 4)
        assertThat(result).isEqualTo(BatchCaptionParser.Validation.CountMismatch(expected = 4, actual = 2))
    }

    @Test
    fun validate_quotedCsvLines_decodesProperly() {
        val input = "\"Hello, World\"\n\"Line with \"\"quotes\"\" inside\""
        val result = BatchCaptionParser.validate(input, expectedCount = 2)
        assertThat(result).isInstanceOf(BatchCaptionParser.Validation.Valid::class.java)
        val valid = result as BatchCaptionParser.Validation.Valid
        assertThat(valid.captions).containsExactly("Hello, World", "Line with \"quotes\" inside").inOrder()
    }

    @Test
    fun validate_invalidCsvQuotes_returnsInvalidCsv() {
        val input = "Valid line\n\"Unclosed quote line"
        val result = BatchCaptionParser.validate(input, expectedCount = 2)
        assertThat(result).isEqualTo(BatchCaptionParser.Validation.InvalidCsv(lineNumber = 2))
    }

    @Test
    fun toInput_emptyOrAllNull_returnsEmpty() {
        val images = listOf(
            ImageInfo(uri = android.net.Uri.EMPTY, caption = null),
            ImageInfo(uri = android.net.Uri.EMPTY, caption = null)
        )
        val result = BatchCaptionParser.toInput(images)
        assertThat(result).isEmpty()
    }

    @Test
    fun toInput_withCaptions_joinsWithNewlines() {
        val images = listOf(
            ImageInfo(uri = android.net.Uri.EMPTY, caption = "Image 1"),
            ImageInfo(uri = android.net.Uri.EMPTY, caption = "\"Quoted\"")
        )
        val result = BatchCaptionParser.toInput(images)
        assertThat(result).isEqualTo("Image 1\n\"\"\"Quoted\"\"\"")
    }
}
