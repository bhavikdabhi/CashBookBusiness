package com.cashbk.app.splash

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.cashbk.app.R
import com.cashbk.app.auth.AuthActivity
import com.cashbk.app.auth.PinLockActivity
import com.cashbk.app.business.BusinessDetailActivity
import com.cashbk.app.databinding.ActivitySplashBinding
import com.cashbk.app.utils.isDarkMode
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make window layout immersive & draw under status/navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Adjust status bar icon appearance based on active theme (dark/light)
        val isDark = isDarkMode()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startSplashAnimations()
    }

    private fun startSplashAnimations() {
        // Entrance Fade-in for the main brand content
        binding.splashContent.visibility = View.VISIBLE
        val logoFadeIn = AnimationUtils.loadAnimation(this, R.anim.splash_fade_in)
        binding.splashContent.startAnimation(logoFadeIn)

        // Wait for the animation to showcase (1.5 seconds) then navigate
        binding.splashContent.postDelayed({
            checkAuthAndNavigate()
        }, 1500)
    }

    private fun checkAuthAndNavigate() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val intent = if (currentUser != null) {
            val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
            val isLockEnabled = sharedPrefs.getBoolean("app_lock_enabled", false)
            if (isLockEnabled) {
                Intent(this, PinLockActivity::class.java)
            } else {
                Intent(this, BusinessDetailActivity::class.java)
            }
        } else {
            Intent(this, AuthActivity::class.java)
        }

        startActivity(intent)
        
        // Premium cross-fade transition
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        finish()
    }
}
