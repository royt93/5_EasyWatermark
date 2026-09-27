package com.mckimquyen.watermark.data.model

/**
 * FEAT-13: phân tích danh sách caption theo quy ước một dòng ứng với một ảnh trong batch.
 * Dòng có thể được bọc bằng dấu nháy kép theo CSV; `""` bên trong được giải mã thành một dấu
 * nháy kép. Caption rỗng hợp lệ và có nghĩa ảnh tương ứng không vẽ text watermark.
 */
object BatchCaptionParser {
    sealed interface Validation {
        data object Disabled : Validation

        data class Valid(val captions: List<String>) : Validation

        data class CountMismatch(val expected: Int, val actual: Int) : Validation

        data class InvalidCsv(val lineNumber: Int) : Validation
    }

    fun validate(input: String, expectedCount: Int): Validation {
        if (input.isEmpty()) return Validation.Disabled

        val lines = splitLinesPreservingTrailingEmpty(input)
        val captions = ArrayList<String>(lines.size)
        lines.forEachIndexed { index, line ->
            val parsed = parseLine(line) ?: return Validation.InvalidCsv(index + 1)
            captions += parsed
        }
        return if (captions.size == expectedCount) {
            Validation.Valid(captions)
        } else {
            Validation.CountMismatch(expectedCount, captions.size)
        }
    }

    fun toInput(imageList: List<ImageInfo>): String {
        if (imageList.none { it.caption != null }) return ""
        return imageList.joinToString("\n") { encodeLine(it.caption.orEmpty()) }
    }

    /**
     * IDEA-17: chỉ số dòng (0-based) mà con trỏ ở vị trí [cursor] đang đứng — cũng chính là chỉ số
     * ảnh trong batch, vì một dòng ứng một ảnh. Con trỏ ngay sau `\n` tính là đã sang dòng mới.
     */
    fun lineIndexAt(input: String, cursor: Int): Int {
        val clamped = cursor.coerceIn(0, input.length)
        var lineIndex = 0
        for (index in 0 until clamped) {
            if (input[index] == '\n') lineIndex++
        }
        return lineIndex
    }

    /**
     * IDEA-17: thay nội dung dòng [lineIndex] bằng [text] (kết quả nhận dạng giọng nói), trả về
     * cặp (input mới, vị trí con trỏ mới ở cuối dòng vừa điền). Input chưa đủ dòng thì chèn thêm
     * dòng trống cho tới [lineIndex].
     *
     * [text] được làm phẳng xuống-dòng thành dấu cách: một câu nói tách làm 2 dòng sẽ đẩy lệch mọi
     * caption phía sau sang nhầm ảnh. Text mở đầu bằng `"` được encode theo CSV để [validate] không
     * hiểu nhầm là dòng quoted chưa đóng ngoặc.
     */
    fun replaceLine(input: String, lineIndex: Int, text: String): Pair<String, Int> {
        require(lineIndex >= 0) { "lineIndex phải >= 0, nhận được $lineIndex" }

        val encoded = encodeLine(text.replace('\n', ' ').replace('\r', ' '))
        val lines = splitLinesPreservingTrailingEmpty(input).toMutableList()
        while (lines.size <= lineIndex) {
            lines += ""
        }
        lines[lineIndex] = encoded

        // Mỗi dòng phía trước đóng góp độ dài của nó cộng 1 ký tự '\n' do joinToString chèn vào.
        val cursor = lines.take(lineIndex).sumOf { it.length + 1 } + encoded.length
        return lines.joinToString("\n") to cursor
    }

    private fun splitLinesPreservingTrailingEmpty(input: String): List<String> {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<String>()
        var start = 0
        normalized.forEachIndexed { index, char ->
            if (char == '\n') {
                result += normalized.substring(start, index)
                start = index + 1
            }
        }
        result += normalized.substring(start)
        return result
    }

    private fun parseLine(line: String): String? {
        if (!line.startsWith('"')) return line
        val result = StringBuilder(line.length)
        var index = 1
        while (index < line.length) {
            when {
                line[index] != '"' -> result.append(line[index++])
                index + 1 < line.length && line[index + 1] == '"' -> {
                    result.append('"')
                    index += 2
                }
                index == line.lastIndex -> return result.toString()
                else -> return null
            }
        }
        return null
    }

    private fun encodeLine(caption: String): String =
        if (caption.startsWith('"')) {
            "\"${caption.replace("\"", "\"\"")}\""
        } else {
            caption
        }
}
