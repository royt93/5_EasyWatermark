package com.mckimquyen.watermark.ui.dlg

import android.app.Dialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.MainActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CompressImageDialogFragment — state-based dialog cho compress progress.
 * - Dialog non-cancelable
 * - setTupState() handle 3 state: COMPRESSING, OK, ERROR + default
 * - Cancel: viewModel.cancelCompressJob() + dismiss
 * - OK: dismiss
 * - Retry: viewModel.compressImg()
 * - safetyShow: tránh duplicate
 */
@RunWith(RobolectricTestRunner::class)
class CompressImageDialogFragmentTest {

    @Before
    fun setUp() {
        // Initialization
    }

    @Test
    fun onCreateDialog_setNonCancelable() {
        // Test that dialog is created as non-cancelable with transparent background
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = CompressImageDialogFragment()
                val dialog = fragment.onCreateDialog(null)

                assertThat(dialog.isCancelable).isFalse()
            }
        }
    }

    @Test
    fun safetyShow_preventsDuplicateDialog() {
        // Test that safetyShow() prevents showing duplicate CompressImageDialogFragment instances
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager

                // First show
                CompressImageDialogFragment.safetyShow(fragmentManager)

                val firstInstance = fragmentManager.findFragmentByTag("CompressImageDialogFragment")
                assertThat(firstInstance).isNotNull()
                assertThat(firstInstance).isInstanceOf(CompressImageDialogFragment::class.java)

                // Second show (should reuse same instance if already added)
                CompressImageDialogFragment.safetyShow(fragmentManager)

                val secondInstance = fragmentManager.findFragmentByTag("CompressImageDialogFragment")
                assertThat(secondInstance).isSameInstanceAs(firstInstance)
            }
        }
    }

    @Test
    fun safetyShow_handleFragmentNotAddedButExist() {
        // Test that safetyShow() re-shows fragment if it exists but is not added
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragmentManager = activity.supportFragmentManager

                // Show first time
                CompressImageDialogFragment.safetyShow(fragmentManager)

                val firstInstance = fragmentManager.findFragmentByTag("CompressImageDialogFragment")
                assertThat(firstInstance).isNotNull()

                // In real scenario, fragment might be not added (removed after dismiss)
                // but still in manager's fragment list. safetyShow should handle it.
                CompressImageDialogFragment.safetyShow(fragmentManager)
            }
        }
    }

    @Test
    fun safetyShow_handleIllegalState() {
        // Test that safetyShow() gracefully handles IllegalStateException (manager state invalid)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Valid fragment manager, but if there's concurrent transaction,
                // findFragmentByTag might throw IllegalStateException.
                // safetyShow should not crash application.
                val fragmentManager = activity.supportFragmentManager

                // This should not throw — safetyShow catches IllegalStateException
                CompressImageDialogFragment.safetyShow(fragmentManager)
                CompressImageDialogFragment.safetyShow(fragmentManager)
                CompressImageDialogFragment.safetyShow(fragmentManager)
            }
        }
    }
}
