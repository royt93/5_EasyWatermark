package com.mckimquyen.watermark.ui.dlg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-16: [EditTextContentFragment.initialText] phải trả về chuỗi rỗng (không phải literal "null")
 * khi `shareViewModel.waterMark.value` chưa emit lúc dialog mở.
 */
class EditTextContentFragmentRoboTest {

    @Test
    fun nullText_returnsEmptyString_notNullLiteral() {
        assertThat(EditTextContentFragment.initialText(null)).isEqualTo("")
    }

    @Test
    fun nonNullText_returnsSameValue() {
        assertThat(EditTextContentFragment.initialText("Hello watermark")).isEqualTo("Hello watermark")
    }
}
