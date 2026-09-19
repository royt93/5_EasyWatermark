package com.mckimquyen.watermark.utils.ktx

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.forEach
import com.google.android.material.appbar.MaterialToolbar

/**
 * Universal Action Bar & Toolbar icon tint engine.
 * Ensures 100% consistent icon colors across all Android devices, including Samsung OneUI
 * which often overrides ActionMenuView, compound drawables, and overflow icons.
 */
fun Toolbar.applyConsistentIconTint(@ColorInt iconColor: Int) {
    // 1. Navigation icon
    navigationIcon?.let {
        DrawableCompat.setTint(it.mutate(), iconColor)
        DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
    }
    if (this is MaterialToolbar) {
        setNavigationIconTint(iconColor)
    }

    // 2. Overflow icon (3 dots)
    overflowIcon?.let {
        DrawableCompat.setTint(it.mutate(), iconColor)
        DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
    }

    // 3. Menu items
    menu?.forEach { menuItem ->
        menuItem.icon?.let {
            DrawableCompat.setTint(it.mutate(), iconColor)
            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menuItem.iconTintList = ColorStateList.valueOf(iconColor)
            menuItem.iconTintMode = PorterDuff.Mode.SRC_IN
        }
    }

    // 4. Ensure toolbar logo (image resource watermark) is NEVER tinted
    logo?.let {
        DrawableCompat.setTintList(it, null)
    }

    // 5. Samsung OneUI & Android Framework ActionMenuView Child Traversal
    // Only traverse ActionMenuView to tint action buttons and overflow menu.
    // Never tint Toolbar direct children outside ActionMenuView (such as the watermark logo image).
    fun tintActionMenuView(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is ActionMenuView -> tintActionMenuView(child)
                is ViewGroup -> tintActionMenuView(child)
                is TextView -> {
                    // ActionMenuItemView on Samsung and Android is a TextView
                    child.compoundDrawables.forEach { d ->
                        d?.let {
                            DrawableCompat.setTint(it.mutate(), iconColor)
                            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        child.compoundDrawablesRelative.forEach { d ->
                            d?.let {
                                DrawableCompat.setTint(it.mutate(), iconColor)
                                DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
                            }
                        }
                    }
                }
                is ImageView -> {
                    // Only tint overflow button inside ActionMenuView, never the logo
                    if (child.drawable !== logo) {
                        child.imageTintList = ColorStateList.valueOf(iconColor)
                        child.imageTintMode = PorterDuff.Mode.SRC_IN
                        child.drawable?.let {
                            DrawableCompat.setTint(it.mutate(), iconColor)
                            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
                        }
                    } else {
                        child.imageTintList = null
                    }
                }
            }
        }
    }

    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is ActionMenuView) {
            tintActionMenuView(child)
        } else if (child is ImageView && child.drawable === logo) {
            // Explicitly clear tint on logo view
            child.imageTintList = null
        }
    }

    // 6. Post to guarantee tinting after layout passes or dynamic menu view recreation
    post {
        navigationIcon?.let {
            DrawableCompat.setTint(it.mutate(), iconColor)
            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
        }
        overflowIcon?.let {
            DrawableCompat.setTint(it.mutate(), iconColor)
            DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
        }
        menu?.forEach { menuItem ->
            menuItem.icon?.let {
                DrawableCompat.setTint(it.mutate(), iconColor)
                DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                menuItem.iconTintList = ColorStateList.valueOf(iconColor)
                menuItem.iconTintMode = PorterDuff.Mode.SRC_IN
            }
        }
        logo?.let {
            DrawableCompat.setTintList(it, null)
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is ActionMenuView) {
                tintActionMenuView(child)
            } else if (child is ImageView && child.drawable === logo) {
                child.imageTintList = null
            }
        }
    }
}
