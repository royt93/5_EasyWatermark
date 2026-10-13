package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-46: cửa sổ debounce auto-select của `TouchSensitiveRv` là hằng có tên [TouchSensitiveRv.AUTO_SELECT_DEBOUNCE_MS].
 * Test khoá hành vi: lần snap đầu bắn `onSnapViewSelected`; lần thứ 2 trong cửa sổ bị chặn; ranh giới cửa sổ test qua hàm thuần (đồng hồ hệ thống không giả được).
 */
@RunWith(RobolectricTestRunner::class)
class TouchSensitiveRvDebounceRoboTest {

    private val context: Context by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    private class Cell(context: Context) : View(context)

    private class CellAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(Cell(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        override fun getItemCount() = CELL_COUNT
    }

    private fun laidOutRv(): TouchSensitiveRv {
        val rv = TouchSensitiveRv(context)
        rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = CellAdapter()
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(RV_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(RV_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, RV_WIDTH, RV_HEIGHT)
        shadowOf(Looper.getMainLooper()).idle() // flush layout/adapter runnables để snapHelper thấy child
        return rv
    }

    /**
     * `RecyclerView.onScrollStateChanged(int)` chỉ là hook cho lớp con, KHÔNG phát tới `OnScrollListener`; listener
     * (nơi chứa debounce) chạy qua `dispatchOnScrollStateChanged` — đúng đường `setScrollState` đi khi cuộn dừng thật.
     */
    private fun idleScrollState(rv: TouchSensitiveRv) {
        val dispatch = RecyclerView::class.java.getDeclaredMethod("dispatchOnScrollStateChanged", Int::class.javaPrimitiveType)
        dispatch.isAccessible = true
        dispatch.invoke(rv, RecyclerView.SCROLL_STATE_IDLE)
    }

    @Test
    fun debounceConstant_isThreeHundredMs() {
        assertThat(TouchSensitiveRv.AUTO_SELECT_DEBOUNCE_MS).isEqualTo(300L)
    }

    @Test
    fun secondIdleWithinWindow_isSuppressed() {
        val rv = laidOutRv()
        var selected = 0
        rv.onSnapViewSelected { _, _ -> selected++ }

        idleScrollState(rv)
        assertThat(selected).isEqualTo(1) // tiền điều kiện: harness có snap view và bắn được callback
        idleScrollState(rv)

        assertThat(selected).isEqualTo(1)
    }

    @Test
    fun debounceWindow_boundaries() {
        val last = 1_000L
        val window = TouchSensitiveRv.AUTO_SELECT_DEBOUNCE_MS

        assertThat(TouchSensitiveRv.isWithinAutoSelectDebounce(last, last)).isTrue()
        assertThat(TouchSensitiveRv.isWithinAutoSelectDebounce(last + window - 1, last)).isTrue()
        // Đúng bằng cửa sổ trở đi thì không còn bị chặn (so sánh nghiêm `<`, như code gốc `< 300`).
        assertThat(TouchSensitiveRv.isWithinAutoSelectDebounce(last + window, last)).isFalse()
        assertThat(TouchSensitiveRv.isWithinAutoSelectDebounce(last + window + 1, last)).isFalse()
    }

    @Test
    fun firstIdle_afterInit_isNotDebounced() {
        // debounceTs khởi tạo 0 → mốc thời gian thực lớn hơn xa cửa sổ, lần snap đầu luôn bắn.
        assertThat(TouchSensitiveRv.isWithinAutoSelectDebounce(System.currentTimeMillis(), 0L)).isFalse()
    }

    @Test
    fun canAutoSelectedFalse_skipsOnceThenReEnables() {
        val rv = laidOutRv()
        var selected = 0
        rv.onSnapViewSelected { _, _ -> selected++ }
        rv.canAutoSelected = false

        idleScrollState(rv)

        assertThat(selected).isEqualTo(0)
        assertThat(rv.canAutoSelected).isTrue()
    }

    private companion object {
        const val CELL_COUNT = 5
        const val RV_WIDTH = 400
        const val RV_HEIGHT = 100
    }
}
