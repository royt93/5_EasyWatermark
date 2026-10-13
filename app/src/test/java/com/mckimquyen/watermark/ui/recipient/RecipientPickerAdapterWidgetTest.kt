package com.mckimquyen.watermark.ui.recipient

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.entity.Recipient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** IDEA-10 / ENH-47: adapter widget test — None, selected state, bind recipient and click payload. */
@RunWith(RobolectricTestRunner::class)
class RecipientPickerAdapterWidgetTest {

    private val context by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    private fun holder(adapter: RecipientPickerBottomSheetFragment.RecipientPickerAdapter) =
        adapter.onCreateViewHolder(FrameLayout(context), 0)

    private fun recipient(
        code: String = "CODE-1",
        name: String = "Recipient One",
        notes: String? = "note"
    ) = Recipient(id = 1, code = code, name = name, notes = notes)

    @Test
    fun emptyList_hasOnlyNoneRow_andSelectingReturnsNull() {
        var selected: Recipient? = recipient()
        val adapter = RecipientPickerBottomSheetFragment.RecipientPickerAdapter(null) { selected = it }
        adapter.submitList(emptyList())
        val holder = holder(adapter)

        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()

        assertThat(adapter.itemCount).isEqualTo(1)
        assertThat(selected).isNull()
        assertThat(holder.itemView.findViewById<View>(R.id.ivSelectedCheck).visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun selectedRecipient_showsNameCodeAndCheck_andClickReturnsSameRecipient() {
        val item = recipient()
        var selected: Recipient? = null
        val adapter = RecipientPickerBottomSheetFragment.RecipientPickerAdapter(item.code) { selected = it }
        adapter.submitList(listOf(item))
        val holder = holder(adapter)

        adapter.onBindViewHolder(holder, 1)

        assertThat(holder.itemView.findViewById<android.widget.TextView>(R.id.tvRecipientName).text).isEqualTo(item.name)
        assertThat(holder.itemView.findViewById<android.widget.TextView>(R.id.tvRecipientCode).text.toString()).contains(item.code)
        assertThat(holder.itemView.findViewById<View>(R.id.ivSelectedCheck).visibility).isEqualTo(View.VISIBLE)
        holder.itemView.performClick()
        assertThat(selected).isSameInstanceAs(item)
    }

    @Test
    fun unselectedRecipient_hidesCheck_andOmitsBlankNotes() {
        val item = recipient(code = "C-2", name = "Recipient Two", notes = "  ")
        val adapter = RecipientPickerBottomSheetFragment.RecipientPickerAdapter("OTHER") {}
        adapter.submitList(listOf(item))
        val holder = holder(adapter)

        adapter.onBindViewHolder(holder, 1)

        assertThat(holder.itemView.findViewById<View>(R.id.ivSelectedCheck).visibility).isEqualTo(View.GONE)
        val code = holder.itemView.findViewById<android.widget.TextView>(R.id.tvRecipientCode)
        assertThat(code.visibility).isEqualTo(View.VISIBLE)
        assertThat(code.text.toString()).contains(item.code)
        assertThat(code.text.toString()).doesNotContain(" · ")
    }

    @Test
    fun submitList_replacesItemsAndCountIncludesNoneRow() {
        val adapter = RecipientPickerBottomSheetFragment.RecipientPickerAdapter(null) {}
        adapter.submitList(listOf(recipient()))
        assertThat(adapter.itemCount).isEqualTo(2)

        adapter.submitList(listOf(recipient("A", "Alpha", null), recipient("B", "Beta", null)))

        assertThat(adapter.itemCount).isEqualTo(3)
    }
}
