package com.mckimquyen.watermark.ui.panel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FTextContentDisplayBinding
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import com.mckimquyen.watermark.utils.ktx.titleTextColor

class TextContentDisplayFragment : BaseBindFragment<FTextContentDisplayBinding>() {
    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FTextContentDisplayBinding {
        return FTextContentDisplayBinding.inflate(layoutInflater)
    }

//    private val paintStyleAdapter by lazy {
//        TextPaintStyleAdapter(
//            TextPaintStyleAdapter.obtainDefaultPaintStyleList(
//                requireContext()
//            ),
//            shareViewModel.waterMark.value?.textStyle
//        ) { _, paintStyle ->
//            shareViewModel.updateTextStyle(paintStyle)
//            typefaceAdapter.updateTextStyle(paintStyle)
//        }
//    }

//    private val typefaceAdapter by lazy {
//        TextTypefaceAdapter(
//            TextTypefaceAdapter.obtainDefaultTypefaceList(
//                requireContext()
//            ),
//            shareViewModel.waterMark.value?.textTypeface
//        ) { _, typeface ->
//            shareViewModel.updateTextTypeface(typeface)
//        }
//    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.root?.apply {
            setOnClickListener {
                shareViewModel.goEditDialog()
            }
        }

        shareViewModel.colorPalette.observe(this.viewLifecycleOwner) {
            val color = it.titleTextColor(requireContext())
            binding?.etWaterText?.setTextColor(color)
        }

        shareViewModel.waterMark.observe(this.viewLifecycleOwner) {
            val string = (it?.text ?: getString(R.string.tips_input_text_can_not_be_empty))
            binding?.etWaterText?.text = string
        }
    }

    companion object {
        const val TAG = "TextContentDisplayFragment"

        fun remove(activity: FragmentActivity) {
            activity.supportFragmentManager.commitNow(true) {
                activity.supportFragmentManager.findFragmentByTag(TAG)?.let { remove(it) }
            }
        }

        fun replaceShow(fa: FragmentActivity, containerId: Int): Boolean {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return true
            }
            fa.commitWithAnimation {
                replace(
                    containerId,
                    TextContentDisplayFragment(),
                    TAG
                )
            }
            return false
        }
    }
}
