package com.mckimquyen.watermark.ui

import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.feature.vip.VipManagementActivity
import com.mckimquyen.watermark.ui.widget.LaunchView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityVipEntryWidgetTest {

    @Test
    fun launchVipCard_existsIsAccessibleAndNavigatesToVipManagement() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = content.getChildAt(0) as com.mckimquyen.watermark.ui.widget.LaunchView
        val card = launchView.ivGoVip

        assertThat(card).isInstanceOf(MaterialCardView::class.java)
        assertThat(card.cardBackgroundColor.defaultColor)
            .isEqualTo(launchView.ivSelectedPhotoTips.cardBackgroundColor.defaultColor)
        assertThat(card.contentDescription).isEqualTo(activity.getString(R.string.vip_status_toolbar_free))
        card.performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.component?.className).isEqualTo(VipManagementActivity::class.java.name)
        controller.pause().stop().destroy()
    }

    @Test
    fun vipCrown_activeIsGold_animates_andStopsWhenDetached() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val launchView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LaunchView
        val badge = (launchView.ivGoVip.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup
        val icon = badge.getChildAt(0) as ImageView

        launchView.bindVipState(true)

        assertThat(icon.imageTintList?.defaultColor).isEqualTo(activity.getColor(R.color.vip_gold))
        assertThat(launchView.isVipCrownAnimating).isTrue()
        controller.pause().stop().destroy()
        assertThat(launchView.isVipCrownAnimating).isFalse()
    }

    @Test
    fun vipCrown_whenSystemAnimationsDisabled_doesNotAnimate() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        android.provider.Settings.Global.putFloat(
            activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val launchView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LaunchView

        launchView.bindVipState(false)

        assertThat(launchView.isVipCrownAnimating).isFalse()
        controller.pause().stop().destroy()
    }
}
