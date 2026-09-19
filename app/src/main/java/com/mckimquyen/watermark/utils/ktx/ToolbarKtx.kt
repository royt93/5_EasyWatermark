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

    // 4. Samsung OneUI & Android Framework ActionMenuView Child Traversal
    fun tintChildViews(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is ActionMenuView -> tintChildViews(child)
                is ViewGroup -> tintChildViews(child)
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
                    child.imageTintList = ColorStateList.valueOf(iconColor)
                    child.imageTintMode = PorterDuff.Mode.SRC_IN
                    child.drawable?.let {
                        DrawableCompat.setTint(it.mutate(), iconColor)
                        DrawableCompat.setTintMode(it, PorterDuff.Mode.SRC_IN)
                    }
                }
            }
        }
    }

    tintChildViews(this)

    // 5. Post to guarantee tinting after layout passes or dynamic menu view recreation
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
        tintChildViews(this)
    }
}
