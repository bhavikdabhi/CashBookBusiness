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
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    
    val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.isAppearanceLightStatusBars = !isDark
    windowInsetsController.isAppearanceLightNavigationBars = !isDark
    
    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
}
