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
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-26: Widget test cho [DualPresetPickerBSDFragment] và nút Dual Presets trên [LayerManagerBSDFragment].
 */
@RunWith(RobolectricTestRunner::class)
class DualPresetPickerBSDFragmentWidgetTest {

    companion object {
        lateinit var testViewModel: MainViewModel
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

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    private fun launchPicker(): DualPresetPickerBSDFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        // Kích hoạt LiveData bằng cách observe nó trong lifecycle của Activity
        testViewModel.waterMark.observe(activity) {}
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = DualPresetPickerBSDFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, DualPresetPickerBSDFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun pickerDisplaysAllPresets() {
        val fragment = launchPicker()
        val adapter = fragment.binding.rvDualPresets.adapter
        assertThat(adapter).isNotNull()
        assertThat(adapter?.itemCount).isEqualTo(DualWatermarkPreset.entries.size)
    }

    @Test
    fun selectPreset_appliesToViewModel() {
        val fragment = launchPicker()
        fragment.binding.rvDualPresets.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        fragment.binding.rvDualPresets.layout(0, 0, 500, 1000)

        val holder = fragment.binding.rvDualPresets.findViewHolderForAdapterPosition(0)
        assertThat(holder).isNotNull()

        holder?.itemView?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val mark = testViewModel.waterMark.value
        assertThat(mark).isNotNull()
        assertThat(mark?.anchor).isEqualTo(DualWatermarkPreset.BRAND_COPYRIGHT.primaryAnchor.ordinal)
        assertThat(mark?.extraLayers).hasSize(1)
        assertThat(mark?.extraLayers?.get(0)?.anchor).isEqualTo(DualWatermarkPreset.BRAND_COPYRIGHT.secondaryAnchor.ordinal)
    }
}
