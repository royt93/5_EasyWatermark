package com.mckimquyen.watermark.ui

import android.graphics.Shader
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-26: smoke test thật trên TECNO KJ7 cho thấy áp preset khi ảnh đang ở tile REPEAT không
 * đặt được watermark vào 2 góc (REPEAT tile phủ kín, neo 9-grid chỉ có hiệu lực ở CLAMP).
 * Khoá hành vi: [MainViewModel.applyDualPreset] phải ép CLAMP cho ảnh đang chọn và phát
 * [UiState.ApplyAnchor] đúng neo/margin của layer chính.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelApplyDualPresetRoboTest {

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        const val WAIT_STEP_MS = 20L
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val imageUri = Uri.parse("content://media/external/images/media/1")
    private lateinit var repo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val waterMarkStore = newTestWaterMarkDataStore(context)
        val userStore = newTestUserDataStore(context)
        runBlocking {
            waterMarkStore.edit { it.clear() }
            userStore.edit { it.clear() }
        }
        repo = WaterMarkRepository(context, waterMarkStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userStore),
            waterMarkRepo = repo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
        // LiveData chỉ có .value khi đang có observer.
        viewModel.selectedImage.observeForever { }
        // BUG-68: uiStateFlow giờ là SharedFlow one-shot (không còn .value) — thu event bằng collector
        // chạy sẵn TRƯỚC khi gọi applyDualPreset, giống Fragment thật đang STARTED.
        collectJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
            viewModel.uiStateFlow.collect { emittedStates.add(it) }
        }
    }

    private val emittedStates = java.util.Collections.synchronizedList(mutableListOf<UiState>())
    private var collectJob: kotlinx.coroutines.Job? = null

    @org.junit.After
    fun tearDown() {
        collectJob?.cancel()
    }

    private fun anchorEvent(): UiState.ApplyAnchor? =
        synchronized(emittedStates) { emittedStates.filterIsInstance<UiState.ApplyAnchor>().lastOrNull() }

    private fun selectImage(tileMode: Shader.TileMode) = runBlocking {
        repo.updateImageList(listOf(ImageInfo(uri = imageUri, tileMode = tileMode.ordinal)))
        repo.select(imageUri)
        awaitUntil { viewModel.selectedImage.value?.uri == imageUri }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Timeout chờ điều kiện" }
            Thread.sleep(WAIT_STEP_MS)
        }
    }

    @Test
    fun applyDualPreset_fromRepeat_forcesClampOnSelectedImage() {
        selectImage(Shader.TileMode.REPEAT)

        viewModel.applyDualPreset(DualWatermarkPreset.BRAND_COPYRIGHT)
        awaitUntil { repo.selectedImage.value.tileMode == Shader.TileMode.CLAMP.ordinal }

        assertThat(repo.selectedImage.value.tileMode).isEqualTo(Shader.TileMode.CLAMP.ordinal)
        assertThat(repo.imageInfoList.single().tileMode).isEqualTo(Shader.TileMode.CLAMP.ordinal)
    }

    @Test
    fun applyDualPreset_emitsApplyAnchorWithPrimaryAnchorAndMargin() {
        selectImage(Shader.TileMode.REPEAT)
        val preset = DualWatermarkPreset.DIAGONAL_BALANCE

        viewModel.applyDualPreset(preset)
        awaitUntil { anchorEvent() != null }

        val state = anchorEvent()!!
        assertThat(state.anchor).isEqualTo(preset.primaryAnchor)
        assertThat(state.marginPercent).isEqualTo(preset.primaryMarginPercent)
    }

    @Test
    fun applyDualPreset_alreadyClamp_keepsClampAndStillAppliesLayers() {
        selectImage(Shader.TileMode.CLAMP)

        viewModel.applyDualPreset(DualWatermarkPreset.BRAND_COPYRIGHT)
        awaitUntil { anchorEvent() != null }

        assertThat(repo.selectedImage.value.tileMode).isEqualTo(Shader.TileMode.CLAMP.ordinal)
        assertThat(runBlocking { repo.waterMark.first() }.extraLayers).hasSize(1)
    }

    @Test
    fun applyDualPreset_withoutSelectedImage_doesNotCrash_andStillEmitsAnchor() {
        viewModel.applyDualPreset(DualWatermarkPreset.BRAND_COPYRIGHT)
        awaitUntil { anchorEvent() != null }

        assertThat(anchorEvent()).isInstanceOf(UiState.ApplyAnchor::class.java)
    }
}
