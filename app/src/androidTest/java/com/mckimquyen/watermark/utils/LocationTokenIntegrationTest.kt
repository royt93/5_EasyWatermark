package com.mckimquyen.watermark.utils

import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.utils.bitmap.decodeBitmapFromUri
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * IDEA-16 integration test trên thiết bị thật: JPEG có EXIF GPS Hà Nội → [decodeBitmapFromUri] đọc
 * đúng toạ độ → [ExportNaming] + Geocoder THẬT (Robolectric không có Geocoder) ra tên địa danh không rỗng.
 * Geocoder cần mạng + Google Play services — thiếu thì skip bằng assume, không fail.
 */
@RunWith(AndroidJUnit4::class)
class LocationTokenIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun gpsJpeg(): Uri {
        val file = File(context.cacheDir, "idea16_gps.jpg")
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Canon")
            setLatLong(HANOI_LAT, HANOI_LON)
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    @Test
    fun decode_readsGpsCoordinates_fromRealExif() = runBlocking {
        val result = decodeBitmapFromUri(context, context.contentResolver, gpsJpeg())

        val exif = result.data?.exifModel
        assertThat(exif?.latitude).isWithin(GPS_TOLERANCE).of(HANOI_LAT)
        assertThat(exif?.longitude).isWithin(GPS_TOLERANCE).of(HANOI_LON)
        result.data?.bitmap?.recycle()
        Unit
    }

    @Test
    fun locationToken_realGeocoder_resolvesNonEmptyPlaceName() = runBlocking {
        assumeTrue("Thiết bị không có Geocoder backend", Geocoder.isPresent())
        val uri = gpsJpeg()
        val decoded = decodeBitmapFromUri(context, context.contentResolver, uri)
        val info = ImageInfo(uri, exifModel = decoded.data?.exifModel)
        decoded.data?.bitmap?.recycle()

        val text = ExportNaming(LocationNameResolver(context)).resolveTextTokens("{location}", info, context.contentResolver, 0)

        assumeTrue("Geocoder không trả kết quả (offline?)", text.isNotEmpty())
        assertThat(text).doesNotContain("{location}")
        assertThat(text.lowercase()).containsMatch("hà nội|ha noi|hanoi|hoàn kiếm|hoan kiem|viet ?nam|việt nam")
    }

    private companion object {
        const val SIZE = 64
        const val JPEG_QUALITY = 90
        const val HANOI_LAT = 21.0285
        const val HANOI_LON = 105.8542
        const val GPS_TOLERANCE = 1e-4
    }
}
