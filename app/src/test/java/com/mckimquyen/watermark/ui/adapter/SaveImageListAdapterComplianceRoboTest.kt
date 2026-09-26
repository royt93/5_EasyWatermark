package com.mckimquyen.watermark.ui.adapter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.export.BatchExportEngine
import com.mckimquyen.watermark.export.BrandComplianceScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-15: widget test cho badge Brand Compliance trên mỗi card preview trong [SaveImageListAdapter].
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageListAdapterComplianceRoboTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun createBoundHolder(
        adapter: SaveImageListAdapter,
        position: Int
    ): SaveImageListAdapter.ImageHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    @Test
    fun showCompliance_pass_showsVisibleBadgeWithCheckIcon() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        val passResult = BrandComplianceScorer.ComplianceResult(BrandComplianceScorer.Level.PASS, emptyList())
        holder.showCompliance(passResult)

        assertThat(holder.ivCompliance.isVisible).isTrue()
        assertThat(holder.ivCompliance.contentDescription.toString())
            .isEqualTo(context.getString(R.string.brand_compliance_pass))
    }

    @Test
    fun showCompliance_warn_showsVisibleBadgeWithWarningDescription() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        val warnResult = BrandComplianceScorer.ComplianceResult(
            BrandComplianceScorer.Level.WARN,
            listOf(BrandComplianceScorer.Issue.LOW_OPACITY)
        )
        holder.showCompliance(warnResult)

        assertThat(holder.ivCompliance.isVisible).isTrue()
        assertThat(holder.ivCompliance.contentDescription.toString())
            .contains(context.getString(R.string.brand_compliance_issue_opacity))
    }

    @Test
    fun showCompliance_fail_showsVisibleBadgeWithFailDescription() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        val failResult = BrandComplianceScorer.ComplianceResult(
            BrandComplianceScorer.Level.FAIL,
            listOf(BrandComplianceScorer.Issue.EDGE)
        )
        holder.showCompliance(failResult)

        assertThat(holder.ivCompliance.isVisible).isTrue()
        assertThat(holder.ivCompliance.contentDescription.toString())
            .contains(context.getString(R.string.brand_compliance_issue_edge))
    }

    @Test
    fun showCompliance_null_hidesBadge() {
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ -> null },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        val info = ImageInfo(Uri.parse("content://media/a"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        holder.showCompliance(null)

        assertThat(holder.ivCompliance.isVisible).isFalse()
    }

    @Test
    fun previewSuccess_appliesCompliance_andRebindKeepsBadge() {
        val dummyBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val compliance = BrandComplianceScorer.ComplianceResult(
            BrandComplianceScorer.Level.WARN,
            listOf(BrandComplianceScorer.Issue.EDGE)
        )
        val adapter = SaveImageListAdapter(
            context = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            generatePreview = { _, _ ->
                BatchExportEngine.PreviewResult.Success(dummyBitmap, 100, 100, compliance)
            },
            estimateOutput = { w, h -> (w to h) to 0L }
        )
        val info = ImageInfo(Uri.parse("content://media/cached_preview"))
        adapter.submitList(listOf(info))

        val holder = createBoundHolder(adapter, 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(holder.ivCompliance.isVisible).isTrue()

        // Rebind qua payload (ví dụ state change) không render lại preview nhưng vẫn giữ badge từ cache
        adapter.onBindViewHolder(holder, 0, mutableListOf("state"))
        assertThat(holder.ivCompliance.isVisible).isTrue()
    }
}
