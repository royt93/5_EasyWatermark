package com.mckimquyen.watermark.utils.ktx

import android.view.View
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_NO_BOUNCY
import com.mckimquyen.watermark.ui.widget.utils.ViewAnimation

/**
 * REVIEW-13/14: cộng dồn [base] (padding gốc đọc TRƯỚC khi gắn `setOnApplyWindowInsetsListener`,
 * vd khai trong XML) với [inset] hệ thống (status/navigation bar...) rồi set vào paddingBottom của
 * view này, giữ nguyên left/top/right — thay cho gọi `setPadding(..., inset)` trực tiếp, vốn ghi
 * đè mất [base] mỗi lần insets được áp lại. Đã lặp lại NGUYÊN VẸN lỗi này 3 lần độc lập
 * (`BatchHistoryActivity`, `WatermarkProfileActivity`, `RecipientManagementActivity` — lần cuối dù
 * code có comment nhắc "bài học FEAT-06" vẫn chép nhầm pattern cũ) trước khi gộp vào đây.
 *
 * LƯU Ý: [base] phải đọc 1 LẦN từ bên ngoài listener (trước khi gắn), KHÔNG đọc `paddingBottom`
 * hiện tại bên trong listener — listener có thể bị gọi lại nhiều lần (xoay màn hình, bàn phím ẩn/
 * hiện...), đọc lại `paddingBottom` lúc đó đã CHỨA inset lần trước, cộng dồn sai (tăng dần mỗi lần).
 */
fun View.setBottomPaddingWithInset(base: Int, inset: Int) {
    setPadding(paddingLeft, paddingTop, paddingRight, base + inset)
}

fun View.appearAnimation(
    dampingRatio: Float = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
    stiffness: Float = SpringForce.STIFFNESS_LOW
): SpringAnimation {
    return SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f).apply {
        spring = SpringForce()
            .setFinalPosition(0f)
            .setDampingRatio(dampingRatio)
            .setStiffness(stiffness)
    }
}

// fun View.disappearAnimation(toPos: Float = 10f): SpringAnimation {
//    return SpringAnimation(this, SpringAnimation.TRANSLATION_Y, toPos).apply {
//        spring = SpringForce()
//            .setFinalPosition(toPos)
//            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
//            .setStiffness(SpringForce.STIFFNESS_LOW)
//        addUpdateListener { _, _, _ ->
//            this@disappearAnimation.isVisible = true
//        }
//        addEndListener { _, _, _, _ ->
//            this@disappearAnimation.isVisible = false
//        }
//    }
// }

fun View.appear(
    fromX: Float = 0f,
    fromY: Float = 10.dp.toFloat(),
    fromAlpha: Float = 0.5f,
    duration: Long = 200
) {
    this.translationY = fromY
    this.translationX = fromX
    this.alpha = fromAlpha
    this.scaleX = 0.75f
    this.scaleY = 0.75f
    this.animate()
        .translationY(0f)
        .translationX(0f)
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setDuration(duration)
        .withStartAction {
            this.isVisible = true
        }
}

fun View.disappear(
    toX: Float = 0f,
    toY: Float = 10.dp.toFloat(),
    toAlpha: Float = 0.5f,
    duration: Long = 200
) {
    // BUG-AUDIT-2026-09-29-R6: translationX/Y bị đảo ngược so với tham số — toX phải đi vào
    // translationX, toY vào translationY (tên hàm/tham số mô tả rõ). Với default (toX=0f,
    // toY=10dp), lỗi cũ làm view trượt NGANG 10dp thay vì trượt XUỐNG khi fade-out.
    this.animate()
        .translationX(toX)
        .translationY(toY)
        .alpha(toAlpha)
        .setDuration(duration)
        .withStartAction {
            this.isVisible = true
        }
        .withEndAction {
            this.isVisible = false
        }
}

// fun generateAppearAnimationList(
//    vararg views: View,
// ): List<SpringAnimation> {
//    return views.map {
//        it.appearAnimation()
//    }
// }

fun generateAppearAnimationList(
    views: Iterable<View>
): List<ViewAnimation> {
    return views.mapIndexed { index, view ->
        ViewAnimation(view, view.appearAnimation(dampingRatio = DAMPING_RATIO_NO_BOUNCY)).apply {
            setListener {
                applyBeforeStart { view, _ ->
                    view.translationY = 20.dp.toFloat() + index * 35.dp
                    view.alpha = 0.1f
                    view.animate()
                        .alpha(1f)
                        .withStartAction {
                            view.isVisible = true
                        }
                        .setDuration(200L)
                        .start()
                }
            }
        }
    }
}

fun generateDisappearAnimationList(
    views: Iterable<View>
): List<ViewAnimation> {
    return views.mapIndexed { _, view ->
        ViewAnimation(view, null).apply {
            setListener {
                applyBeforeStart { view, _ ->
                    view.translationY = 0f
                    view.animate()
                        .alpha(0f)
                        .setDuration(200L)
                        .withEndAction {
                            view.visibility = android.view.View.GONE
                        }
                        .start()
                }
            }
        }
    }
}
