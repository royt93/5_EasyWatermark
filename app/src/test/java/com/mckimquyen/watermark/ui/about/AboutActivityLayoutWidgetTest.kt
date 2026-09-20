package com.mckimquyen.watermark.ui.about

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AboutActivityLayoutWidgetTest {

    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
    }

    @Test
    fun aboutLayout_containsVerticalEngagementAndDataManagementCards() {
        val inflater = android.view.LayoutInflater.from(themedContext)
        val binding = AAboutBinding.inflate(inflater)

        // Identity card
        assertThat(binding.cardIdentity).isNotNull()
        assertThat(binding.tvVersionValue).isNotNull()

        // Engagement section & card
        assertThat(binding.tvTitleEngagement).isNotNull()
        assertThat(binding.cardEngagement).isNotNull()
        assertThat(binding.tvRating).isNotNull()
        assertThat(binding.tvShareApp).isNotNull()
        assertThat(binding.tvMoreApp).isNotNull()

        // Verify each row has title text
        val tvRatingTitle = binding.tvRating.findViewById<TextView>(android.R.id.text1)
            ?: binding.tvRating.findViewWithTag<TextView>("title")
            ?: (binding.tvRating.getChildAt(1) as? android.view.ViewGroup)?.getChildAt(0) as? TextView
        assertThat(tvRatingTitle?.text?.toString()).isEqualTo(themedContext.getString(R.string.action_rate_us))

        // Data Management section & card
        assertThat(binding.tvTitleData).isNotNull()
        assertThat(binding.cardDataManagement).isNotNull()
        assertThat(binding.tvBackupData).isNotNull()
        assertThat(binding.tvRestoreData).isNotNull()
    }

    @Test
    fun aboutActivity_ratingRowClick_launchesPlayStoreIntent() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)

        val tvRating = activity.findViewById<View>(R.id.tvRating)
        assertThat(tvRating).isNotNull()
        tvRating.performClick()

        val nextIntent = shadowActivity.nextStartedActivity
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(nextIntent.data?.toString()).contains("play.google.com/store/apps/details?id=")
    }

    @Test
    fun aboutActivity_shareRowClick_launchesShareChooserIntent() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)

        val tvShareApp = activity.findViewById<View>(R.id.tvShareApp)
        assertThat(tvShareApp).isNotNull()
        tvShareApp.performClick()

        val nextIntent = shadowActivity.nextStartedActivity
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.action).isEqualTo(Intent.ACTION_CHOOSER)
    }

    @Test
    fun aboutActivity_moreAppsRowClick_launchesDeveloperSearchIntent() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)

        val tvMoreApp = activity.findViewById<View>(R.id.tvMoreApp)
        assertThat(tvMoreApp).isNotNull()
        tvMoreApp.performClick()

        val nextIntent = shadowActivity.nextStartedActivity
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(nextIntent.data?.toString()).contains("play.google.com/store/search?q=pub:")
    }
}
