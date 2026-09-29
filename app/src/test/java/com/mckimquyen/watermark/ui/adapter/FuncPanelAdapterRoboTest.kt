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

    @org.junit.Before
    fun setUp() {
        context.setTheme(R.style.Theme_MyApp)
    }

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

    /**
     * BUG-AUDIT-2026-09-29: `seNewData()` trước đây gán `selectedPos = toPos` (trigger notify trên
     * dataSet CŨ) TRƯỚC khi đổi dataSet — `toPos` vượt size dataSet CŨ nhưng hợp lệ với dataSet
     * MỚI (dài hơn) phải không crash, kết thúc ở trạng thái đúng (item được chọn hiển thị đúng).
     */
    @Test
    fun seNewData_toPosBeyondOldDataSetSize_doesNotCrash_selectsCorrectItemInNewDataSet() {
        val adapter = FuncPanelAdapter(context, dataSet()) // size cũ = 2
        val longerList = listOf(
            FuncTitleModel(FuncTitleModel.FuncType.Text, "A", R.drawable.ic_func_text),
            FuncTitleModel(FuncTitleModel.FuncType.Text, "B", R.drawable.ic_func_text),
            FuncTitleModel(FuncTitleModel.FuncType.Text, "C", R.drawable.ic_func_text),
            FuncTitleModel(FuncTitleModel.FuncType.Text, "D", R.drawable.ic_func_text)
        )

        adapter.seNewData(longerList, toPos = 3) // 3 >= size cũ (2), hợp lệ với size mới (4)

        assertThat(adapter.itemCount).isEqualTo(4)
        assertThat(adapter.selectedPos).isEqualTo(3)
        val holder = createBoundHolder(adapter, 3)
        assertThat(holder.flIconBg.isSelected).isTrue()
    }
}
