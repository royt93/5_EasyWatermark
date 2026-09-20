package com.mckimquyen.watermark.feature.vip

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit & Integration Tests for VipManagementActivity:
 * - Edge case calculations for elapsed progress bar
 * - Activity lifecycle & Material 3 view binding
 * - Privacy policy navigation intent
 * - Accessibility attributes and touch targets
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class VipManagementActivityRoboTest {

    // ----------------------------------------------------------------------------------
    // Unit Tests: computeElapsedProgress boundary conditions
    // ----------------------------------------------------------------------------------

    @Test
    fun computeElapsedProgress_boundaryCases() {
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java)
        val activity = controller.get()

        val start = 1_000_000L
        val end = 2_000_000L

        // Exactly at start: 0%
        assertThat(activity.computeElapsedProgress(start, end, start)).isEqualTo(0)

        // Halfway: 50%
        assertThat(activity.computeElapsedProgress(start, end, start + 500_000L)).isEqualTo(50)

        // At expiry: 100%
        assertThat(activity.computeElapsedProgress(start, end, end)).isEqualTo(100)

        // After expiry: capped at 100%
        assertThat(activity.computeElapsedProgress(start, end, end + 500_000L)).isEqualTo(100)

        // Before start: capped at 0%
        assertThat(activity.computeElapsedProgress(start, end, start - 100_000L)).isEqualTo(0)

        // Corrupted range (expiry <= grantedAt): returns 100%
        assertThat(activity.computeElapsedProgress(end, start, start)).isEqualTo(100)
        assertThat(activity.computeElapsedProgress(start, start, start)).isEqualTo(100)
    }

    // ----------------------------------------------------------------------------------
    // Integration Tests: Activity Scenario, Views & Privacy Action
    // ----------------------------------------------------------------------------------

    @Test
    fun activityScenario_verifiesViewsAndPrivacyPolicyIntent() {
        ActivityScenario.launch(VipManagementActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvPrivacy = activity.findViewById<TextView>(R.id.tvPrivacy)
                val btnRevoke = activity.findViewById<MaterialButton>(R.id.btnRevoke)
                val progressVip = activity.findViewById<LinearProgressIndicator>(R.id.progressVip)

                assertThat(tvPrivacy).isNotNull()
                assertThat(tvPrivacy.minimumHeight).isAtLeast(48)
                assertThat(btnRevoke).isNotNull()
                assertThat(btnRevoke.minimumHeight).isAtLeast(48)
                assertThat(progressVip).isNotNull()

                // Trigger privacy policy click
                tvPrivacy.performClick()

                val shadowActivity = shadowOf(activity)
                val startedIntent = shadowActivity.nextStartedActivity
                assertThat(startedIntent).isNotNull()
                assertThat(startedIntent.action).isEqualTo(Intent.ACTION_VIEW)
                assertThat(startedIntent.data).isEqualTo(Uri.parse(com.mckimquyen.watermark.common.const.AdKeys.PRIVACY_POLICY_URL))
            }
        }
    }
}
