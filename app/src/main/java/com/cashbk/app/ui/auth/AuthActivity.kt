package com.cashbk.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.ActivityAuthBinding
import com.cashbk.app.ui.business.BusinessDetailActivity
import com.google.firebase.auth.FirebaseAuth

import android.widget.Toast

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check already logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
            if (isAppLockEnabled) {
                val authManager = com.ext.biometric_auth.BiometricAuthManager(this)
                val callback = object : com.ext.biometric_auth.BiometricCallback {
                    override fun onResult(result: com.ext.biometric_auth.BiometricResult) {
                        when (result) {
                            is com.ext.biometric_auth.BiometricResult.AuthenticationSucceeded,
                            is com.ext.biometric_auth.BiometricResult.PinAuthenticationSucceeded -> {
                                startActivity(Intent(this@AuthActivity, BusinessDetailActivity::class.java))
                                finish()
                            }
                            is com.ext.biometric_auth.BiometricResult.AuthenticationCancelled -> {
                                finish()
                            }
                            is com.ext.biometric_auth.BiometricResult.LockoutActive -> {
                                Toast.makeText(this@AuthActivity, "Too many failed attempts. Try again later.", Toast.LENGTH_LONG).show()
                                finish()
                            }
                            is com.ext.biometric_auth.BiometricResult.AuthenticationFailed -> {
                                Toast.makeText(this@AuthActivity, "Authentication failed: ${result.reason}", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // retry
                            }
                        }
                    }
                }
                authManager.authenticate(this, callback)
            } else {
                startActivity(Intent(this, BusinessDetailActivity::class.java))
                finish()
            }
            return
        }

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(LoginFragment())
            binding.bottomAuthNavigation.selectedItemId = R.id.nav_login
        }

        binding.bottomAuthNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_login -> {
                    loadFragment(LoginFragment())
                    true
                }
                R.id.nav_register -> {
                    loadFragment(RegisterFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
