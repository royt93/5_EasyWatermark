package com.mckimquyen.watermark.ui.base

import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.watermark.ui.widget.ItemClickSupportViewHolder

open class BaseViewHolder(itemView: View) :
    RecyclerView.ViewHolder(itemView),
    ItemClickSupportViewHolder {

    val translationX: SpringAnimation by lazy {
        SpringAnimation(itemView, SpringAnimation.TRANSLATION_X)
            .setSpring(
                SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_LOW)
            )
    }
}
