package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.databinding.FSurvivabilityBottomSheetBinding
import com.mckimquyen.watermark.export.BrandComplianceScorer
import com.mckimquyen.watermark.export.SurvivabilityProfile
import com.mckimquyen.watermark.ui.base.BaseBindBSDFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IDEA-09: xem watermark sẽ trông ra sao SAU KHI nền tảng mạng xã hội xử lý lại ảnh (crop tỉ lệ,
 * downscale cạnh dài, recompress JPEG) và chấm điểm watermark còn sống sót hay không, kèm gợi ý sửa.
 *
 * Bitmap trả về từ [com.mckimquyen.watermark.ui.MainViewModel.generateSurvivabilityPreview] do
 * fragment này SỞ HỮU — luôn gán vào `ivPreview` TRƯỚC rồi mới recycle bản cũ (tránh view đang vẽ
 * bitmap đã recycle), và recycle nốt bản cuối trong [onDestroyView].
 */
class SurvivabilityBottomSheetFragment : BaseBindBSDFragment<FSurvivabilityBottomSheetBinding>() {

    private var renderJob: Job? = null
    private var currentBitmap: Bitmap? = null
    private var selectedProfile: SurvivabilityProfile.Profile = SurvivabilityProfile.profiles.first()

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FSurvivabilityBottomSheetBinding {
        return FSurvivabilityBottomSheetBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uri = arguments?.getString(ARG_URI)?.let(Uri::parse)
        val index = arguments?.getInt(ARG_INDEX) ?: 0
        if (uri == null) {
            dismissAllowingStateLoss()
            return
        }

        val labels = SurvivabilityProfile.profiles.map { platformLabel(requireContext(), it.platform) }
        binding.atvPlatform.also {
            it.setAdapter(ArrayAdapter(requireContext(), R.layout.simple_dropdown_item_1line, labels))
            it.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.bg_dropdown_popup))
            it.setText(labels.first(), false)
            it.setOnItemClickListener { _, _, position, _ ->
                val profile = SurvivabilityProfile.profiles.getOrNull(position) ?: return@setOnItemClickListener
                selectedProfile = profile
                render(uri, index)
            }
        }
        render(uri, index)
    }

    private fun render(uri: Uri, index: Int) {
        renderJob?.cancel()
        val profile = selectedProfile
        binding.progress.isVisible = true
        binding.tvError.isVisible = false
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            val imageInfo = shareViewModel.imageList.value?.first?.find { it.uri == uri } ?: ImageInfo(uri)
            // Không huỷ giữa chừng tác vụ đã cấp phát bitmap — nếu job bị huỷ (user đổi nền tảng
            // nhanh) thì recycle ngay bản mồ côi ở dưới thay vì để rò rỉ.
            val result = withContext(NonCancellable) {
                shareViewModel.generateSurvivabilityPreview(requireActivity().contentResolver, imageInfo, index, profile)
            }
            if (!isActive || view == null) {
                result?.bitmap?.recycle()
                return@launch
            }
            binding.progress.isVisible = false
            if (result == null) {
                binding.tvError.isVisible = true
                showScore(null)
                return@launch
            }
            val previous = currentBitmap
            currentBitmap = result.bitmap
            binding.ivPreview.setImageBitmap(result.bitmap)
            if (previous != null && previous !== result.bitmap && !previous.isRecycled) {
                previous.recycle()
            }
            showScore(result.score)
        }
    }

    private fun showScore(score: SurvivabilityProfile.Result?) {
        if (score == null) {
            binding.ivScore.isVisible = false
            binding.tvScore.text = ""
            binding.tvSuggestions.isVisible = false
            return
        }
        val context = requireContext()
        val issueText = score.issues.joinToString(", ") { formatIssue(context, it) }
        val (iconRes, tintAttr, label) = when (score.level) {
            BrandComplianceScorer.Level.PASS -> Triple(
                R.drawable.baseline_check_circle_outline_24,
                com.google.android.material.R.attr.colorPrimary,
                context.getString(R.string.survivability_pass)
            )

            BrandComplianceScorer.Level.WARN -> Triple(
                R.drawable.baseline_warning_amber_24,
                com.google.android.material.R.attr.colorTertiary,
                context.getString(R.string.survivability_warn, issueText)
            )

            BrandComplianceScorer.Level.FAIL -> Triple(
                R.drawable.baseline_error_24,
                com.google.android.material.R.attr.colorError,
                context.getString(R.string.survivability_fail, issueText)
            )
        }
        binding.ivScore.isVisible = true
        binding.ivScore.setImageResource(iconRes)
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(tintAttr, typedValue, true)) {
            binding.ivScore.setColorFilter(typedValue.data)
        }
        binding.ivScore.contentDescription = label
        binding.tvScore.text = label

        val suggestions = score.suggestions.joinToString("\n") { "• " + formatSuggestion(context, it) }
        binding.tvSuggestions.isVisible = suggestions.isNotEmpty()
        binding.tvSuggestions.text = suggestions
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        renderJob = null
        binding.ivPreview.setImageDrawable(null)
        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        currentBitmap = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SurvivabilityBottomSheetFragment"
        private const val ARG_URI = "arg_uri"
        private const val ARG_INDEX = "arg_index"

        fun platformLabel(context: Context, platform: SurvivabilityProfile.Platform): String = when (platform) {
            SurvivabilityProfile.Platform.FACEBOOK -> context.getString(R.string.survivability_platform_facebook)
            SurvivabilityProfile.Platform.INSTAGRAM -> context.getString(R.string.survivability_platform_instagram)
            SurvivabilityProfile.Platform.ZALO -> context.getString(R.string.survivability_platform_zalo)
        }

        fun formatIssue(context: Context, issue: SurvivabilityProfile.Issue): String = when (issue) {
            SurvivabilityProfile.Issue.CROPPED_OUT -> context.getString(R.string.survivability_issue_cropped)
            SurvivabilityProfile.Issue.TOO_SMALL_AFTER_DOWNSCALE -> context.getString(R.string.survivability_issue_too_small)
            SurvivabilityProfile.Issue.EDGE -> context.getString(R.string.brand_compliance_issue_edge)
            SurvivabilityProfile.Issue.LOW_OPACITY -> context.getString(R.string.brand_compliance_issue_opacity)
            SurvivabilityProfile.Issue.LOW_CONTRAST -> context.getString(R.string.brand_compliance_issue_contrast)
            SurvivabilityProfile.Issue.SIZE_OUT_OF_RANGE -> context.getString(R.string.brand_compliance_issue_size)
        }

        fun formatSuggestion(context: Context, suggestion: SurvivabilityProfile.Suggestion): String = when (suggestion) {
            SurvivabilityProfile.Suggestion.MOVE_TOWARD_CENTER -> context.getString(R.string.survivability_fix_move_center)
            SurvivabilityProfile.Suggestion.INCREASE_SIZE -> context.getString(R.string.survivability_fix_increase_size)
            SurvivabilityProfile.Suggestion.INCREASE_OPACITY -> context.getString(R.string.survivability_fix_increase_opacity)
            SurvivabilityProfile.Suggestion.INCREASE_CONTRAST -> context.getString(R.string.survivability_fix_increase_contrast)
        }

        fun safetyShow(manager: FragmentManager, uri: Uri, index: Int) {
            try {
                val f = SurvivabilityBottomSheetFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_URI, uri.toString())
                        putInt(ARG_INDEX, index)
                    }
                }
                f.show(manager, TAG)
            } catch (ie: IllegalStateException) {
                ie.printStackTrace()
            }
        }
    }
}
