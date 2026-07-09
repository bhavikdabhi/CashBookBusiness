package com.cashbk.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CashBookApp : Application() {
    companion object {
        lateinit var instance: CashBookApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Load and apply saved theme preference
        val sharedPrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("app_theme", "system") ?: "system"
        val nightMode = when (savedTheme) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}