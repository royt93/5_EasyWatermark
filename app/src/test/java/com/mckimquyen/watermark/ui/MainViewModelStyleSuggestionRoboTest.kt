package com.mckimquyen.watermark.ui

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkStyleHistoryRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-12: [MainViewModel.styleSuggestionFlow]/[MainViewModel.applySuggestedStyle]/
 * [MainViewModel.dismissStyleSuggestion] — logic đếm tần suất/ngưỡng thật đã test kỹ ở
 * `WatermarkStyleCoachTest`, ở đây chỉ verify đường dây ViewModel → styleHistoryRepo → LiveData/
 * StateFlow → waterMarkRepo.applyWaterMark() đúng (mirror `MainViewModelExifFrameSuggestionRoboTest`).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelStyleSuggestionRoboTest {

    private class FakeStyleHistoryDao : WatermarkStyleHistoryDao {
        var recentEntities: List<WatermarkStyleHistoryEntity> = emptyList()
        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long = 0
        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> = recentEntities.take(n)
        override suspend fun pruneKeepLatest(keep: Int) = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var fakeDao: FakeStyleHistoryDao

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        fakeDao = FakeStyleHistoryDao()
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(fakeDao)
        )
        viewModel.waterMark.observeForever {}
        viewModel.imageList.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 6/10 — đúng ngưỡng 60%, KHÁC cấu hình mặc định đang áp dụng (textColor/anchor/alpha default khác 0xAABBCC/3/222). */
    private fun majoritySignature() = WatermarkStyleHistoryEntity(
        id = 0,
        timestamp = 0L,
        textColor = 0xAABBCC,
        textStyleKey = TextPaintStyle.Fill.serializeKey(),
        textTypefaceKey = TextTypeface.Bold.serializeKey(),
        alpha = 222,
        anchor = Anchor.TOP_LEFT.ordinal,
        markModeValue = WaterMarkRepository.MarkMode.Text.value,
        exifFrameStyle = ExifFrameStyle.MINIMAL.ordinal,
        textEffectStroke = true,
        textEffectShadow = false,
        textEffectPillBackground = false
    )

    private fun seedHistory(majority: WatermarkStyleHistoryEntity, majorityCount: Int, total: Int = 10) {
        val minority = majority.copy(textColor = majority.textColor + 1)
        fakeDao.recentEntities = (1..majorityCount).map { majority.copy(id = it.toLong(), timestamp = it.toLong()) } +
            (majorityCount + 1..total).map { minority.copy(id = it.toLong(), timestamp = it.toLong()) }
    }

    private fun awaitTrue(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        error("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun updateImageList_fewerThan10HistoryRows_noSuggestion() {
        seedHistory(majoritySignature(), majorityCount = 6, total = 9) // chỉ 9 dòng, chưa đủ MIN_SAMPLES

        viewModel.updateImageList(listOf(Uri.parse("content://media/1.jpg")))
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.styleSuggestionFlow.value).isNull()
    }

    @Test
    fun updateImageList_60PercentMajority_differsFromCurrent_showsSuggestion() {
        seedHistory(majoritySignature(), majorityCount = 6, total = 10)

        viewModel.updateImageList(listOf(Uri.parse("content://media/1.jpg")))

        awaitTrue { viewModel.styleSuggestionFlow.value != null }
        val suggestion = viewModel.styleSuggestionFlow.value!!
        assertThat(suggestion.textColor).isEqualTo(0xAABBCC)
        assertThat(suggestion.anchor).isEqualTo(Anchor.TOP_LEFT.ordinal)
    }

    @Test
    fun updateImageList_majoritySameAsCurrentConfig_noSuggestion() = runBlocking {
        val majority = majoritySignature()
        // Áp majority thành cấu hình ĐANG DÙNG trước — suggestion trùng hiện tại thì không nên hiện.
        val current = waterMarkRepo.waterMark.first()
        waterMarkRepo.applyWaterMark(
            current.copy(
                textColor = majority.textColor,
                textStyle = TextPaintStyle.obtainSealedClass(majority.textStyleKey),
                textTypeface = TextTypeface.obtainSealedClass(majority.textTypefaceKey),
                alpha = majority.alpha,
                anchor = majority.anchor,
                markMode = WaterMarkRepository.MarkMode.Text,
                exifFrameStyle = majority.exifFrameStyle,
                textEffectStroke = majority.textEffectStroke,
                textEffectShadow = majority.textEffectShadow,
                textEffectPillBackground = majority.textEffectPillBackground
            )
        )
        seedHistory(majority, majorityCount = 6, total = 10)

        viewModel.updateImageList(listOf(Uri.parse("content://media/1.jpg")))
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(80)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.styleSuggestionFlow.value).isNull()
        Unit
    }

    @Test
    fun applySuggestedStyle_updatesWaterMarkFields_keepsTextAndIconUri_thenClearsSuggestion() = runBlocking {
        val originalText = waterMarkRepo.waterMark.first().text
        seedHistory(majoritySignature(), majorityCount = 6, total = 10)
        viewModel.updateImageList(listOf(Uri.parse("content://media/1.jpg")))
        awaitTrue { viewModel.styleSuggestionFlow.value != null }

        viewModel.applySuggestedStyle()
        awaitTrue { viewModel.styleSuggestionFlow.value == null }

        val applied = waterMarkRepo.waterMark.first()
        assertThat(applied.textColor).isEqualTo(0xAABBCC)
        assertThat(applied.textStyle).isEqualTo(TextPaintStyle.Fill)
        assertThat(applied.textTypeface).isEqualTo(TextTypeface.Bold)
        assertThat(applied.alpha).isEqualTo(222)
        assertThat(applied.anchor).isEqualTo(Anchor.TOP_LEFT.ordinal)
        assertThat(applied.exifFrameStyle).isEqualTo(ExifFrameStyle.MINIMAL.ordinal)
        assertThat(applied.textEffectStroke).isTrue()
        // text/iconUri KHÔNG bị suggestion đè — giữ nguyên cấu hình hiện tại.
        assertThat(applied.text).isEqualTo(originalText)
        Unit
    }

    @Test
    fun dismissStyleSuggestion_hidesBanner_doesNotReappearWithoutNewBatch() {
        seedHistory(majoritySignature(), majorityCount = 6, total = 10)
        viewModel.updateImageList(listOf(Uri.parse("content://media/1.jpg")))
        awaitTrue { viewModel.styleSuggestionFlow.value != null }

        viewModel.dismissStyleSuggestion()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.styleSuggestionFlow.value).isNull()
        // Không có batch mới nào được nạp lại → refreshStyleSuggestion() không tự chạy lại, banner
        // không tự tái hiện dù data lịch sử gốc không đổi.
        Thread.sleep(80)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(viewModel.styleSuggestionFlow.value).isNull()
    }
}
