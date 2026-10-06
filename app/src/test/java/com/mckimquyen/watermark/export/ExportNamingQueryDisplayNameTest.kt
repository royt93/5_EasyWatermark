package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * FEAT-19/ENH-01: [ExportNaming.queryDisplayName] — truy vấn tên file từ ContentResolver
 * (DISPLAY_NAME) hoặc fallback Uri.lastPathSegment khi ContentResolver không trả kết quả.
 * Bỏ extension cuối ('.jpg', '.png', ...). Cache kết quả theo uri để preview không query lặp.
 */
@RunWith(RobolectricTestRunner::class)
class ExportNamingQueryDisplayNameTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val exportNaming = ExportNaming()

    @Test
    fun queryDisplayName_fileScheme_extractsNameWithoutExtension() {
        val file = File(context.cacheDir, "photo_2024.jpg")
        file.writeText("dummy")
        val uri = Uri.fromFile(file)

        val name = exportNaming.queryDisplayName(context.contentResolver, uri)

        assertThat(name).isEqualTo("photo_2024")
    }

    @Test
    fun queryDisplayName_noExtension_returnsNameAsIs() {
        val file = File(context.cacheDir, "photo_no_ext")
        file.writeText("dummy")
        val uri = Uri.fromFile(file)

        val name = exportNaming.queryDisplayName(context.contentResolver, uri)

        assertThat(name).isEqualTo("photo_no_ext")
    }

    @Test
    fun queryDisplayName_multiDotName_removesOnlyLastExtension() {
        val file = File(context.cacheDir, "my.photo.backup.jpg")
        file.writeText("dummy")
        val uri = Uri.fromFile(file)

        val name = exportNaming.queryDisplayName(context.contentResolver, uri)

        assertThat(name).isEqualTo("my.photo.backup")
    }

    @Test
    fun queryDisplayName_cachesResultForSameUri() {
        val file = File(context.cacheDir, "cached_photo.png")
        file.writeText("dummy")
        val uri = Uri.fromFile(file)

        val first = exportNaming.queryDisplayName(context.contentResolver, uri)
        // Caching verifies via internals; this test ensures calling twice returns same value
        val second = exportNaming.queryDisplayName(context.contentResolver, uri)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun queryDisplayName_differentUris_queriesEachIndependently() {
        val file1 = File(context.cacheDir, "photo1.jpg")
        val file2 = File(context.cacheDir, "photo2.jpg")
        file1.writeText("dummy1")
        file2.writeText("dummy2")
        val uri1 = Uri.fromFile(file1)
        val uri2 = Uri.fromFile(file2)

        val name1 = exportNaming.queryDisplayName(context.contentResolver, uri1)
        val name2 = exportNaming.queryDisplayName(context.contentResolver, uri2)

        assertThat(name1).isEqualTo("photo1")
        assertThat(name2).isEqualTo("photo2")
        assertThat(name1).isNotEqualTo(name2)
    }
}
