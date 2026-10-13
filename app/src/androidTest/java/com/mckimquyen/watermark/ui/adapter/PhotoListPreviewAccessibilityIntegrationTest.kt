package com.mckimquyen.watermark.ui.adapter

import android.net.Uri
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.ui.about.OpenSourceActivity
import com.mckimquyen.watermark.ui.widget.onItemClick
import org.junit.Test
import org.junit.runner.RunWith

/** BUG-84: filmstrip rows expose a position label and click action through the real Android accessibility API. */
@RunWith(AndroidJUnit4::class)
class PhotoListPreviewAccessibilityIntegrationTest {

    @Test
    fun filmstripItems_exposeLabelAndClickActionToAccessibilityNodeInfo() {
        ActivityScenario.launch(OpenSourceActivity::class.java).use { scenario ->
            var selectedPosition = RecyclerView.NO_POSITION
            lateinit var recyclerView: RecyclerView
            lateinit var expectedLabel: String
            scenario.onActivity { activity ->
                recyclerView = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
                }
                val adapter = PhotoListPreviewAdapter(activity)
                adapter.submitList(photoItems()) {}
                recyclerView.adapter = adapter
                recyclerView.onItemClick { _, position, _ -> selectedPosition = position }
                activity.addContentView(recyclerView, ViewGroup.LayoutParams(VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
                expectedLabel = activity.getString(R.string.gallery_photo_item_desc, FIRST_POSITION + POSITION_OFFSET)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val item = requireNotNull(recyclerView.getChildAt(FIRST_POSITION))
                val info = AccessibilityNodeInfo.obtain()
                item.onInitializeAccessibilityNodeInfo(info)

                assertThat(info.contentDescription?.toString()).isEqualTo(expectedLabel)
                assertThat(info.isClickable).isTrue()
                assertThat(info.actionList.map { it.id }).contains(AccessibilityNodeInfo.ACTION_CLICK)

                item.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
                info.recycle()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertThat(selectedPosition).isEqualTo(FIRST_POSITION)
        }
    }

    private fun photoItems() = listOf(
        ImageInfo(Uri.parse("content://test/photo-a")),
        ImageInfo(Uri.parse("content://test/photo-b")),
        ImageInfo(Uri.parse("content://test/photo-c"))
    )

    private companion object {
        const val FIRST_POSITION = 0
        const val POSITION_OFFSET = 1
        const val VIEWPORT_WIDTH = 900
        const val VIEWPORT_HEIGHT = 200
    }
}
