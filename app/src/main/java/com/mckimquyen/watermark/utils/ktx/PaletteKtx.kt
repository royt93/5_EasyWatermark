package com.mckimquyen.watermark.utils.ktx

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.google.android.material.color.MaterialColors
import com.mckimquyen.watermark.R

fun Palette?.bgColor(context: Context): Int {
    if (this == null) {
        return context.colorSurfaceVariant
    }

    val platteColor = (this.darkMutedSwatch?.rgb ?: this.mutedSwatch?.rgb) ?: context.colorSurfaceVariant
    val harmonizedColor = MaterialColors.harmonize(
        platteColor,
        ContextCompat.getColor(context, R.color.md_theme_dark_background)
    )
//    Log.i("Palette", "platteColor = $platteColor, finalColor $harmonizedColor")
    return harmonizedColor
}

fun Palette?.titleTextColor(context: Context): Int {
    val surfaceColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSurfaceContainerHigh,
        context.colorSurfaceVariant
    )
    val defaultOnSurface = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSurface,
        context.colorOnSurfaceVariant
    )
    if (this == null) {
        return defaultOnSurface
    }
    val candidate = (this.darkMutedSwatch?.titleTextColor ?: this.mutedSwatch?.titleTextColor)
    if (candidate != null && androidx.core.graphics.ColorUtils.calculateContrast(candidate, surfaceColor) >= 4.5) {
        return candidate
    }
    return defaultOnSurface
}
