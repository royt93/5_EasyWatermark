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

    // ── IDEA-17: dòng đích cho kết quả nhận dạng giọng nói ────────────────────────────────────

    @Test
    fun lineIndexAt_conTroDauInput_traVeDongDauTien() {
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2\nAnh 3", cursor = 0)).isEqualTo(0)
    }

    @Test
    fun lineIndexAt_conTroGiuaDong_traVeDungDongDo() {
        // Vị trí 8 nằm giữa "Anh 2" (dòng index 1).
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2\nAnh 3", cursor = 8)).isEqualTo(1)
    }

    @Test
    fun lineIndexAt_conTroNgaySauXuongDong_daTinhLaDongMoi() {
        // Vị trí 6 = ngay sau '\n' đầu tiên → đã thuộc dòng 2.
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2", cursor = 6)).isEqualTo(1)
        // Vị trí 5 = ngay TRƯỚC '\n' → vẫn thuộc dòng 1.
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2", cursor = 5)).isEqualTo(0)
    }

    @Test
    fun lineIndexAt_dongCuoiRong_vaConTroNgoaiPhamVi_khongNem() {
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\n", cursor = 6)).isEqualTo(1)
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2", cursor = 999)).isEqualTo(1)
        assertThat(BatchCaptionParser.lineIndexAt("Anh 1\nAnh 2", cursor = -5)).isEqualTo(0)
    }

    @Test
    fun replaceLine_thayDungDongGiua_giuNguyenDongKhac() {
        val (output, _) = BatchCaptionParser.replaceLine("Anh 1\nAnh 2\nAnh 3", lineIndex = 1, text = "Hoàng hôn biển")
        assertThat(output).isEqualTo("Anh 1\nHoàng hôn biển\nAnh 3")
    }

    @Test
    fun replaceLine_conTroTraVeNamCuoiDongVuaDien() {
        val text = "Hoàng hôn"
        val (output, cursor) = BatchCaptionParser.replaceLine("Anh 1\nAnh 2\nAnh 3", lineIndex = 1, text = text)
        assertThat(cursor).isEqualTo("Anh 1\n".length + text.length)
        assertThat(output.substring(0, cursor)).endsWith(text)
    }

    @Test
    fun replaceLine_inputNganHonLineIndex_chenThemDongTrong() {
        val (output, _) = BatchCaptionParser.replaceLine("Anh 1", lineIndex = 3, text = "Anh 4")
        assertThat(output).isEqualTo("Anh 1\n\n\nAnh 4")
        // Vẫn đúng 4 dòng để khớp 4 ảnh.
        val result = BatchCaptionParser.validate(output, expectedCount = 4)
        assertThat(result).isInstanceOf(BatchCaptionParser.Validation.Valid::class.java)
    }

    @Test
    fun replaceLine_textCoXuongDong_biLamPhangDeKhongDayLechAnhKhac() {
        val (output, _) = BatchCaptionParser.replaceLine(
            "Anh 1\nAnh 2\nAnh 3",
            lineIndex = 1,
            text = "Câu một\nCâu hai"
        )
        assertThat(output).isEqualTo("Anh 1\nCâu một Câu hai\nAnh 3")
        val valid = BatchCaptionParser.validate(output, expectedCount = 3)
            as BatchCaptionParser.Validation.Valid
        assertThat(valid.captions[1]).isEqualTo("Câu một Câu hai")
        assertThat(valid.captions[2]).isEqualTo("Anh 3")
    }

    @Test
    fun replaceLine_textMoDauBangNgoacKep_roundTripQuaValidateRaDungCaptionGoc() {
        val spoken = "\"Trích dẫn\" của khách"
        val (output, _) = BatchCaptionParser.replaceLine("Anh 1\nAnh 2", lineIndex = 0, text = spoken)

        val result = BatchCaptionParser.validate(output, expectedCount = 2)
        assertThat(result).isInstanceOf(BatchCaptionParser.Validation.Valid::class.java)
        val valid = result as BatchCaptionParser.Validation.Valid
        assertThat(valid.captions[0]).isEqualTo(spoken)
        assertThat(valid.captions[1]).isEqualTo("Anh 2")
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
