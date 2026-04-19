package com.mckimquyen.watermark.ui.about

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mckimquyen.watermark.BaseActivity
import com.mckimquyen.watermark.databinding.AOpenSourceBinding
import com.mckimquyen.watermark.utils.ktx.inflate
import com.mckimquyen.watermark.utils.ktx.openLink

class OpenSourceActivity : BaseActivity() {

    private val binding by inflate<AOpenSourceBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("roy93~", "OpenSourceActivity onCreate")
        setContentView(binding.root)
        // Insets: push toolbar down below the status bar, and add bottom padding for nav bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.myToolbar) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            Log.d("roy93~", "OpenSourceActivity insets — statusBarTop=$top")
            view.setPadding(0, top, 0, 0)
            view.layoutParams.height = resources.getDimensionPixelSize(
                com.google.android.material.R.dimen.m3_appbar_size_compact
            ) + top
            insets
        }
        setupViews()
    }

    private fun setupViews() {
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
