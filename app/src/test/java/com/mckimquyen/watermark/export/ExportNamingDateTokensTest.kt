package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * FEAT-25: Kiểm thử phân giải token thời gian {date}, {time}, {datetime} trong [ExportNaming.resolveTextTokens].
 */
@RunWith(RobolectricTestRunner::class)
class ExportNamingDateTokensTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val naming = ExportNaming()

    @Test
    fun resolveTextTokens_withStandardExifDate_resolvesDateTimeAndTokens() {
        val exif = ExifModel(dateTime = "2026:09:29 14:30:22")
        val info = ImageInfo(
            uri = Uri.parse("content://media/test.jpg"),
            exifModel = exif
        )

        val resolvedDate = naming.resolveTextTokens("{date}", info, context.contentResolver, 0)
        val resolvedTime = naming.resolveTextTokens("{time}", info, context.contentResolver, 0)
        val resolvedDateTime = naming.resolveTextTokens("{datetime}", info, context.contentResolver, 0)

        assertThat(resolvedDate).isEqualTo("2026-09-29")
        assertThat(resolvedTime).isEqualTo("14:30")
        assertThat(resolvedDateTime).isEqualTo("2026-09-29 14:30")
    }

    @Test
    fun resolveTextTokens_withCombinedTemplate_replacesAllTokens() {
        val exif = ExifModel(dateTime = "2026:09:29 08:15:00")
        val info = ImageInfo(
            uri = Uri.parse("content://media/photo.jpg"),
            exifModel = exif
        )

        val template = "Chụp ngày {date} lúc {time} (Mã: {seq})"
        val result = naming.resolveTextTokens(template, info, context.contentResolver, 0)

        assertThat(result).isEqualTo("Chụp ngày 2026-09-29 lúc 08:15 (Mã: 1)")
    }

    @Test
    fun resolveTextTokens_withoutExif_fallsBackToValidDateAndTime() {
        val info = ImageInfo(uri = Uri.parse("content://media/no_exif.jpg"))

        val date = naming.resolveTextTokens("{date}", info, context.contentResolver, 0)
        val time = naming.resolveTextTokens("{time}", info, context.contentResolver, 0)
        val datetime = naming.resolveTextTokens("{datetime}", info, context.contentResolver, 0)

        assertThat(date).matches("""\d{4}-\d{2}-\d{2}""")
        assertThat(time).matches("""\d{2}:\d{2}""")
        assertThat(datetime).matches("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }

    @Test
    fun queryFileLastModified_withLocalFile_returnsFileLastModified() {
        val tempFile = File.createTempFile("test_date", ".jpg").apply {
            setLastModified(1_700_000_000_000L)
            deleteOnExit()
        }
        val uri = Uri.fromFile(tempFile)

        val lm = ExportNaming.queryFileLastModified(context.contentResolver, uri)

        assertThat(lm).isNotNull()
        assertThat(lm).isEqualTo(1_700_000_000_000L)
    }
}
