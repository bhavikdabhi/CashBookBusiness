package com.cashbk.app.utils

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation

fun View.startPulseAnimation() {
    val anim = AlphaAnimation(0.4f, 1.0f).apply {
        duration = 800
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
    }
    this.startAnimation(anim)
}

fun View.stopPulseAnimation() {
    this.clearAnimation()
}
