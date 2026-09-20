package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ImageInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportNamingConflictTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val exportNaming = ExportNaming()

    @Test
    fun buildVersionedName_versionLessThanOrEqualToOne_returnsOriginalName() {
        assertThat(exportNaming.buildVersionedName("image.jpg", 1)).isEqualTo("image.jpg")
        assertThat(exportNaming.buildVersionedName("image.jpg", 0)).isEqualTo("image.jpg")
    }

    @Test
    fun buildVersionedName_withExtension_insertsVersionBeforeExtension() {
        assertThat(exportNaming.buildVersionedName("photo.jpg", 2)).isEqualTo("photo_v2.jpg")
        assertThat(exportNaming.buildVersionedName("photo.png", 3)).isEqualTo("photo_v3.png")
        assertThat(exportNaming.buildVersionedName("my.cool.photo.webp", 4)).isEqualTo("my.cool.photo_v4.webp")
    }

    @Test
    fun buildVersionedName_withoutExtension_appendsVersion() {
        assertThat(exportNaming.buildVersionedName("photo", 2)).isEqualTo("photo_v2")
    }

    @Test
    fun resolveVersionedName_nameNotTaken_returnsOriginalName() {
        val resolved = exportNaming.resolveVersionedName("output.jpg") { false }
        assertThat(resolved).isEqualTo("output.jpg")
    }

    @Test
    fun resolveVersionedName_nameTakenOnce_returnsV2() {
        val existingNames = setOf("output.jpg")
        val resolved = exportNaming.resolveVersionedName("output.jpg") { existingNames.contains(it) }
        assertThat(resolved).isEqualTo("output_v2.jpg")
    }

    @Test
    fun resolveVersionedName_nameTakenMultipleTimes_incrementsToAvailableVersion() {
        val existingNames = setOf("output.jpg", "output_v2.jpg", "output_v3.jpg")
        val resolved = exportNaming.resolveVersionedName("output.jpg") { existingNames.contains(it) }
        assertThat(resolved).isEqualTo("output_v4.jpg")
    }

    @Test
    fun conflictPolicyEnum_idsAndFromId_matchCorrectly() {
        assertThat(ConflictPolicy.fromId(0)).isEqualTo(ConflictPolicy.KEEP_BOTH)
        assertThat(ConflictPolicy.fromId(1)).isEqualTo(ConflictPolicy.RENAME_VERSION)
        assertThat(ConflictPolicy.fromId(2)).isEqualTo(ConflictPolicy.OVERWRITE)
        assertThat(ConflictPolicy.fromId(3)).isEqualTo(ConflictPolicy.SKIP)
        // Fallback
        assertThat(ConflictPolicy.fromId(999)).isEqualTo(ConflictPolicy.KEEP_BOTH)
    }

    @Test
    fun generateOutputName_withPatternAndIndex_resolvesProperly() {
        val dummyUri = Uri.parse("content://media/external/images/media/123")
        val info = ImageInfo(dummyUri)
        val name = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 0,
            outputNamePattern = "watermark_{seq}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        assertThat(name).isEqualTo("watermark_1.jpg")
    }
}
