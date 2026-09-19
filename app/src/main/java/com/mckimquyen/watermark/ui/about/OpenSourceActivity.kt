package com.mckimquyen.watermark.ui.about
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.LOG_TAG
import com.mckimquyen.watermark.databinding.AOpenSourceBinding
import com.mckimquyen.watermark.utils.ktx.applyConsistentIconTint
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink

class OpenSourceActivity : BaseActivity() {

    private val binding by inflate<AOpenSourceBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(LOG_TAG, "OpenSourceActivity onCreate")
        setContentView(binding.root)
        // Insets: push toolbar down below the status bar & camera cutout, and add bottom padding for nav bar
        val baseAppBarHeight = resources.getDimensionPixelSize(
            com.google.android.material.R.dimen.m3_appbar_size_compact
        )
        val baseScrollBottom = (32 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            val navBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            ).bottom
            binding.myToolbar.setPadding(0, statusBarTop, 0, 0)
            binding.myToolbar.layoutParams.height = baseAppBarHeight + statusBarTop
            binding.root.setPadding(0, 0, 0, baseScrollBottom + navBarBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        setupViews()
    }

    private fun setupViews() {
        val iconColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        binding.myToolbar.applyConsistentIconTint(iconColor)
        binding.myToolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.cardColorPicker.setOnClickListener {
            openLink("https://github.com/skydoves/ColorPickerView")
        }

        binding.cardGlideLibrary.setOnClickListener {
            openLink("https://github.com/bumptech/glide")
        }

        binding.cardMaterialComponents.setOnClickListener {
            openLink("https://github.com/material-components/material-components-android")
        }

        binding.cardMaterialCompressor.setOnClickListener {
            openLink("https://github.com/zetbaitsu/Compressor/")
        }
    }
}
