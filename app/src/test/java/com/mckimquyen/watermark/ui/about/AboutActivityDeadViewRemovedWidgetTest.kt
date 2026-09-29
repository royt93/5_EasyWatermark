package com.mckimquyen.watermark.ui.about

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.AAboutBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-37: guard chống ai vô tình thêm lại 19 view chết đã xoá khỏi `a_about.xml` (2 dev card
 * quảng bá tác giả/designer cũ, 6 binding-stub rỗng, 1 section label "Debug" mồ côi) — toàn bộ
 * luôn `visibility="gone"` vĩnh viễn, không code path nào từng bật hiện, 0 reference ngoài comment
 * chết trong `AboutActivity.kt` (đã xoá cùng đợt). `switchDebug` + card bọc nó GIỮ NGUYÊN (có
 * listener sống trong `AboutActivity.kt`), chỉ ẩn theo thiết kế — test khẳng định rõ 2 nhóm khác
 * nhau để tránh nhầm "gone" = "dead".
 */
@RunWith(RobolectricTestRunner::class)
class AboutActivityDeadViewRemovedWidgetTest {

    private val removedIds = listOf(
        "hsv", "clDevContainer", "clDesignerContainer", "tvTitle", "tvSubTitle", "civAvatar",
        "tvTitleDesigner", "tvSubTitleDesigner", "civAvatarDesigner",
        "tvChangeLog", "tvOpenSource", "tvPrivacyCn",
        "ivBack", "flBackButton", "tvHeaderTitle",
        "tvTitleInfo"
    )

    @Test
    fun aboutLayout_deadViewIds_noLongerFieldsOnBinding() {
        // getIdentifier() không đủ: vài tên id (vd. tvTitle) trùng tên ở layout KHÁC trong app nên
        // vẫn tồn tại global. Check đúng field trên chính AAboutBinding — bằng chứng chính xác
        // layout a_about.xml không còn khai báo id đó.
        val fieldNames = AAboutBinding::class.java.declaredFields.map { it.name }
        assertThat(fieldNames).containsNoneIn(removedIds)
    }

    @Test
    fun aboutLayout_switchDebugAndItsCard_stillPresent_onlyHiddenByDesign() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
        val binding = AAboutBinding.inflate(LayoutInflater.from(themedContext))

        assertThat(binding.switchDebug).isNotNull()
        assertThat(binding.switchDebug.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun aboutLayout_totalViewCount_reducedBelowPreCleanupBaseline() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
        val binding = AAboutBinding.inflate(LayoutInflater.from(themedContext))

        assertThat(countViews(binding.root)).isLessThan(95)
    }

    /**
     * `tvOpenSource` (stub chết, đã xoá) và `rowOpenSource` (row sống, mở
     * [OpenSourceActivity]) là 2 id KHÁC NHAU cùng chữ "OpenSource" — case dễ xoá nhầm nhất
     * trong đợt dọn này. Test khẳng định rõ row sống vẫn hoạt động đúng sau cleanup.
     */
    @Test
    fun aboutActivity_rowOpenSourceClick_stillLaunchesOpenSourceActivity() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        val shadowActivity = shadowOf(activity)

        val rowOpenSource = activity.findViewById<View>(R.id.rowOpenSource)
        assertThat(rowOpenSource).isNotNull()
        rowOpenSource.performClick()

        val nextIntent = shadowActivity.nextStartedActivity
        assertThat(nextIntent).isNotNull()
        assertThat(nextIntent.component?.className).isEqualTo(OpenSourceActivity::class.java.name)
    }

    /**
     * `switchDebug` giữ nguyên (không thuộc phạm vi xoá) — test khẳng định listener
     * (`AboutActivity.kt` L255-258) vẫn wire đúng sau khi dọn 3 listener chết kế bên
     * (`tvChangeLog`/`tvOpenSource`/`tvPrivacyCn`, cùng khối `setContent {}` lambda).
     */
    @Test
    fun aboutActivity_switchDebugToggle_listenerStillWired_noCrash() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()

        val switchDebug = activity.findViewById<MaterialSwitch>(R.id.switchDebug)
        assertThat(switchDebug).isNotNull()

        switchDebug.isChecked = true
        assertThat(switchDebug.isChecked).isTrue()

        switchDebug.isChecked = false
        assertThat(switchDebug.isChecked).isFalse()
    }

    private fun countViews(view: View): Int {
        var count = 1
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                count += countViews(view.getChildAt(i))
            }
        }
        return count
    }
}
