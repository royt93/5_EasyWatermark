package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** IDEA-13: token {seq3} đệm 3 chữ số, {seq} giữ nguyên 1-based thông thường. */
@RunWith(RobolectricTestRunner::class)
class ExportNamingSeqPadTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val naming = ExportNaming()
    private val info = ImageInfo(Uri.parse("content://media/test.jpg"))

    @Test
    fun seq3_padsWithThreeZeros() {
        assertThat(naming.resolveTextTokens("{seq3}", info, context.contentResolver, 0)).isEqualTo("001")
        assertThat(naming.resolveTextTokens("{seq3}", info, context.contentResolver, 9)).isEqualTo("010")
        assertThat(naming.resolveTextTokens("{seq3}", info, context.contentResolver, 99)).isEqualTo("100")
        assertThat(naming.resolveTextTokens("{seq3}", info, context.contentResolver, 999)).isEqualTo("1000")
    }

    @Test
    fun seq_remainsUnpadded() {
        assertThat(naming.resolveTextTokens("{seq}", info, context.contentResolver, 0)).isEqualTo("1")
        assertThat(naming.resolveTextTokens("{seq}", info, context.contentResolver, 9)).isEqualTo("10")
    }
}
