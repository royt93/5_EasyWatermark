package com.mckimquyen.watermark.data.model

import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ENH-08: [ImageInfo] là immutable data class — toàn bộ thay đổi qua `copy()`, không mutate
 * trực tiếp. Kiểm chứng equals()/hashCode() so nội dung (data class), chuyển đổi TileMode
 * từ ordinal, so sánh giữa 2 instance qua isSameItem(), factory method empty().
 */
@RunWith(RobolectricTestRunner::class)
class ImageInfoTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun testFileUri(fileName: String = "test.jpg"): Uri {
        val file = File(context.cacheDir, fileName)
        file.writeText("dummy")
        return Uri.fromFile(file)
    }

    @Test
    fun imageInfo_defaultValues_matchDocumentation() {
        val uri = testFileUri()
        val imageInfo = ImageInfo(uri)

        assertThat(imageInfo.width).isEqualTo(1)
        assertThat(imageInfo.height).isEqualTo(1)
        assertThat(imageInfo.inSample).isEqualTo(1)
        assertThat(imageInfo.scaleX).isEqualTo(1f)
        assertThat(imageInfo.scaleY).isEqualTo(1f)
        assertThat(imageInfo.result).isNull()
        assertThat(imageInfo.jobState).isEqualTo(JobState.Ready)
        assertThat(imageInfo.isInDelModel).isFalse()
        assertThat(imageInfo.tileMode).isEqualTo(Shader.TileMode.REPEAT.ordinal)
        assertThat(imageInfo.offsetX).isEqualTo(0.5f)
        assertThat(imageInfo.offsetY).isEqualTo(0.5f)
        assertThat(imageInfo.exifModel).isNull()
        assertThat(imageInfo.caption).isNull()
        assertThat(imageInfo.isSkippedInExport).isFalse()
        assertThat(imageInfo.cropRect).isNull()
        assertThat(imageInfo.rotationDegrees).isEqualTo(0f)
        assertThat(imageInfo.detectedFaceRectsNormalized).isNull()
        assertThat(imageInfo.redactionRectsNormalized).isNull()
    }

    @Test
    fun obtainTileMode_repeat_returnsRepeatOrdinal() {
        val uri = testFileUri()
        val imageInfo = ImageInfo(uri, tileMode = Shader.TileMode.REPEAT.ordinal)

        val tileMode = imageInfo.obtainTileMode()

        assertThat(tileMode).isEqualTo(Shader.TileMode.REPEAT)
    }

    @Test
    fun obtainTileMode_clamp_returnsClampOrdinal() {
        val uri = testFileUri()
        val imageInfo = ImageInfo(uri, tileMode = Shader.TileMode.CLAMP.ordinal)

        val tileMode = imageInfo.obtainTileMode()

        assertThat(tileMode).isEqualTo(Shader.TileMode.CLAMP)
    }

    @Test
    fun obtainTileMode_mirror_returnsMirrorOrdinal() {
        val uri = testFileUri()
        val imageInfo = ImageInfo(uri, tileMode = Shader.TileMode.MIRROR.ordinal)

        val tileMode = imageInfo.obtainTileMode()

        assertThat(tileMode).isEqualTo(Shader.TileMode.MIRROR)
    }

    @Test
    fun isSameItem_sameUriAndState_returnsTrue() {
        val uri = testFileUri()
        val result = Result.success(data = uri, code = "OK")
        val imageInfo1 = ImageInfo(uri, result = result, jobState = JobState.Success(result))
        val imageInfo2 = ImageInfo(uri, result = result, jobState = JobState.Success(result))

        val isSame = imageInfo1.isSameItem(imageInfo2)

        assertThat(isSame).isTrue()
    }

    @Test
    fun isSameItem_differentUri_returnsFalse() {
        val uri1 = testFileUri("photo1.jpg")
        val uri2 = testFileUri("photo2.jpg")
        val imageInfo1 = ImageInfo(uri1)
        val imageInfo2 = ImageInfo(uri2)

        val isSame = imageInfo1.isSameItem(imageInfo2)

        assertThat(isSame).isFalse()
    }

    @Test
    fun isSameItem_differentJobState_returnsFalse() {
        val uri = testFileUri()
        val imageInfo1 = ImageInfo(uri, jobState = JobState.Ready)
        val imageInfo2 = ImageInfo(uri, jobState = JobState.Failure(Result.failure<Any>(code = "ERR")))

        val isSame = imageInfo1.isSameItem(imageInfo2)

        assertThat(isSame).isFalse()
    }

    @Test
    fun isSameItem_differentIsInDelModel_returnsFalse() {
        val uri = testFileUri()
        val imageInfo1 = ImageInfo(uri, isInDelModel = false)
        val imageInfo2 = ImageInfo(uri, isInDelModel = true)

        val isSame = imageInfo1.isSameItem(imageInfo2)

        assertThat(isSame).isFalse()
    }

    @Test
    fun isSameItem_differentOtherFields_returnsTrue() {
        // isSameItem() chỉ so sánh uri, result, jobState, isInDelModel
        // Khác nhau ở width, caption, cropRect, ... vẫn coi là "same item"
        val uri = testFileUri()
        val imageInfo1 = ImageInfo(uri, width = 100, caption = "Original")
        val imageInfo2 = ImageInfo(uri, width = 200, caption = "Different")

        val isSame = imageInfo1.isSameItem(imageInfo2)

        assertThat(isSame).isTrue()
    }

    @Test
    fun empty_returnsInstanceWithEmptyUri() {
        val empty = ImageInfo.empty()

        assertThat(empty.uri).isEqualTo(Uri.EMPTY)
        assertThat(empty.width).isEqualTo(1)
        assertThat(empty.height).isEqualTo(1)
        assertThat(empty.jobState).isEqualTo(JobState.Ready)
    }

    @Test
    fun dataClass_copy_createsNewInstanceWithModifiedField() {
        val uri = testFileUri()
        val imageInfo = ImageInfo(uri, width = 100, height = 100)

        val copied = imageInfo.copy(width = 200)

        assertThat(copied.width).isEqualTo(200)
        assertThat(copied.height).isEqualTo(100)
        assertThat(copied.uri).isEqualTo(uri)
        assertThat(copied).isNotSameInstanceAs(imageInfo)
    }

    @Test
    fun dataClass_equals_comparesBothContentAndAllFields() {
        val uri1 = testFileUri("photo1.jpg")
        val uri2 = testFileUri("photo2.jpg")
        val imageInfo1 = ImageInfo(uri1, width = 100, height = 100)
        val imageInfo2 = ImageInfo(uri1, width = 100, height = 100)
        val imageInfo3 = ImageInfo(uri2, width = 100, height = 100)

        assertThat(imageInfo1).isEqualTo(imageInfo2)
        assertThat(imageInfo1).isNotEqualTo(imageInfo3)
    }

    @Test
    fun shareUri_fromSuccessResult_returnsUriFromData() {
        val uri = testFileUri()
        val result = Result.success(data = uri, code = "OK")
        val imageInfo = ImageInfo(testFileUri("input.jpg"), result = result)

        val shareUri = imageInfo.shareUri

        assertThat(shareUri).isEqualTo(uri)
    }

    @Test
    fun shareUri_failureResult_returnsNull() {
        val result = Result.failure<Uri>(code = "ERR", message = "Failed")
        val imageInfo = ImageInfo(testFileUri(), result = result)

        val shareUri = imageInfo.shareUri

        assertThat(shareUri).isNull()
    }

    @Test
    fun shareUri_noResult_returnsNull() {
        val imageInfo = ImageInfo(testFileUri(), result = null)

        val shareUri = imageInfo.shareUri

        assertThat(shareUri).isNull()
    }
}
