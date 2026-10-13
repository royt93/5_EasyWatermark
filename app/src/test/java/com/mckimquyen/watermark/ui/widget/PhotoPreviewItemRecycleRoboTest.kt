package com.mckimquyen.watermark.ui.widget

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.adapter.PhotoListPreviewAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * BUG-81: `PhotoPreviewItem` chạy animator kéo-xoá kèm `withEndAction { onRemove }`; ViewHolder bị recycle giữa
 * chừng (RecyclerView tái dùng cho item khác) phải huỷ animator + reset trạng thái, nếu không callback xoá bắn
 * vào item mới và view tái dùng còn alpha/translation/long-press của item cũ.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoPreviewItemRecycleRoboTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    private class Fixture(
        val adapter: PhotoListPreviewAdapter,
        val holder: PhotoListPreviewAdapter.ImageHolder,
        val item: PhotoPreviewItem
    ) {
        var removeCount = 0
    }

    private fun fixture(): Fixture {
        // parent.parent của ivIcon phải là ViewGroup có > 1 child (long-click guard của widget).
        val list = FrameLayout(themedContext)
        val adapter = PhotoListPreviewAdapter(themedContext)
        val holder = adapter.onCreateViewHolder(list, 0)
        val item = holder.itemView as PhotoPreviewItem
        list.addView(item)
        list.addView(View(themedContext))
        item.measure(
            View.MeasureSpec.makeMeasureSpec(ITEM_SIZE, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ITEM_SIZE, View.MeasureSpec.EXACTLY)
        )
        item.layout(0, 0, ITEM_SIZE, ITEM_SIZE)
        return Fixture(adapter, holder, item).also { f -> item.onRemove { f.removeCount++ } }
    }

    private fun touch(item: PhotoPreviewItem, action: Int, y: Float) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, 0f, y, 0)
        item.ivIcon.dispatchTouchEvent(ev)
        ev.recycle()
    }

    /** Gesture thật: chạm giữ → long-press → kéo lên quá ivDel → thả (bắt đầu animator xoá). */
    private fun dragToRemoveAndRelease(item: PhotoPreviewItem) {
        touch(item, MotionEvent.ACTION_DOWN, 0f)
        item.ivIcon.performLongClick()
        touch(item, MotionEvent.ACTION_MOVE, DRAG_FAR_UP)
        touch(item, MotionEvent.ACTION_UP, DRAG_FAR_UP)
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun dragToRemove_withoutRecycle_invokesOnRemoveOnce() {
        val f = fixture()

        dragToRemoveAndRelease(f.item)
        idle(ANIMATION_SETTLE_MS)

        // Đối chứng: harness thật sự chạy được animator xoá tới cùng.
        assertThat(f.removeCount).isEqualTo(1)
    }

    @Test
    fun recycleRightAfterRelease_cancelsOnRemove() {
        val f = fixture()

        // Thả tay → animator xoá đã start() nhưng chưa chạy frame nào (Looper chưa idle): đây là trạng thái
        // "đang dở" xác định được (Robolectric hoàn tất animator trong 1 tick nên không idle một phần được).
        dragToRemoveAndRelease(f.item)
        assertThat(f.removeCount).isEqualTo(0)
        f.adapter.onViewRecycled(f.holder)
        idle(ANIMATION_SETTLE_MS)

        assertThat(f.removeCount).isEqualTo(0)
    }

    @Test
    fun recycle_resetsViewStateForReuse() {
        val f = fixture()

        dragToRemoveAndRelease(f.item)
        f.adapter.onViewRecycled(f.holder)
        idle(ANIMATION_SETTLE_MS)

        assertThat(f.item.ivIcon.alpha).isEqualTo(1f)
        assertThat(f.item.ivIcon.translationX).isEqualTo(0f)
        assertThat(f.item.ivIcon.translationY).isEqualTo(0f)
        assertThat(f.item.ivDel.alpha).isEqualTo(0f)
    }

    @Test
    fun recycleDuringLongPress_resetsLongPressSoNextMoveDoesNotDrag() {
        val f = fixture()
        touch(f.item, MotionEvent.ACTION_DOWN, 0f)
        f.item.ivIcon.performLongClick()

        f.adapter.onViewRecycled(f.holder)
        // Holder tái dùng cho item khác: chạm mới + kéo KHÔNG được thừa hưởng trạng thái long-press cũ.
        touch(f.item, MotionEvent.ACTION_DOWN, 0f)
        touch(f.item, MotionEvent.ACTION_MOVE, DRAG_FAR_UP)

        assertThat(f.item.ivIcon.translationY).isEqualTo(0f)
    }

    @Test
    fun recycleWithoutAnyGesture_isSafeNoOp() {
        val f = fixture()

        f.adapter.onViewRecycled(f.holder)

        assertThat(f.removeCount).isEqualTo(0)
        assertThat(f.item.ivIcon.alpha).isEqualTo(1f)
    }

    private companion object {
        const val ITEM_SIZE = 150
        const val DRAG_FAR_UP = -1000f
        const val ANIMATION_SETTLE_MS = 500L
    }
}
