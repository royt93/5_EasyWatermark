package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** IDEA-13: BatchExportEngine xử lý caption riêng khi proofing. */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineCaptionRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun resolveBaseText_proofingMode_andCaptionSet_appendsSeq3ToCaption() = runBlocking {
        val repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        repo.updateText("Shared Text")
        val config = repo.waterMark.first()
        val engine = BatchExportEngine(context, ExportNaming())

        val infoWithCaption = ImageInfo(Uri.parse("content://media/1"), caption = "Client Caption")

        val text = engine.resolveBaseText(infoWithCaption, config, proofingMode = true)

        assertThat(text).isEqualTo("Client Caption  #{seq3}")
    }

    @Test
    fun resolveBaseText_notProofing_andCaptionSet_returnsRawCaption() = runBlocking {
        val repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        repo.updateText("Shared Text")
        val config = repo.waterMark.first()
        val engine = BatchExportEngine(context, ExportNaming())

        val infoWithCaption = ImageInfo(Uri.parse("content://media/1"), caption = "Client Caption")

        val text = engine.resolveBaseText(infoWithCaption, config, proofingMode = false)

        assertThat(text).isEqualTo("Client Caption")
    }

    @Test
    fun resolveBaseText_proofingMode_andNoCaption_returnsSharedTextWithoutAppending() = runBlocking {
        // Chỉ nối seq3 vào caption riêng (khi user cố tình gõ); watermark chung (được override bằng PROOF) đã tự định nghĩa sẵn mẫu có #{seq3}
        val repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        repo.updateText("Shared Text")
        val config = repo.waterMark.first()
        val engine = BatchExportEngine(context, ExportNaming())

        val infoNoCaption = ImageInfo(Uri.parse("content://media/1"), caption = null)

        val text = engine.resolveBaseText(infoNoCaption, config, proofingMode = true)

        assertThat(text).isEqualTo("Shared Text")
    }
}
