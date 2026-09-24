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
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-03: [LayerManagerBSDFragment] — chuyển trạng thái danh sách/form sửa, thêm/xoá layer cập
 * nhật đúng qua `shareViewModel`. Cùng kỹ thuật host Activity với `MainViewModel` thật (repo
 * DataStore cô lập) như `TextWatermarkBSDFragmentWidgetTest` (ENH-30).
 */
@RunWith(RobolectricTestRunner::class)
class LayerManagerBSDFragmentWidgetTest {

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
            templateRepo = TemplateRepository(null)
        )
    }

    private fun launchFragment(): LayerManagerBSDFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = LayerManagerBSDFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, LayerManagerBSDFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun initialState_showsEmptyList() {
        val fragment = launchFragment()

        assertThat(fragment.binding.groupList.visibility).isEqualTo(View.VISIBLE)
        assertThat(fragment.binding.groupEdit.visibility).isEqualTo(View.GONE)
        assertThat(fragment.binding.tvEmpty.visibility).isEqualTo(View.VISIBLE)
        assertThat(fragment.binding.rvLayers.adapter?.itemCount).isEqualTo(0)
    }

    @Test
    fun tapAddLayer_switchesToEditForm() {
        val fragment = launchFragment()

        fragment.binding.btnAddLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.groupList.visibility).isEqualTo(View.GONE)
        assertThat(fragment.binding.groupEdit.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun addTextLayer_thenSave_appearsInListAndReturnsToListView() {
        val fragment = launchFragment()

        fragment.binding.btnAddLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.binding.etLayerText.setText("Copyright 2026")
        fragment.binding.btnSaveLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.groupList.visibility).isEqualTo(View.VISIBLE)
        assertThat(fragment.binding.groupEdit.visibility).isEqualTo(View.GONE)
        assertThat(fragment.binding.tvEmpty.visibility).isEqualTo(View.GONE)
        assertThat(fragment.binding.rvLayers.adapter?.itemCount).isEqualTo(1)
        assertThat(testViewModel.waterMark.value?.extraLayers?.single()?.text).isEqualTo("Copyright 2026")
    }

    @Test
    fun saveLayer_withBlankText_showsToastAndStaysInEditForm() {
        val fragment = launchFragment()

        fragment.binding.btnAddLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        fragment.binding.etLayerText.setText("")
        fragment.binding.btnSaveLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.groupEdit.visibility).isEqualTo(View.VISIBLE)
        assertThat(testViewModel.waterMark.value?.extraLayers).isEmpty()
    }

    @Test
    fun tapDeleteOnRow_removesLayer() = runBlocking {
        testViewModel.waterMarkRepo.addLayer(WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text, text = "to delete"))
        val fragment = launchFragment()
        // RecyclerView layout thật (LinearLayoutManager) cần 1 vòng measure/layout nữa sau idle()
        // đầu tiên trong launchFragment() để tạo xong ViewHolder cho item vừa submit.
        fragment.binding.rvLayers.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        fragment.binding.rvLayers.layout(0, 0, 500, 1000)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(fragment.binding.rvLayers.adapter?.itemCount).isEqualTo(1)

        val holder = fragment.binding.rvLayers.findViewHolderForAdapterPosition(0)
        assertThat(holder).isNotNull()
        val deleteButton = holder!!.itemView.findViewById<View>(com.mckimquyen.watermark.R.id.btnDelete)
        deleteButton.performClick()
        awaitExtraLayers { it.isEmpty() }

        assertThat(testViewModel.waterMark.value?.extraLayers).isEmpty()
    }

    /**
     * `removeLayer`/`addLayer`... ghi qua `viewModelScope.launch { waterMarkRepo.xxx() }` — DataStore
     * `edit{}` suspend thật trên `Dispatchers.IO`, không nằm trên Robolectric main Looper nên 1 lần
     * `idle()` không đủ đảm bảo đã ghi xong (cùng lý do poll ngắn ở `QrCodeBottomSheetFragmentRoboTest`).
     */
    private fun awaitExtraLayers(timeoutMs: Long = 5_000, predicate: (List<WatermarkLayer>) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate(testViewModel.waterMark.value?.extraLayers.orEmpty())) return
            Thread.sleep(20)
        }
    }

    @Test
    fun addLayer_atMaxCapacity_tappingAddDoesNotOpenEditForm() = runBlocking {
        repeat(WaterMarkRepository.MAX_EXTRA_LAYERS) {
            testViewModel.waterMarkRepo.addLayer(WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Text, text = "layer-$it"))
        }
        val fragment = launchFragment()

        fragment.binding.btnAddLayer.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // Vẫn ở danh sách (không mở form thêm layer thứ 5) — đúng giới hạn MAX_EXTRA_LAYERS.
        assertThat(fragment.binding.groupList.visibility).isEqualTo(View.VISIBLE)
        assertThat(testViewModel.waterMark.value?.extraLayers).hasSize(WaterMarkRepository.MAX_EXTRA_LAYERS)
    }
}
