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
 * IDEA-13 widget test: kiểm tra switch Proofing Mode trong dialog lưu ảnh.
 *
 * BUG-FLAKY-2026-09-30: xem giải thích đầy đủ ở [SaveImageBSDialogFragmentInvisibleRoboTest] —
 * dùng [HiltAndroidRule] + `TestDataStoreModule` (`@TestInstallIn`) để cô lập DataStore, tránh
 * deadlock Mutex singleton dùng chung khi chạy full suite.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentProofingRoboTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // BUG-FLAKY-2026-09-30: xem giải thích ở SaveImageBSDialogFragmentInvisibleRoboTest.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private fun setupDialog(): Pair<MainActivity, SaveImageBSDialogFragment> {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
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
            .add(dialogFragment, "SaveImageBSDialogFragmentTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun swProofingMode_isMaterialSwitch_andReflectsViewModelState() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swProofingMode
        assertThat(switch).isInstanceOf(MaterialSwitch::class.java)
        assertThat(switch.isChecked).isEqualTo(viewModel.proofingMode)
        assertThat(switch.contentDescription.toString())
            .isEqualTo(activity.getString(R.string.proofing_mode_title))
    }

    @Test
    fun swProofingMode_toggle_callsSaveProofingMode() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swProofingMode
        val initialState = viewModel.proofingMode

        // Giả lập user bấm trực tiếp (isPressed = true)
        switch.isPressed = true
        switch.isChecked = !initialState
        shadowOf(Looper.getMainLooper()).idle()

        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.proofingMode == initialState && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.proofingMode).isEqualTo(!initialState)
    }

    /**
     * Integration test (BUG-FLAKY-2026-09-30): xem giải thích đầy đủ ở
     * [SaveImageBSDialogFragmentInvisibleRoboTest.setupDialog_hiltBindsIsolatedDataStore_khongTrungSingletonSanXuat] —
     * so REFERENCE (không so giá trị) để không phụ thuộc thứ tự chạy các test khác.
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
}
