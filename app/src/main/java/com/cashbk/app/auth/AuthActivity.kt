package com.cashbk.app.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.ActivityAuthBinding
import com.cashbk.app.business.BusinessDetailActivity
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check already logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
            val isLockEnabled = sharedPrefs.getBoolean("app_lock_enabled", false)
            if (isLockEnabled) {
                startActivity(Intent(this, PinLockActivity::class.java))
            } else {
                startActivity(Intent(this, BusinessDetailActivity::class.java))
            }
            finish()
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
