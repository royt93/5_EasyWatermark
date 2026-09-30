package com.mckimquyen.watermark.ui.adapter

import android.net.Uri
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Review pass 10 (2026-09-30) — REPRO + FIX: [GalleryAdapter.GalleryItemHolder] inflate view con
 * (`cbImage`/`ivImage`, cả 2 `lateinit var`) BẤT ĐỒNG BỘ qua `AsyncSquareFrameLayout.inflate()`
 * (`AsyncLayoutInflater`, hoàn tất qua Handler post lên main looper). `onBindViewHolder()` gắn
 * `setOnClickListener`/`setOnLongClickListener` NGAY LẬP TỨC (không đợi inflate xong) và cả 2 đọc
 * thẳng `holder.cbImage` — chạm vào item TRƯỚC khi inflate hoàn tất (item mới tạo/cuộn nhanh vào
 * item chưa từng bind, main thread bận dưới tải cao — đúng kịch bản máy chậm hay gặp trong dự án
 * này, xem TECNO_KJ7) ném `UninitializedPropertyAccessException`.
 *
 * Dưới Robolectric, main looper mặc định KHÔNG tự chạy các Runnable đã post cho tới khi
 * `shadowOf(Looper.getMainLooper()).idle()` được gọi — cho 1 cửa sổ tái hiện CHẮC CHẮN 100% (không
 * phụ thuộc timing thật) giữa lúc `onBindViewHolder()` xong và lúc `AsyncLayoutInflater` hoàn tất.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryAdapterAsyncInflateClickRaceTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    private fun images(count: Int) = (0 until count).map {
        Image(id = it, uri = Uri.parse("content://media/$it"), name = "img$it.jpg", size = 1024, date = it.toLong())
    }

    @Test
    fun `cham vao item ngay sau bind, TRUOC khi async inflate xong - khong crash`() {
        val adapter = GalleryAdapter()
        adapter.submitList(images(3))
        val parent = RecyclerView(themedContext)

        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        // KHÔNG idle main looper ở đây — cố tình giữ đúng cửa sổ TRƯỚC khi AsyncLayoutInflater
        // post callback hoàn tất (cbImage/ivImage vẫn CHƯA được gán).
        // Trước fix: ném kotlin.UninitializedPropertyAccessException (lateinit property cbImage)
        // ngay khi listener (gắn NGAY LẬP TỨC, ngoài bindWhenInflated) chạy.
        holder.itemView.performClick()
    }
}
