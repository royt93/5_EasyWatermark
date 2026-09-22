package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-18 AC2: bấm vào 1 card (khác nút skip) mở màn so sánh trước/sau cho ĐÚNG ảnh + vị trí đó.
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterItemClickRoboTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun createBoundHolder(adapter: SaveImageListAdapter, position: Int): SaveImageListAdapter.ImageHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    @Test
    fun tapCard_invokesOnItemClick_withCorrectItemAndPosition() {
        var clicked: Pair<ImageInfo, Int>? = null
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L },
            onItemClick = { info, index -> clicked = info to index }
        )
        val infoA = ImageInfo(Uri.parse("content://media/a"))
        val infoB = ImageInfo(Uri.parse("content://media/b"))
        adapter.submitList(listOf(infoA, infoB))

        val holder = createBoundHolder(adapter, 1)
        holder.itemView.performClick()

        assertThat(clicked).isEqualTo(infoB to 1)
    }

    @Test
    fun tapSkipToggle_doesNotAlsoTriggerOnItemClick() {
        var clickCount = 0
        var skipCount = 0
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L },
            onToggleSkip = { skipCount++ },
            onItemClick = { _, _ -> clickCount++ }
        )
        adapter.submitList(listOf(ImageInfo(Uri.parse("content://media/a"))))

        val holder = createBoundHolder(adapter, 0)
        holder.ivSkipToggle.performClick()

        assertThat(skipCount).isEqualTo(1)
        assertThat(clickCount).isEqualTo(0)
    }
}
