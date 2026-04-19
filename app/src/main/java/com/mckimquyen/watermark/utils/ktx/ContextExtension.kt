package com.mckimquyen.watermark.utils.ktx

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun Activity.isStoragePermissionGrated(): Boolean {
    val readGranted = ContextCompat.checkSelfPermission(
        this,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    ) == PackageManager.PERMISSION_GRANTED

    val writeGranted =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    return readGranted && writeGranted
}

fun Activity.preCheckStoragePermission(block: () -> Unit) {
    if (isStoragePermissionGrated()) {
        block.invoke()
    } else {
        requestPermission()
    }
}

fun Activity.requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            MainActivity.REQ_CODE_REQ_WRITE_PERMISSION
        )
    } else {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            MainActivity.REQ_CODE_REQ_WRITE_PERMISSION
        )
    }
}


inline fun ViewModel.launch(crossinline action: suspend CoroutineScope.() -> Unit): Job {
    return viewModelScope.launch {
        action()
    }
}

val Context.colorPrimary: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorPrimary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_primary80
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorPrimary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_primary40
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_primary)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_primary)
            }
        }
    }

val Context.colorOnPrimary: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnPrimary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_primary20
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnPrimary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_primary100
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_onPrimary)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_onPrimary)
            }
        }
    }

//val Context.colorPrimaryContainer: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorPrimaryContainer, defaultColor = R.color.material_dynamic_primary20)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorPrimaryContainer, defaultColor = R.color.material_dynamic_primary90)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_primaryContainer)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_primaryContainer)
//            }
//        }
//    }

//val Context.colorOnPrimaryContainer: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOnPrimaryContainer, defaultColor = R.color.material_dynamic_primary90)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnPrimaryContainer, defaultColor = R.color.material_dynamic_primary10)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_onPrimaryContainer)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_onPrimaryContainer)
//            }
//        }
//    }

val Context.colorSecondary: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSecondary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_secondary80
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSecondary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_secondary40
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_secondary)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_secondary)
            }
        }
    }

//val Context.colorOnSecondary: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOnSecondary, defaultColor = R.color.material_dynamic_secondary20)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnSecondary, defaultColor = R.color.material_dynamic_secondary100)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_secondary)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_secondary)
//            }
//        }
//    }

val Context.colorSecondaryContainer: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSecondaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_secondary30
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSecondaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_secondary90
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_secondary)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_secondary)
            }
        }
    }

//val Context.colorOnSecondaryContainer: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOnSecondaryContainer, defaultColor = R.color.material_dynamic_secondary90)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnSecondaryContainer, defaultColor = R.color.material_dynamic_secondary10)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_secondary)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_secondary)
//            }
//        }
//    }

/**
 * 用于对比重音，这些重音可用于平衡主色和次色，或提高对元素(如输入字段)的注意。
 */
val Context.colorTertiary: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorTertiary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary80
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorTertiary,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary40
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_tertiary)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_tertiary)
            }
        }
    }

//val Context.colorOnTertiary: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOnTertiary, defaultColor = R.color.material_dynamic_tertiary20)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnTertiary, defaultColor = R.color.material_dynamic_tertiary100)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_onTertiary)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_onTertiary)
//            }
//        }
//    }

val Context.colorTertiaryContainer: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorTertiaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary30
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorTertiaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary90
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_tertiaryContainer)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_tertiaryContainer)
            }
        }
    }

val Context.colorOnTertiaryContainer: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnTertiaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary90
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnTertiaryContainer,
                    defaultColor = com.google.android.material.R.color.material_dynamic_tertiary10
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_onTertiaryContainer)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_onTertiaryContainer)
            }
        }
    }

val Context.colorError: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorError,
                    defaultColor = com.google.android.material.R.color.design_default_color_error
                )
            }

            isNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_error)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_error)
            }
        }
    }

//val Context.colorOnError: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnError, defaultColor = R.color.design_default_color_on_error)
//            }
//
//            isNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_onError)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_onError)
//            }
//        }
//    }

//val Context.colorErrorContainer: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorErrorContainer, defaultColor = R.color.md_theme_dark_errorContainer)
//            }
//
//            isNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_errorContainer)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_errorContainer)
//            }
//        }
//    }

//val Context.colorOnErrorContainer: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnErrorContainer, defaultColor = R.color.md_theme_dark_onErrorContainer)
//            }
//
//            isNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_onErrorContainer)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_onErrorContainer)
//            }
//        }
//    }

val Context.colorBackground: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.backgroundColor,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral10
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.backgroundColor,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral99
                )
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_background)
            }
        }
    }

//val Context.colorOnBackground: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOnBackground, defaultColor = R.color.material_dynamic_neutral90)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOnBackground, defaultColor = R.color.material_dynamic_neutral10)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_onBackground)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_onBackground)
//            }
//        }
//    }

val Context.colorSurface: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSurface,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral10
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSurface,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral99
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_surface)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_surface)
            }
        }
    }

val Context.colorOnSurface: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnSurface,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral80
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnSurface,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral10
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_onSurface)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_onSurface)
            }
        }
    }

val Context.colorSurfaceVariant: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral30
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral90
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_surfaceVariant)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_surfaceVariant)
            }
        }
    }

val Context.colorOnSurfaceVariant: Int
    get() {
        return when {
            CMonet.isDynamicColorAvailable() && isNight() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral80
                )
            }

            CMonet.isDynamicColorAvailable() -> {
                getColorFromAttr(
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    defaultColor = com.google.android.material.R.color.material_dynamic_neutral30
                )
            }

            isNight() || !supportNight() -> {
                ContextCompat.getColor(this, R.color.md_theme_dark_onSurfaceVariant)
            }

            else -> {
                ContextCompat.getColor(this, R.color.md_theme_light_onSurfaceVariant)
            }
        }
    }

//val Context.colorOutline: Int
//    get() {
//        return when {
//            CMonet.isDynamicColorAvailable() && isNight() -> {
//                getColorFromAttr(R.attr.colorOutline, defaultColor = R.color.material_dynamic_neutral60)
//            }
//
//            CMonet.isDynamicColorAvailable() -> {
//                getColorFromAttr(R.attr.colorOutline, defaultColor = R.color.material_dynamic_neutral50)
//            }
//
//            isNight() || !supportNight() -> {
//                ContextCompat.getColor(this, R.color.md_theme_dark_outline)
//            }
//
//            else -> {
//                ContextCompat.getColor(this, R.color.md_theme_light_outline)
//            }
//        }
//    }


fun Context.isNight(): Boolean {
    return when (this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true // Night mode is not active, we're using the light theme
        Configuration.UI_MODE_NIGHT_NO -> false // Night mode is active, we're using dark theme
        else -> false
    }
}

fun supportNight(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}

fun supportDynamicColor(): Boolean {
    return CMonet.isDynamicColorAvailable()
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
    defaultColor: Int = 0,
): Int {
    return try {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        typedValue.data
    } catch (e: Exception) {
        ContextCompat.getColor(this, defaultColor)
    }
}

fun Context.toast(msg: String?) {
    if (msg.isNullOrBlank()) return
    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}