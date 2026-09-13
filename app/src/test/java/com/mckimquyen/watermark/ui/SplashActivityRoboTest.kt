package com.mckimquyen.watermark.ui

import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SplashActivityRoboTest {

    @Test
    fun splashActivity_inflatesAndBindsAllBrandingElements() {
        val controller = Robolectric.buildActivity(SplashActivity::class.java).create().start().resume()
        val activity = controller.get()

        val tvAppName = activity.findViewById<TextView>(R.id.tvAppName)
        val tvTagline = activity.findViewById<TextView>(R.id.tvTagline)
        val progressLoading = activity.findViewById<LinearProgressIndicator>(R.id.progressLoading)
        val tvVersion = activity.findViewById<TextView>(R.id.tvVersion)
        val tvCopyright = activity.findViewById<TextView>(R.id.tvCopyright)

        assertThat(tvAppName).isNotNull()
        assertThat(tvAppName.text.toString()).isEqualTo(activity.getString(R.string.app_name))

        assertThat(tvTagline).isNotNull()
        assertThat(tvTagline.text.toString()).contains("Offline Protection")

        assertThat(progressLoading).isNotNull()
        assertThat(progressLoading.isIndeterminate).isTrue()

        assertThat(tvVersion).isNotNull()
        assertThat(tvVersion.text.toString()).contains(BuildConfig.VERSION_NAME)

        assertThat(tvCopyright).isNotNull()
        assertThat(tvCopyright.text.toString()).contains("© 2026 McKim Quyen")

        controller.pause().stop().destroy()
    }
}
