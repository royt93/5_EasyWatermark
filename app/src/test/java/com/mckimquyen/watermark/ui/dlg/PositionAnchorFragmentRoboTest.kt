package com.mckimquyen.watermark.ui.dlg

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Memory leak prevention test:
 * `anchorButtons` MUST NOT be cached with `by lazy` at Fragment level because BottomSheetDialogFragment
 * views can be recreated multiple times in the same fragment instance.
 * Property getter ensures binding is read dynamically without leaking dead view references.
 */
@RunWith(RobolectricTestRunner::class)
class PositionAnchorFragmentRoboTest {

    @Test
    fun anchorButtons_hasNoLazyDelegateField() {
        val fields = PositionAnchorBottomSheetFragment::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("anchorButtons\$delegate")
    }

    @Test
    fun anchorButtons_hasNoBackingFieldAtAll() {
        val fields = PositionAnchorBottomSheetFragment::class.java.declaredFields.map { it.name }
        assertThat(fields.none { it.contains("anchorButtons") }).isTrue()
    }
}
