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
 * FEAT-08: nút "Choose folder" (`ivPickFolder`) trong `GalleryFragment` phải mở
 * `ACTION_OPEN_DOCUMENT_TREE` (SAF) — dùng lại pattern `TestHostActivity` từ
 * [GalleryFragmentPhotoPickerRoboTest]/BUG-10 để launch `GalleryFragment` không phụ thuộc Hilt.
 *
 * ENH-33: click `ivPickFolder` giờ mở dialog hỏi "Include subfolders" trước — chỉ launch
 * `ACTION_OPEN_DOCUMENT_TREE` sau khi user bấm nút xác nhận (`tips_confirm_dialog`) trong dialog.
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
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @Test
    fun `choose folder menu item shows dialog then launches ACTION_OPEN_DOCUMENT_TREE on confirm`() {
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
        shadowOf(Looper.getMainLooper()).idle()

        // ENH-33: dialog "Include subfolders" hiện ra trước, SAF picker CHƯA launch ngay.
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertThat(dialog).isNotNull()
        assertThat(shadowOf(activity).nextStartedActivityForResult).isNull()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        val switch = findMaterialSwitch(dialog!!)
        assertThat(switch).isNotNull()
        assertThat(switch!!.isChecked).isFalse()

        // Bấm nút xác nhận (mặc định KHÔNG bật subfolder) — mới launch SAF picker.
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
            ?: shadowOf(activity).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    /** ENH-33: bật switch trong dialog trước khi confirm phải phản ánh đúng vào [GalleryFragment.pendingIncludeSubfolders]. */
    @Test
    fun `toggling include-subfolders switch before confirm sets pendingIncludeSubfolders`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        fragment.requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
            .menu.performIdentifierAction(R.id.ivPickFolder, 0)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val switch = findMaterialSwitch(dialog)!!
        switch.isChecked = true
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.pendingIncludeSubfolders).isTrue()
    }

    /** ENH-33: bấm Huỷ trong dialog KHÔNG được launch SAF picker. */
    @Test
    fun `cancelling include-subfolders dialog does not launch SAF picker`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        fragment.requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
            .menu.performIdentifierAction(R.id.ivPickFolder, 0)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(shadowOf(activity).nextStartedActivityForResult).isNull()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    /** [dialog]'s view tree chỉ có 1 MaterialSwitch — duyệt đệ quy tìm thay vì cần fix id. */
    private fun findMaterialSwitch(dialog: androidx.appcompat.app.AlertDialog): com.google.android.material.materialswitch.MaterialSwitch? {
        fun search(view: android.view.View): com.google.android.material.materialswitch.MaterialSwitch? {
            if (view is com.google.android.material.materialswitch.MaterialSwitch) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    search(view.getChildAt(i))?.let { return it }
                }
            }
            return null
        }
        return search(dialog.window!!.decorView)
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

    /**
     * BUG-35: handleTreeUriResult với null Uri không thực hiện hành động nào và không crash.
     */
    @Test
    fun `handleTreeUriResult with null does not crash`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        // Null treeUri
        fragment.handleTreeUriResult(null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * BUG-35: Khi takePersistableUriPermission ném SecurityException,
     * fragment bắt lỗi qua runCatching, không làm crash app và vẫn xử lý flow.
     */
    @Test
    fun `handleTreeUriResult catches SecurityException when persisting permission fails`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        // ContentResolver trên Robolectric sẽ ném SecurityException cho Uri này
        val dummyTreeUri = android.net.Uri.parse("content://unauthorized.provider/tree/test")
        fragment.handleTreeUriResult(dummyTreeUri)
        shadowOf(Looper.getMainLooper()).idle()
        // Không crash!
    }
}
