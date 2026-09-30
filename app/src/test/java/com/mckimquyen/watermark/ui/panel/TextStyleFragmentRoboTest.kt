package com.mckimquyen.watermark.ui.panel

import android.os.Build
import android.os.Looper
import androidx.recyclerview.widget.ConcatAdapter
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.ui.adapter.TextEffectAdapter
import com.mckimquyen.watermark.ui.adapter.TextPaintStyleAdapter
import com.mckimquyen.watermark.ui.adapter.TextTypefaceAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * P1 review pass 8: `TextStyleFragment` không có test nào (8 fragment panel còn lại đều có) —
 * mapping `effectAdapter` position -> handler (`when (pos) { 0..3 -> ... }`) + cross-callback
 * `paintStyleAdapter`/`typefaceAdapter` đủ phức tạp để cần test trực tiếp thay vì chỉ tin bằng mắt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TextStyleFragmentRoboTest {

    @Suppress("UNCHECKED_CAST")
    private fun mainViewModel(activity: MainActivity): MainViewModel =
        MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
            .invoke(activity) as MainViewModel

    private fun setupFragment(): Pair<MainActivity, TextStyleFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = TextStyleFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, TextStyleFragment.TAG)
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    private fun concatAdapter(fragment: TextStyleFragment): ConcatAdapter =
        fragment.binding?.rvColor?.adapter as ConcatAdapter

    /** `updateXxx()` ghi qua DataStore thật (IO dispatcher, thread thật) rồi Flow mới emit lại
     * LiveData -> idle() 1 lần không đủ, phải bơm lặp lại tới khi giá trị đổi (cùng pattern các
     * test async khác trong project, vd `SaveImageListAdapterUpdateJobStateRoboTest.awaitFinishCount`). */
    private fun awaitUntil(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(20)
        }
    }

    /** Mỗi chip effect (vị trí 0..3) phải gọi ĐÚNG hàm ViewModel tương ứng — không lệch mapping. */
    @Test
    fun effectChip_eachPosition_updatesCorrectWaterMarkField() {
        val (activity, fragment) = setupFragment()
        val viewModel = mainViewModel(activity)
        val effectAdapter = concatAdapter(fragment).adapters.filterIsInstance<TextEffectAdapter>().single()
        val rv = fragment.binding!!.rvColor

        fun clickPosition(pos: Int) {
            val holder = effectAdapter.onCreateViewHolder(rv, 0)
            effectAdapter.onBindViewHolder(holder, pos)
            holder.root.performClick()
        }

        clickPosition(0) // Stroke
        awaitUntil { viewModel.waterMark.value?.textEffectStroke == true }
        assertThat(viewModel.waterMark.value?.textEffectStroke).isTrue()
        assertThat(viewModel.waterMark.value?.textEffectShadow).isFalse()

        clickPosition(1) // Shadow
        awaitUntil { viewModel.waterMark.value?.textEffectShadow == true }
        assertThat(viewModel.waterMark.value?.textEffectShadow).isTrue()

        clickPosition(2) // Pill background
        awaitUntil { viewModel.waterMark.value?.textEffectPillBackground == true }
        assertThat(viewModel.waterMark.value?.textEffectPillBackground).isTrue()

        clickPosition(3) // Auto contrast
        awaitUntil { viewModel.waterMark.value?.autoContrastEnabled == true }
        assertThat(viewModel.waterMark.value?.autoContrastEnabled).isTrue()

        // Stroke vẫn giữ nguyên true — 4 lần click trên không giẫm lên nhau (đúng mapping độc lập).
        assertThat(viewModel.waterMark.value?.textEffectStroke).isTrue()
    }

    @Test
    fun paintStyleChip_click_updatesWaterMarkTextStyle() {
        val (activity, fragment) = setupFragment()
        val viewModel = mainViewModel(activity)
        val paintStyleAdapter = concatAdapter(fragment).adapters.filterIsInstance<TextPaintStyleAdapter>().single()
        val rv = fragment.binding!!.rvColor

        // Vị trí 1 = Stroke (obtainDefaultPaintStyleList: [Fill, Stroke]).
        val holder = paintStyleAdapter.onCreateViewHolder(rv, 0)
        paintStyleAdapter.onBindViewHolder(holder, 1)
        (holder.itemView).performClick()
        awaitUntil { viewModel.waterMark.value?.textStyle == TextPaintStyle.Stroke }

        assertThat(viewModel.waterMark.value?.textStyle).isEqualTo(TextPaintStyle.Stroke)
    }

    @Test
    fun typefaceChip_click_updatesWaterMarkTypeface() {
        val (activity, fragment) = setupFragment()
        val viewModel = mainViewModel(activity)
        val typefaceAdapter = concatAdapter(fragment).adapters.filterIsInstance<TextTypefaceAdapter>().single()
        val rv = fragment.binding!!.rvColor

        // Vị trí 1 = Bold (obtainDefaultTypefaceList: [Normal, Bold, Italic, BoldItalic]).
        val holder = typefaceAdapter.onCreateViewHolder(rv, 0)
        typefaceAdapter.onBindViewHolder(holder, 1)
        (holder.itemView).performClick()
        awaitUntil { viewModel.waterMark.value?.textTypeface == TextTypeface.Bold }

        assertThat(viewModel.waterMark.value?.textTypeface).isEqualTo(TextTypeface.Bold)
    }
}
