package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.utils.LocationNameResolver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** IDEA-16 AC: ảnh có EXIF GPS + token {location} → tên địa danh; không GPS → rỗng; không dùng token → không geocode. */
@RunWith(RobolectricTestRunner::class)
class ExportNamingLocationTest {

    private val contentResolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    private var lookups = 0
    private val naming = ExportNaming(LocationNameResolver { _, _ -> lookups++; "Hà Nội, Việt Nam" })
    private val gpsImage = ImageInfo(
        Uri.parse("content://media/gps.jpg"),
        exifModel = ExifModel(make = "Canon", latitude = 21.0285, longitude = 105.8542)
    )

    @Test
    fun locationToken_withGps_resolvesPlaceName() {
        assertThat(naming.resolveTextTokens("📍 {location}", gpsImage, contentResolver, 0)).isEqualTo("📍 Hà Nội, Việt Nam")
    }

    @Test
    fun locationToken_withoutGps_resolvesEmpty_noLookup() {
        val noGps = ImageInfo(Uri.parse("content://media/nogps.jpg"), exifModel = ExifModel(make = "Canon"))

        assertThat(naming.resolveTextTokens("[{location}]", noGps, contentResolver, 0)).isEqualTo("[]")
        assertThat(naming.resolveTextTokens("[{location}]", ImageInfo(Uri.parse("content://media/x.jpg")), contentResolver, 0))
            .isEqualTo("[]")
        assertThat(lookups).isEqualTo(0)
    }

    @Test
    fun textWithoutLocationToken_neverGeocodes() {
        naming.resolveTextTokens("{make} #{seq}", gpsImage, contentResolver, 0)

        assertThat(lookups).isEqualTo(0)
    }

    @Test
    fun qrContent_supportsLocationToken() {
        val content = naming.resolveQrContent("{location}|{portfolio_link}", gpsImage, contentResolver, 0, "site")

        assertThat(content).isEqualTo("Hà Nội, Việt Nam|site")
    }

    @Test
    fun defaultConstructor_locationAlwaysEmpty() {
        assertThat(ExportNaming().resolveTextTokens("{location}", gpsImage, contentResolver, 0)).isEmpty()
    }
}
