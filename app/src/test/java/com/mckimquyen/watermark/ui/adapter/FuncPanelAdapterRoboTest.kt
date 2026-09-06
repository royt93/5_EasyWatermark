package com.mckimquyen.watermark.ui.adapter

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.FuncTitleModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget test (Robolectric): [FuncPanelAdapter] nay nhan `context` qua constructor thay vi
 * `MyApplication.instance` (dep chinh cua doc/todo.md - static instance leak). Xac nhan
 * adapter bind dung text/color voi context truyen vao, khong crash khi khong co static instance.
 */
@RunWith(RobolectricTestRunner::class)
class FuncPanelAdapterRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun dataSet() = arrayListOf(
        FuncTitleModel(FuncTitleModel.FuncType.Text, "Text", R.drawable.ic_func_text),
        FuncTitleModel(FuncTitleModel.FuncType.Alpha, "Alpha", R.drawable.ic_func_text)
    )

    private fun createBoundHolder(adapter: FuncPanelAdapter, position: Int): FuncPanelAdapter.FuncTitleHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    @Test
    fun constructor_derivesTextColor_fromPassedContext_notCrash() {
        // Không còn MyApplication.instance: constructor phải chạy được chỉ với context truyền vào.
        val adapter = FuncPanelAdapter(context, dataSet())

        assertThat(adapter.itemCount).isEqualTo(2)
    }

    @Test
    fun bind_unselectedItem_showsTitleAndPlainTextColor() {
        val adapter = FuncPanelAdapter(context, dataSet())
        adapter.selectedPos = 99 // khong khop item nao -> nhanh "else" dung textColor thang

        val holder = createBoundHolder(adapter, 0)

        assertThat(holder.tvTitle.text).isEqualTo("Text")
        assertThat(holder.tvTitle.currentTextColor).isEqualTo(adapter.textColor)
    }

    @Test
    fun applyTextColor_updatesColor_usedOnNextBind() {
        val adapter = FuncPanelAdapter(context, dataSet())
        adapter.selectedPos = 99
        val newColor = 0xFF112233.toInt()

        adapter.applyTextColor(newColor)
        val holder = createBoundHolder(adapter, 1)

        assertThat(adapter.textColor).isEqualTo(newColor)
        assertThat(holder.tvTitle.currentTextColor).isEqualTo(newColor)
    }

    @Test
    fun seNewData_replacesDataSet_andRebindsCorrectItem() {
        val adapter = FuncPanelAdapter(context, dataSet())

        adapter.seNewData(
            listOf(FuncTitleModel(FuncTitleModel.FuncType.Degree, "Degree", R.drawable.ic_func_text)),
            toPos = 0
        )
        val holder = createBoundHolder(adapter, 0)

        assertThat(adapter.itemCount).isEqualTo(1)
        assertThat(holder.tvTitle.text).isEqualTo("Degree")
    }
}
