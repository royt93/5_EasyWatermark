package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** FEAT-28: widget test cho [CardFramePbFragment] — ẩn/hiện nhóm tuỳ chỉnh, ghi vào repo, đổ giá trị đã lưu lên UI. */
@RunWith(RobolectricTestRunner::class)
class CardFramePbFragmentWidgetTest {

    companion object {
        lateinit var testViewModel: MainViewModel
        const val WAIT_TIMEOUT_MS = 5_000L
        const val WAIT_STEP_MS = 20L
        const val PERCENT_BASE = 100f
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        repo = WaterMarkRepository(context, waterMarkDataStore)
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = repo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(WAIT_STEP_MS)
        }
    }

    private fun launch(): CardFramePbFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        testViewModel.waterMark.observe(activity) {}
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = CardFramePbFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "t").commit()
        idleUntil { testViewModel.waterMark.value != null }
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun default_switchOff_andCustomizeGroupHidden() {
        val f = launch()
        assertThat(f.binding.swCardFrame.isChecked).isFalse()
        assertThat(f.binding.groupCardCustomize.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun savedEnabledConfig_showsGroup_andFillsSlidersFromRepo() {
        runBlocking {
            repo.updateCardFrameEnabled(true)
            repo.updateCardCornerRadiusPercent(0.2f)
            repo.updateCardShadowPercent(0.05f)
        }
        val f = launch()
        idleUntil { f.binding.swCardFrame.isChecked }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(f.binding.swCardFrame.isChecked).isTrue()
        assertThat(f.binding.groupCardCustomize.visibility).isEqualTo(View.VISIBLE)
        assertThat(f.binding.slideCardCorner.value).isWithin(0.01f).of(0.2f * PERCENT_BASE)
        assertThat(f.binding.slideCardShadow.value).isWithin(0.01f).of(0.05f * PERCENT_BASE)
        assertThat(f.binding.tvCardCornerValue.text.toString()).isEqualTo("20%")
        assertThat(f.binding.tvCardShadowValue.text.toString()).isEqualTo("5%")
    }

    @Test
    fun pressingSwitch_enablesCardFrameInRepo() {
        val f = launch()
        f.binding.swCardFrame.isPressed = true
        f.binding.swCardFrame.isChecked = true
        idleUntil { runBlocking { repo.waterMark.first().cardFrameEnabled } }
        assertThat(runBlocking { repo.waterMark.first().cardFrameEnabled }).isTrue()
    }

    @Test
    fun programmaticChecked_doesNotWriteRepo() {
        val f = launch()
        // không "pressed" → chỉ là đồng bộ UI theo repo, không được ghi ngược (tránh vòng lặp).
        f.binding.swCardFrame.isChecked = true
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(WAIT_STEP_MS * 5)
        assertThat(runBlocking { repo.waterMark.first().cardFrameEnabled }).isFalse()
    }

    @Test
    fun valueLabels_andSliderBounds_matchRepositoryLimits() {
        val f = launch()
        assertThat(f.binding.slideCardCorner.valueTo).isEqualTo(WaterMarkRepository.MAX_CARD_CORNER_PERCENT * PERCENT_BASE)
        assertThat(f.binding.slideCardShadow.valueTo).isEqualTo(WaterMarkRepository.MAX_CARD_SHADOW_PERCENT * PERCENT_BASE)
        assertThat(f.binding.slideCardCorner.valueFrom).isEqualTo(0f)
        assertThat(f.binding.slideCardShadow.valueFrom).isEqualTo(0f)
    }

    @Test
    fun a11y_controlsHaveContentDescriptions() {
        val f = launch()
        assertThat(f.binding.swCardFrame.contentDescription).isNotNull()
        assertThat(f.binding.slideCardCorner.contentDescription).isNotNull()
        assertThat(f.binding.slideCardShadow.contentDescription).isNotNull()
        assertThat(f.binding.flCardBackground.contentDescription).isNotNull()
    }
}
