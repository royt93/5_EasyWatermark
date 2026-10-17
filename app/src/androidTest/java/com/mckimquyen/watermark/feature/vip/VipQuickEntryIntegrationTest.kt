package com.mckimquyen.watermark.feature.vip

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.widget.LaunchView
import com.roy.sdkadbmob.AdManager
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Chạy sau khi operator duyệt VIP tạm: không dùng mã thật, không ghi token hoặc secret vào log. */
@RunWith(AndroidJUnit4::class)
class VipQuickEntryIntegrationTest {
    @Test
    fun activeVip_updatesCrownStatusAndReturnsToFreeAfterCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeFalse("Giữ nguyên quyền lợi VIP đã có", AdManager.isVipByKeyActive())
        AdManager.suppressAppOpenTemporarily(true)
        try {
            assertThat(AdManager.grantVipDays(context, TEST_VIP_DAYS)).isTrue()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val launch = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LaunchView
                    assertThat(launch.ivGoVip.contentDescription.toString())
                        .isEqualTo(activity.getString(R.string.vip_status_toolbar_active))
                    val row = launch.ivGoVip.getChildAt(0) as ViewGroup
                    val crown = (row.getChildAt(0) as ViewGroup).getChildAt(0) as ImageView
                    assertThat(crown.imageTintList?.defaultColor).isEqualTo(activity.getColor(R.color.vip_gold))
                    val texts = row.getChildAt(1) as ViewGroup
                    assertThat((texts.getChildAt(1) as TextView).text.toString())
                        .isEqualTo(activity.getString(R.string.vip_status_toolbar_active))
                    assertThat(launch.toolbar.menu.findItem(R.id.actionVip)).isNotNull()
                }
            }
        } finally {
            AdManager.clearVipByKey()
            AdManager.suppressAppOpenTemporarily(false)
        }
        assertThat(AdManager.isVipByKeyActive()).isFalse()
    }

    private companion object {
        const val TEST_VIP_DAYS = 1
    }
}
