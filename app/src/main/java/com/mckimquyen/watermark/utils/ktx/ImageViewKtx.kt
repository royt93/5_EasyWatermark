package com.mckimquyen.watermark.utils.ktx

import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions

fun ImageView.loadSmall(uri: Uri, placeholder: Int = 0) {
    val options = RequestOptions()
        .override(300, 300) // Increased for better quality
        .centerCrop()
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // Let Glide decide
        .downsample(DownsampleStrategy.CENTER_OUTSIDE) // Better quality
        .skipMemoryCache(false) // Use memory cache
        .dontAnimate() // Skip animation for performance
        .encodeQuality(90) // Higher quality encoding

    Glide.with(this)
        .asBitmap()
        .apply(options)
        .thumbnail(0.25f) // Larger thumbnail for smoother loading
        .placeholder(placeholder)
        .load(uri)
        .into(this)
}
