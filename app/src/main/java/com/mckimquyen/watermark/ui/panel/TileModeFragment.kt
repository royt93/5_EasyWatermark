package com.mckimquyen.watermark.ui.panel

import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.FTileModeBinding
import com.mckimquyen.watermark.ui.base.BaseBindFragment
import com.mckimquyen.watermark.ui.dlg.PositionAnchorBottomSheetFragment
import com.mckimquyen.watermark.utils.ktx.commitWithAnimation
import com.mckimquyen.watermark.utils.ktx.titleTextColor

class TileModeFragment : BaseBindFragment<FTileModeBinding>() {

    override fun bindView(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FTileModeBinding {
        return FTileModeBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shareViewModel.selectedImage.observe(viewLifecycleOwner) {
            if (it == null) {
                return@observe
            }
            val checkedId = when (it.tileMode) {
                Shader.TileMode.CLAMP.ordinal -> R.id.btnTileModeDecal
                else -> R.id.btnTileModeRepeat
            }
            binding?.btnPositionAnchor?.visibility =
                if (it.tileMode == Shader.TileMode.CLAMP.ordinal) View.VISIBLE else View.GONE
            binding?.tgTileMode?.clearOnButtonCheckedListeners()
            binding?.tgTileMode?.check(checkedId)
            binding?.tgTileMode?.addOnButtonCheckedListener { _, checkedButtonId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val imageInfo = it
                if (checkedButtonId == R.id.btnTileModeDecal && imageInfo.tileMode == Shader.TileMode.CLAMP.ordinal) {
                    return@addOnButtonCheckedListener
                }
                if (checkedButtonId == R.id.btnTileModeRepeat && imageInfo.tileMode == Shader.TileMode.REPEAT.ordinal) {
                    return@addOnButtonCheckedListener
                }
                when (checkedButtonId) {
                    R.id.btnTileModeDecal -> shareViewModel.updateTileMode(imageInfo, Shader.TileMode.CLAMP)
                    else -> shareViewModel.updateTileMode(imageInfo, Shader.TileMode.REPEAT)
                }
            }
        }
        shareViewModel.colorPalette.observe(this.viewLifecycleOwner) {
            val color = it.titleTextColor(requireContext())
            binding?.btnPositionAnchor?.setTextColor(color)
            binding?.btnPositionAnchor?.iconTint = android.content.res.ColorStateList.valueOf(color)
        }
        binding?.btnTileModeRepeat?.contentDescription = getString(R.string.tile_mode_title_repeat)
        binding?.btnTileModeDecal?.contentDescription = getString(R.string.tile_mode_title_decal)
        binding?.btnPositionAnchor?.contentDescription = getString(R.string.position_anchor_button)
        binding?.btnPositionAnchor?.setOnClickListener {
            PositionAnchorBottomSheetFragment.safetyShow(parentFragmentManager)
        }
    }

    companion object {
        const val TAG = "TileModeFragment"

        fun replaceShow(fa: FragmentActivity, containerId: Int) {
            val f = fa.supportFragmentManager.findFragmentByTag(TAG)
            if (f?.isVisible == true) {
                return
            }
            fa.commitWithAnimation {
                replace(
                    /* containerViewId = */ containerId,
                    /* fragment = */ TileModeFragment(),
                    /* tag = */ TAG
                )
            }
        }
    }
}
