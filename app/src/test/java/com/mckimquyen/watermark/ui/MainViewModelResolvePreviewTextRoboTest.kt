package com.mckimquyen.watermark.ui

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit test (Robolectric): [MainViewModel.resolvePreviewText] — token động ({filename}/{seq}/
 * {date}/{exif}...) phải render giá trị thật trong preview editor, không chỉ lúc export
 * (feat.md #4, phần "còn lại chưa làm": preview hiện token nguyên văn).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelResolvePreviewTextRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    private fun imageInfo(uri: Uri, exif: ExifModel? = null) = ImageInfo(uri).apply { exifModel = exif }

    @Test
    fun noBraceToken_returnsTextUnchanged() {
        val info = imageInfo(Uri.parse("content://media/1"))

        assertThat(viewModel.resolvePreviewText("plain text, no token", info))
            .isEqualTo("plain text, no token")
    }

    @Test
    fun filenameToken_fallsBackToUriLastSegment_whenNoContentProvider() {
        val info = imageInfo(Uri.parse("content://media/external/images/media/IMG_042.jpg"))

        assertThat(viewModel.resolvePreviewText("{filename}", info)).isEqualTo("IMG_042")
    }

    @Test
    fun seqToken_usesRealPositionInBatchList() {
        val infoA = imageInfo(Uri.parse("content://media/A"))
        val infoB = imageInfo(Uri.parse("content://media/B"))
        runBlocking { waterMarkRepo.updateImageList(listOf(infoA, infoB)) }

        assertThat(viewModel.resolvePreviewText("#{seq}", infoB)).isEqualTo("#2")
    }

    @Test
    fun seqToken_imageNotInList_fallsBackToOne() {
        val info = imageInfo(Uri.parse("content://media/orphan"))

        assertThat(viewModel.resolvePreviewText("#{seq}", info)).isEqualTo("#1")
    }

    @Test
    fun exifTokens_resolveFromImageInfoExifModel() {
        val exif = ExifModel(make = "Canon", model = "EOS R5", iso = "100")
        val info = imageInfo(Uri.parse("content://media/1"), exif)

        assertThat(viewModel.resolvePreviewText("{make} {model} ISO{iso}", info))
            .isEqualTo("Canon EOS R5 ISO100")
    }

    @Test
    fun dateToken_fallsBackToToday_whenExifDateTimeBlank() {
        val info = imageInfo(Uri.parse("content://media/1"), ExifModel())

        val resolved = viewModel.resolvePreviewText("{date}", info)

        assertThat(resolved).isNotEqualTo("{date}")
        assertThat(resolved).matches("""\d{4}-\d{2}-\d{2}""")
    }

    @Test
    fun switchingImage_reResolvesFilenameForNewImage() {
        val infoA = imageInfo(Uri.parse("content://media/external/images/media/first.jpg"))
        val infoB = imageInfo(Uri.parse("content://media/external/images/media/second.jpg"))

        assertThat(viewModel.resolvePreviewText("{filename}", infoA)).isEqualTo("first")
        assertThat(viewModel.resolvePreviewText("{filename}", infoB)).isEqualTo("second")
    }
}
