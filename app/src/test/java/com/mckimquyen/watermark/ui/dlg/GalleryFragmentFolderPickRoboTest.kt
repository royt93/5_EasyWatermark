package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-08: nút "Choose folder" (`ivPickFolder`) trong `GalleryFragment` phải mở
 * `ACTION_OPEN_DOCUMENT_TREE` (SAF) — dùng lại pattern `TestHostActivity` từ
 * [GalleryFragmentPhotoPickerRoboTest]/BUG-10 để launch `GalleryFragment` không phụ thuộc Hilt.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryFragmentFolderPickRoboTest {

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

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @Test
    fun `choose folder menu item launches ACTION_OPEN_DOCUMENT_TREE`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        val handled = fragment.requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.topAppBar
        ).menu.performIdentifierAction(R.id.ivPickFolder, 0)
        assertThat(handled).isTrue()

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
            ?: shadowOf(activity).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    /** Nút multi-pick từng ảnh (`ivSysImage`) vẫn phải hoạt động song song — AC thứ 2 của ticket. */
    @Test
    fun `existing multi-pick menu item still present alongside folder pick`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        val menu = fragment.requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.topAppBar
        ).menu

        assertThat(menu.findItem(R.id.ivSysImage)).isNotNull()
        assertThat(menu.findItem(R.id.ivPickFolder)).isNotNull()
    }
}
