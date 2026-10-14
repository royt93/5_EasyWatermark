package com.mckimquyen.watermark.ui.panel

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** ENH-48: real Activity/Fragment/ViewModel/Preferences DataStore alpha persist-and-restore integration. */
@RunWith(AndroidJUnit4::class)
class AlphaPbFragmentIntegrationTest {

    @Test
    fun slider99Percent_persistsNativeAlphaAndRestores99Percent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: MainViewModel
            lateinit var panel: AlphaPbFragment
            lateinit var slider: Slider
            var originalAlpha = DEFAULT_ALPHA

            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
                originalAlpha = runBlocking { viewModel.waterMarkRepo.waterMark.first().alpha }
                panel = AlphaPbFragment()
                activity.supportFragmentManager.beginTransaction()
                    .add(panel, FRAGMENT_TAG)
                    .commitNowAllowingStateLoss()
                slider = requireNotNull(panel.view).findViewById(R.id.slideContentSize)
                assertThat(slider).isNotNull()
            }

            try {
                scenario.onActivity {
                    // Exercise actual AlphaPbFragment callback; MainViewModel writes to production Preferences DataStore.
                    panel.doOnChange(slider, TARGET_PERCENT, fromUser = true)
                }

                // Poll snapshot thay vì `first { predicate }`: đo trên Pixel 7 Pro, chờ emit mới của
                // DataStore Flow bị timeout 5s dù đọc snapshot sau ~300ms đã thấy alpha=252.
                val persistedAlpha = pollAlpha(viewModel, EXPECTED_NATIVE_ALPHA)
                assertThat(persistedAlpha).isEqualTo(EXPECTED_NATIVE_ALPHA)

                val restoredConfig = runBlocking { viewModel.waterMarkRepo.waterMark.first() }
                assertThat(panel.formatValue(restoredConfig)).isEqualTo(TARGET_PERCENT)
                assertThat(panel.formatValueTips(restoredConfig)).contains("99")

                scenario.onActivity { activity ->
                    activity.supportFragmentManager.beginTransaction()
                        .remove(panel)
                        .commitNowAllowingStateLoss()
                    panel = AlphaPbFragment()
                    activity.supportFragmentManager.beginTransaction()
                        .add(panel, FRAGMENT_TAG)
                        .commitNowAllowingStateLoss()
                }
                val restoredFragmentConfig = runBlocking { viewModel.waterMarkRepo.waterMark.first() }
                assertThat(panel.formatValue(restoredFragmentConfig)).isEqualTo(TARGET_PERCENT)
                assertThat(panel.formatValueTips(restoredFragmentConfig)).contains("99")
            } finally {
                runBlocking { viewModel.waterMarkRepo.updateAlpha(originalAlpha) }
            }
        }
    }

    private fun pollAlpha(viewModel: MainViewModel, expected: Int): Int {
        val deadline = System.currentTimeMillis() + DATASTORE_TIMEOUT_MS
        var alpha = runBlocking { viewModel.waterMarkRepo.waterMark.first().alpha }
        while (alpha != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            alpha = runBlocking { viewModel.waterMarkRepo.waterMark.first().alpha }
        }
        return alpha
    }

    private companion object {
        const val FRAGMENT_TAG = "alpha-integration"
        const val TARGET_PERCENT = 99f
        const val EXPECTED_NATIVE_ALPHA = 252
        const val DEFAULT_ALPHA = 255
        const val DATASTORE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
