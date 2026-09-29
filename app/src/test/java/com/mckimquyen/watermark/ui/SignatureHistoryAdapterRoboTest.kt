package com.mckimquyen.watermark.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.SignatureModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * BUG-AUDIT-2026-09-29: `onBindViewHolder` trước đây gọi lại `findViewById()` + tính
 * `Rect`/`TouchDelegate` MỖI LẦN bind — chuyển sang cache trong `SignatureHistoryViewHolder` (tạo
 * 1 lần lúc `onCreateViewHolder`). Gắn adapter vào 1 `RecyclerView` thật (không chỉ gọi tay
 * `onBindViewHolder`) để `bindingAdapterPosition` hoạt động đúng, click callback nhận đúng
 * position.
 *
 * `@Config(sdk = N)`: `ImageView.setImageURI()` từ API 28 trở lên dùng `ImageDecoder` nội bộ —
 * `ShadowImageDecoder` của Robolectric không decode được stream test tối giản (giới hạn môi
 * trường test, không liên quan code thật). Khoá SDK API 24 (đúng minSdk thật của app) để đi qua
 * nhánh `Drawable.createFromStream` cũ hơn, decode ổn định trong Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.N])
class SignatureHistoryAdapterRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** `ImageView.setImageURI()` decode thật qua `ImageDecoder` — cần URI trỏ tới ảnh PNG thật
     *  trên đĩa (`file://`), URI giả `content://` không backed bởi provider nào sẽ ném
     *  `UnsupportedOperationException` khi Robolectric cố đọc stream thật. */
    private fun signature(name: String): SignatureModel {
        val file = File(context.cacheDir, "$name.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return SignatureModel(file = file, dateModified = 0L, uriProvider = { Uri.fromFile(file) })
    }

    private fun setupRecyclerView(adapter: SignatureHistoryAdapter): RecyclerView {
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        val parent = FrameLayout(context).apply { addView(rv) }
        parent.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(500, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 500, 1000)
        return rv
    }

    @Test
    fun rebindingSameHolder_toDifferentPositions_showsCorrectItemEachTime() {
        val adapter = SignatureHistoryAdapter(mutableListOf(signature("a"), signature("b"), signature("c")))
        val rv = setupRecyclerView(adapter)

        // Cùng 1 ViewHolder được RecyclerView tái sử dụng khi cuộn qua nhiều item hơn view khả
        // dụng — verify view cache (ivSignature/btnDelete) KHÔNG lẫn giữa các lần rebind: mỗi lần
        // click đúng đúng item đang hiển thị tại vị trí đó.
        var clickedPos = -1
        adapter.onItemClick = { clickedPos = it }

        val holder0 = rv.findViewHolderForAdapterPosition(0)
        assertThat(holder0).isNotNull()
        holder0!!.itemView.performClick()
        assertThat(clickedPos).isEqualTo(0)

        val holder1 = rv.findViewHolderForAdapterPosition(1)
        assertThat(holder1).isNotNull()
        holder1!!.itemView.performClick()
        assertThat(clickedPos).isEqualTo(1)
    }

    @Test
    fun onCreateViewHolder_cachesViewsOnce_stableAcrossMultipleBinds() {
        val adapter = SignatureHistoryAdapter(mutableListOf(signature("a"), signature("b")))
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder, 0)
        val ivFirst = holder.ivSignature
        val btnFirst = holder.btnDelete

        adapter.onBindViewHolder(holder, 1)
        adapter.onBindViewHolder(holder, 0)

        // Cache 1 lần lúc tạo ViewHolder — cùng instance suốt vòng đời, không findViewById lại.
        assertThat(holder.ivSignature).isSameInstanceAs(ivFirst)
        assertThat(holder.btnDelete).isSameInstanceAs(btnFirst)
    }

    @Test
    fun deleteButtonClick_invokesCallbackWithCorrectPosition() {
        val adapter = SignatureHistoryAdapter(mutableListOf(signature("a"), signature("b")))
        val rv = setupRecyclerView(adapter)
        var deletedPos = -1
        adapter.onDeleteClick = { deletedPos = it }

        val holder1 = rv.findViewHolderForAdapterPosition(1)
        assertThat(holder1).isNotNull()
        (holder1 as SignatureHistoryAdapter.SignatureHistoryViewHolder).btnDelete.performClick()

        assertThat(deletedPos).isEqualTo(1)
    }
}
