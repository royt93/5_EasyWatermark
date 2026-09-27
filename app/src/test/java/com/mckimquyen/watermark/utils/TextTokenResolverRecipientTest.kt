package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IDEA-10: token {recipient} phải thay đúng mã người nhận, và rỗng an toàn khi batch không gắn ai.
 */
class TextTokenResolverRecipientTest {

    @Test
    fun `recipient nam trong danh sach token duoc ho tro`() {
        assertThat(TextTokenResolver.SUPPORTED_TOKENS).contains("recipient")
    }

    @Test
    fun `thay the dung ma nguoi nhan`() {
        val result = TextTokenResolver.resolve(
            "© Roy Studio · {recipient}",
            mapOf("recipient" to "VIP-A")
        )
        assertThat(result).isEqualTo("© Roy Studio · VIP-A")
    }

    @Test
    fun `khong gan nguoi nhan thi token bien mat chu khong de lai dau ngoac`() {
        val result = TextTokenResolver.resolve(
            "© Roy {recipient}",
            mapOf("recipient" to null)
        )
        assertThat(result).isEqualTo("© Roy ")
        assertThat(result).doesNotContain("{recipient}")
    }

    @Test
    fun `token lap lai nhieu lan deu duoc thay`() {
        val result = TextTokenResolver.resolve(
            "{recipient}-{recipient}",
            mapOf("recipient" to "X1")
        )
        assertThat(result).isEqualTo("X1-X1")
    }

    @Test
    fun `khong lam anh huong cac token cu`() {
        val result = TextTokenResolver.resolve(
            "{date} {recipient} {seq}",
            mapOf("date" to "2026-09-27", "recipient" to "VIP-A", "seq" to "1")
        )
        assertThat(result).isEqualTo("2026-09-27 VIP-A 1")
    }
}
