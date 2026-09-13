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
