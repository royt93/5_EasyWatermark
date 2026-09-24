package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-07: [WaterMarkRepository.updateQrDynamicConfig] phải persist đủ 3 field mới
 * (`qrDynamicEnabled`/`qrContentTemplate`/`qrPortfolioLink`) + set `markMode=Image` + `iconUri`
 * đúng như [WaterMarkRepository.updateIcon] — và [WaterMarkRepository.updateIcon] (pick logo/icon
 * thường, không qua QR) phải tắt lại `qrDynamicEnabled`, tránh rò rỉ trạng thái QR động sang icon
 * khác (xem comment ở hàm gốc). Dùng DataStore cô lập — cùng pattern [WaterMarkRepositoryRecentIconsRoboTest].
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryQrDynamicRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun uri(name: String) = Uri.parse("content://media/$name")

    @Test
    fun freshRepo_qrDynamicDisabledByDefault() = runBlocking {
        val waterMark = repo.waterMark.first()
        assertThat(waterMark.qrDynamicEnabled).isFalse()
        assertThat(waterMark.qrContentTemplate).isEmpty()
        assertThat(waterMark.qrPortfolioLink).isEmpty()
    }

    @Test
    fun updateQrDynamicConfig_persistsAllThreeFieldsAndIconMode() = runBlocking {
        repo.updateQrDynamicConfig(uri("qr_preview"), "{hash}|{date}|{portfolio_link}", "https://me.example")

        val waterMark = repo.waterMark.first()
        assertThat(waterMark.markMode).isEqualTo(WaterMarkRepository.MarkMode.Image)
        assertThat(waterMark.iconUri).isEqualTo(uri("qr_preview"))
        assertThat(waterMark.qrDynamicEnabled).isTrue()
        assertThat(waterMark.qrContentTemplate).isEqualTo("{hash}|{date}|{portfolio_link}")
        assertThat(waterMark.qrPortfolioLink).isEqualTo("https://me.example")
    }

    @Test
    fun updateIcon_afterQrDynamicConfig_turnsOffDynamicFlag() = runBlocking {
        repo.updateQrDynamicConfig(uri("qr_preview"), "{hash}", "https://me.example")
        assertThat(repo.waterMark.first().qrDynamicEnabled).isTrue()

        repo.updateIcon(uri("normal_logo"))

        val waterMark = repo.waterMark.first()
        assertThat(waterMark.qrDynamicEnabled).isFalse()
        assertThat(waterMark.iconUri).isEqualTo(uri("normal_logo"))
    }
}
