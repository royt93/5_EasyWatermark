package com.mckimquyen.watermark.ui.dlg

import android.os.Looper
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * IDEA-02 widget test: switch "Watermark vô hình" trong dialog lưu ảnh.
 * Theo khuôn `SaveImageBSDialogFragmentAuthenticityRoboTest` (IDEA-03).
 *
 * BUG-FLAKY-2026-09-30: [SaveImageBSDialogFragment] ép kiểu `requireActivity() as MainActivity`
 * (không host được bằng FragmentActivity trần) — MainActivity tự inject MainViewModel qua Hilt
 * thật ngay `create()`, nên phải dùng [HiltAndroidRule] + [TestDataStoreModule]
 * (`@TestInstallIn` thay [com.mckimquyen.watermark.di.DataStoreModule]) để mỗi test nhận DataStore
 * CÔ LẬP thay vì singleton `context.userDataStore` dùng chung xuyên JVM fork (xem
 * testutil/TestDataStores.kt — nguồn deadlock Mutex vĩnh viễn khi chạy full suite).
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentInvisibleRoboTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // BUG-FLAKY-2026-09-30: HiltTestApplication không implement Configuration.Provider (khác
    // MyApplication thật) — WorkManager không tự init được nữa (MainViewModel.
    // reattachExportWorkIfRunning() gọi WorkManager.getInstance() ngay onViewCreated). Inject
    // HiltWorkerFactory thật rồi tự init test WorkManager, theo đúng recipe đã dùng ở
    // MainViewModelSaveImageImmutabilityRoboTest.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private fun setupDialog(): Pair<MainActivity, SaveImageBSDialogFragment> {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // HiltTestApplication KHÔNG chạy MyApplication.onCreate() (CMonet.init) — tự gọi lại để
        // tránh UninitializedPropertyAccessException khi UI đọc CMonet.isDynamicColorAvailable().
        CMonet.init(context, true)
        val workConfig = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, workConfig)
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val dialogFragment = SaveImageBSDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(dialogFragment, "SaveImageBSDialogFragmentInvisibleTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun swInvisibleWatermark_isMaterialSwitch_andReflectsViewModelState() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swInvisibleWatermark
        assertThat(switch).isInstanceOf(MaterialSwitch::class.java)
        assertThat(switch.isChecked).isEqualTo(viewModel.invisibleWatermark)
        assertThat(switch.contentDescription.toString())
            .isEqualTo(activity.getString(R.string.invisible_watermark_title))
    }

    @Test
    fun swInvisibleWatermark_toggle_luuVaoViewModel() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swInvisibleWatermark
        val initialState = viewModel.invisibleWatermark

        switch.isPressed = true
        switch.isChecked = !initialState
        shadowOf(Looper.getMainLooper()).idle()

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.invisibleWatermark == initialState && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.invisibleWatermark).isEqualTo(!initialState)
    }

    /**
     * Integration test (BUG-FLAKY-2026-09-30): chứng minh [com.mckimquyen.watermark.di.TestDataStoreModule]
     * THẬT SỰ có hiệu lực — so bằng REFERENCE (không so giá trị, vì singleton sản xuất là state
     * toàn cục dùng chung, giá trị của nó phụ thuộc thứ tự chạy các test khác) giữa DataStore Hilt
     * đang bind cho test này với `context.userDataStore` (singleton sản xuất, cache theo file path
     * dùng chung xuyên JVM fork — xem testutil/TestDataStores.kt). Nếu module test bị gỡ nhầm/
     * không áp dụng, 2 instance sẽ TRÙNG NHAU và assertion dưới FAIL ngay, thay vì lộ ra bằng 1
     * lần treo hiếm khi chạy full suite.
     */
    @Test
    fun setupDialog_hiltBindsIsolatedDataStore_khongTrungSingletonSanXuat() {
        setupDialog()

        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val boundUserDataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context,
            com.mckimquyen.watermark.di.DataStoreTestEntryPoint::class.java
        ).userDataStore()

        assertThat(boundUserDataStore).isNotSameInstanceAs(context.userDataStore)
    }

    /** Switch mới không được đẩy 2 switch cũ (IDEA-13, IDEA-03) ra khỏi layout. */
    @Test
    fun cacSwitchCuVanCon() {
        val (_, dialog) = setupDialog()

        assertThat(dialog.binding.swProofingMode).isNotNull()
        assertThat(dialog.binding.swAuthenticityStamp).isNotNull()
        assertThat(dialog.binding.swInvisibleWatermark).isNotNull()
    }

    /**
     * Mô tả KHÔNG được nói "chỉ JPEG" như con dấu EXIF: lớp ẩn nằm trong pixel nên áp dụng được cho
     * mọi định dạng. Nhầm chỗ này là nói dối người dùng.
     */
    @Test
    fun moTa_khongGioiHanDinhDang() {
        val (activity, dialog) = setupDialog()

        val desc = dialog.binding.tvInvisibleWatermarkDesc.text.toString()
        assertThat(desc).isEqualTo(activity.getString(R.string.invisible_watermark_desc))
        assertThat(desc).doesNotContain("JPEG")
    }
}
