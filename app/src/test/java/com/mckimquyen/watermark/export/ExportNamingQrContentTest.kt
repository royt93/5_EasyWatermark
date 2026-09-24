package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * IDEA-07: [ExportNaming.resolveQrContent] — hash SHA-256 đọc trực tiếp từ [imageInfo].uri (file
 * thật trên đĩa qua `Uri.fromFile`, ContentResolver đọc được file:// scheme không cần FileProvider)
 * để chứng minh 2 ảnh khác nội dung ra 2 hash khác nhau — đúng Acceptance Criteria của ticket.
 */
@RunWith(RobolectricTestRunner::class)
class ExportNamingQrContentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val exportNaming = ExportNaming()

    private fun imageInfoWithContent(fileName: String, content: String): ImageInfo {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return ImageInfo(Uri.fromFile(file))
    }

    @Test
    fun resolveQrContent_differentImages_produceDifferentHash() {
        val imageA = imageInfoWithContent("qr_a.bin", "nội dung ảnh A")
        val imageB = imageInfoWithContent("qr_b.bin", "nội dung ảnh B khác hẳn")

        val contentA = exportNaming.resolveQrContent("{hash}", imageA, context.contentResolver, index = 0, portfolioLink = "")
        val contentB = exportNaming.resolveQrContent("{hash}", imageB, context.contentResolver, index = 1, portfolioLink = "")

        assertThat(contentA).isNotEqualTo(contentB)
        assertThat(contentA).hasLength(64)
        assertThat(contentB).hasLength(64)
    }

    @Test
    fun resolveQrContent_sameImage_sameHashRegardlessOfIndex() {
        val image = imageInfoWithContent("qr_same.bin", "same bytes")

        val content0 = exportNaming.resolveQrContent("{hash}", image, context.contentResolver, index = 0, portfolioLink = "")
        val content5 = exportNaming.resolveQrContent("{hash}", image, context.contentResolver, index = 5, portfolioLink = "")

        assertThat(content0).isEqualTo(content5)
    }

    @Test
    fun resolveQrContent_templateWithPortfolioLinkAndSeq_substitutesAllTokens() {
        val image = imageInfoWithContent("qr_tokens.bin", "abc")

        val content = exportNaming.resolveQrContent(
            template = "{seq}|{portfolio_link}",
            imageInfo = image,
            contentResolver = context.contentResolver,
            index = 2,
            portfolioLink = "https://example.com/me"
        )

        assertThat(content).isEqualTo("3|https://example.com/me")
    }

    @Test
    fun resolveQrContent_templateWithoutTokens_returnsTemplateUnchanged_noHashComputed() {
        val image = imageInfoWithContent("qr_notoken.bin", "abc")

        val content = exportNaming.resolveQrContent("static text", image, context.contentResolver, index = 0, portfolioLink = "")

        assertThat(content).isEqualTo("static text")
    }

    @Test
    fun resolveQrContent_uriCannotBeRead_hashTokenBecomesEmpty_noCrash() {
        val brokenImage = ImageInfo(Uri.parse("content://not.a.real.provider/missing"))

        val content = exportNaming.resolveQrContent("{hash}|{portfolio_link}", brokenImage, context.contentResolver, index = 0, portfolioLink = "link")

        assertThat(content).isEqualTo("|link")
    }
}
