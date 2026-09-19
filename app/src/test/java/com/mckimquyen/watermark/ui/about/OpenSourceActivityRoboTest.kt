package com.mckimquyen.watermark.ui.about

import android.content.Intent
import android.os.Build
import android.os.Looper
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class OpenSourceActivityRoboTest {

    @Test
    fun openSourceActivity_insetsAppliedToToolbarAndRoot() {
        val controller = Robolectric.buildActivity(OpenSourceActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.myToolbar)
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

        assertThat(toolbar).isNotNull()
        assertThat(contentRoot).isNotNull()

        val mockInsets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                Insets.of(0, 75, 0, 0)
            )
            .setInsets(
                WindowInsetsCompat.Type.navigationBars(),
                Insets.of(0, 0, 0, 110)
            )
            .build()

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets)

        // Verify toolbar top padding received status bar inset
        assertThat(toolbar.paddingTop).isEqualTo(75)

        // Verify root bottom padding received navigation bar inset
        val baseScrollBottom = (32 * activity.resources.displayMetrics.density).toInt()
        assertThat(contentRoot.paddingBottom).isEqualTo(baseScrollBottom + 110)

        controller.pause().stop().destroy()
    }

    @Test
    fun openSourceActivity_cardsLaunchCorrectUrls() {
        val controller = Robolectric.buildActivity(OpenSourceActivity::class.java).create().start().resume()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)
        shadowOf(Looper.getMainLooper()).idle()

        // 1. ColorPickerView
        val cardColorPicker = activity.findViewById<MaterialCardView>(R.id.cardColorPicker)
        cardColorPicker.performClick()
        val intent1 = shadowActivity.nextStartedActivity
        assertThat(intent1).isNotNull()
        assertThat(intent1.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent1.dataString).isEqualTo("https://github.com/skydoves/ColorPickerView")

        // 2. Glide
        val cardGlide = activity.findViewById<MaterialCardView>(R.id.cardGlideLibrary)
        cardGlide.performClick()
        val intent2 = shadowActivity.nextStartedActivity
        assertThat(intent2).isNotNull()
        assertThat(intent2.dataString).isEqualTo("https://github.com/bumptech/glide")

        // 3. Material Components
        val cardMdc = activity.findViewById<MaterialCardView>(R.id.cardMaterialComponents)
        cardMdc.performClick()
        val intent3 = shadowActivity.nextStartedActivity
        assertThat(intent3).isNotNull()
        assertThat(intent3.dataString).isEqualTo("https://github.com/material-components/material-components-android")

        // 4. Compressor
        val cardCompressor = activity.findViewById<MaterialCardView>(R.id.cardMaterialCompressor)
        cardCompressor.performClick()
        val intent4 = shadowActivity.nextStartedActivity
        assertThat(intent4).isNotNull()
        assertThat(intent4.dataString).isEqualTo("https://github.com/zetbaitsu/Compressor/")

        controller.pause().stop().destroy()
    }
}
