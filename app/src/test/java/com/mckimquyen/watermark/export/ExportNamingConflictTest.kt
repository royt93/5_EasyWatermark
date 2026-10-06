package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ExifModel
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

    /** BUG-40: {exposure}→"1/125s" (BitmapUtils.kt) chứa '/' — không được lọt nguyên vào tên file. */
    @Test
    fun generateOutputName_exposureTokenContainsSlash_sanitizedInOutputName() {
        val dummyUri = Uri.parse("content://media/external/images/media/123")
        val info = ImageInfo(dummyUri).copy(exifModel = ExifModel(exposureTime = "1/125s"))
        val name = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 0,
            outputNamePattern = "photo_{exposure}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        assertThat(name).isEqualTo("photo_1_125s.jpg")
        assertThat(name).doesNotContain("/")
    }

    /** BUG-40: {fnumber}→"f/2.8" — cùng lý do trên. */
    @Test
    fun generateOutputName_fnumberTokenContainsSlash_sanitizedInOutputName() {
        val dummyUri = Uri.parse("content://media/external/images/media/123")
        val info = ImageInfo(dummyUri).copy(exifModel = ExifModel(fNumber = "f/2.8"))
        val name = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 0,
            outputNamePattern = "photo_{fnumber}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        assertThat(name).isEqualTo("photo_f_2.8.jpg")
    }

    /**
     * BUG-AUDIT-2026-09-29: pattern "{filename}" trên ảnh có DISPLAY_NAME rỗng/chỉ-đuôi (lastPathSegment
     * ".jpg" -> substringBeforeLast('.') ra "") từng sinh tên file ẩn ".jpg" (dotfile, dễ bị ghi đè
     * hàng loạt). Phải fallback về pattern rỗng ("ewm_{timestamp}") thay vì trả base rỗng.
     */
    @Test
    fun generateOutputName_patternResolvesToEmptyString_fallsBackToTimestampPrefix() {
        val emptyNameUri = Uri.parse("content://media/external/images/media/.jpg")
        val info = ImageInfo(emptyNameUri)
        val name = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 0,
            outputNamePattern = "{filename}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        assertThat(name).startsWith("ewm_")
        assertThat(name).isNotEqualTo(".jpg")
    }

    /**
     * Review pass 2026-09-29: fallback timestamp thô (millisecond) không đủ phân biệt nếu nhiều
     * ảnh trong CÙNG 1 batch đều rơi vào case base rỗng và xử lý xong trong cùng 1 millisecond
     * (ảnh nhỏ/thiết bị nhanh) — 2 ảnh khác nhau (`index` khác nhau) phải LUÔN ra tên khác nhau.
     */
    @Test
    fun generateOutputName_multipleImagesSameEmptyBase_neverCollide_evenAtSameTimestamp() {
        val emptyNameUri = Uri.parse("content://media/external/images/media/.jpg")
        val info = ImageInfo(emptyNameUri)
        val name0 = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 0,
            outputNamePattern = "{filename}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        val name1 = exportNaming.generateOutputName(
            context.contentResolver,
            info,
            index = 1,
            outputNamePattern = "{filename}",
            outputFormat = Bitmap.CompressFormat.JPEG
        )
        assertThat(name0).isNotEqualTo(name1)
        assertThat(name0).endsWith("_1.jpg")
        assertThat(name1).endsWith("_2.jpg")
    }

    @Test
    fun resolveVersionedName_allVersionsTaken_fallbackToUUID() {
        // Edge case: all 999 versions taken (ultra-rare: ~1 per 1 billion batches)
        // fallback should use UUID to guarantee no collision
        val baseName = "output.jpg"
        val allVersionsTaken = mutableSetOf<String>()
        allVersionsTaken.add("output.jpg")
        for (v in 2..999) {
            allVersionsTaken.add("output_v$v.jpg")
        }

        val resolved = exportNaming.resolveVersionedName(baseName) { allVersionsTaken.contains(it) }

        // Must not be any of the 999 versions
        assertThat(resolved).isNotEqualTo("output.jpg")
        for (v in 2..999) {
            assertThat(resolved).isNotEqualTo("output_v$v.jpg")
        }
        // Fallback should use UUID pattern (_u{8chars})
        assertThat(resolved).matches(Regex("^output_u[a-f0-9]{8}\\.jpg$"))
    }
}
