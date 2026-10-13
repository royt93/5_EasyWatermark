package com.mckimquyen.watermark.ui

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ENH-49: shell profiling is enabled only for debug manifest variant. */
@RunWith(RobolectricTestRunner::class)
class ManifestVariantConfigurationRoboTest {

    @Test
    fun shellProfileable_matchesBuildVariantPolicy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profileable = context.packageManager.getApplicationInfo(context.packageName, 0).isProfileableByShell

        assertThat(profileable).isEqualTo(BuildConfig.DEBUG)
    }
}
