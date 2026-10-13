package com.mckimquyen.watermark.ui.widget

import android.app.Activity
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.ui.adapter.PhotoListPreviewAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** BUG-84: selected-photo filmstrip items expose position labels and standard click semantics to TalkBack. */
@RunWith(RobolectricTestRunner::class)
class PhotoListPreviewAccessibilityWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    @Test
    fun bind_setsLocalizedPhotoPositionDescription() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val adapter = PhotoListPreviewAdapter(activity)
        adapter.submitList(photoItems()) {}
        shadowOf(Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(RecyclerView(activity), 0)
        adapter.onBindViewHolder(holder, SECOND_POSITION)

        assertThat(holder.itemView.contentDescription).isEqualTo(
            activity.getString(R.string.gallery_photo_item_desc, SECOND_POSITION + POSITION_OFFSET)
        )
        assertThat(holder.itemView.isClickable).isTrue()
        assertThat(holder.itemView.isFocusable).isTrue()
    }

    @Test
    fun recycledHolder_clearsOldPhotoDescription() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        val adapter = PhotoListPreviewAdapter(activity)
        adapter.submitList(photoItems()) {}
        shadowOf(Looper.getMainLooper()).idle()
        val holder = adapter.onCreateViewHolder(RecyclerView(activity), 0)
        adapter.onBindViewHolder(holder, SECOND_POSITION)
        assertThat(holder.itemView.contentDescription).isNotNull()

        adapter.onViewRecycled(holder)

        assertThat(holder.itemView.contentDescription).isNull()
    }

    @Test
    fun accessibilityClick_dispatchesToRecyclerViewItemClickListener() {
        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible()
        val activity = activityController.get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val recyclerView = RecyclerView(themedContext).apply {
            layoutManager = LinearLayoutManager(themedContext, RecyclerView.HORIZONTAL, false)
        }
        val adapter = PhotoListPreviewAdapter(themedContext)
        adapter.submitList(photoItems()) {}
        recyclerView.adapter = adapter
        var selectedPosition = RecyclerView.NO_POSITION
        recyclerView.onItemClick { _, position, _ -> selectedPosition = position }
        root.addView(recyclerView, ViewGroup.LayoutParams(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
        shadowOf(Looper.getMainLooper()).idle()
        val item = recyclerView.getChildAt(0)
        assertThat(item).isNotNull()
        assertThat(item.isClickable).isTrue()

        item.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)

        assertThat(selectedPosition).isEqualTo(FIRST_POSITION)
        activityController.pause().stop().destroy()
    }

    private fun photoItems() = listOf(
        ImageInfo(Uri.parse("content://test/photo-a")),
        ImageInfo(Uri.parse("content://test/photo-b")),
        ImageInfo(Uri.parse("content://test/photo-c"))
    )

    private companion object {
        const val FIRST_POSITION = 0
        const val SECOND_POSITION = 1
        const val POSITION_OFFSET = 1
        const val VIEWPORT_WIDTH = 900
        const val VIEWPORT_HEIGHT = 150
    }
}
