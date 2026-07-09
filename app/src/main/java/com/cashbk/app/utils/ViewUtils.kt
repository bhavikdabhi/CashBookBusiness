package com.cashbk.app.utils

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cashbk.app.R

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

fun AppCompatActivity.setupStatusAndNavBar() {
    // Set status bar color to primary
    window.statusBarColor = ContextCompat.getColor(this, R.color.primary)
    
    // Ensure status bar text is white (not light appearance)
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.isAppearanceLightStatusBars = false
    
    // Hide transient system navigation bar but show on swipe up
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
}
