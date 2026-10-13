package com.mckimquyen.watermark.ui.panel

import androidx.fragment.app.commitNow
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
import kotlinx.coroutines.withTimeout
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
                    .commitNow()
                slider = requireNotNull(panel.view).findViewById(R.id.slideContentSize)
                assertThat(slider).isNotNull()
            }

            try {
                scenario.onActivity {
                    // Exercise actual AlphaPbFragment callback; MainViewModel writes to production Preferences DataStore.
                    panel.doOnChange(slider, TARGET_PERCENT, fromUser = true)
                }

                val stored = runBlocking {
                    withTimeout(DATASTORE_TIMEOUT_MS) {
                        viewModel.waterMarkRepo.waterMark.first { it.alpha == EXPECTED_NATIVE_ALPHA }
                    }
                }
                assertThat(stored.alpha).isEqualTo(EXPECTED_NATIVE_ALPHA)

                scenario.onActivity { activity ->
                    activity.supportFragmentManager.beginTransaction()
                        .remove(panel)
                        .commitNow()
                    panel = AlphaPbFragment()
                    activity.supportFragmentManager.beginTransaction()
                        .add(panel, FRAGMENT_TAG)
                        .commitNow()
                }

                val restored = runBlocking { viewModel.waterMarkRepo.waterMark.first() }
                assertThat(panel.formatValue(restored)).isEqualTo(TARGET_PERCENT)
                assertThat(panel.formatValueTips(restored)).contains("99")
            } finally {
                runBlocking { viewModel.waterMarkRepo.updateAlpha(originalAlpha) }
            }
        }
    }

    private companion object {
        const val FRAGMENT_TAG = "alpha-integration"
        const val TARGET_PERCENT = 99f
        const val EXPECTED_NATIVE_ALPHA = 252
        const val DEFAULT_ALPHA = 255
        const val DATASTORE_TIMEOUT_MS = 5_000L
    }
}
