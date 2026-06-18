package com.cashbk.app.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.databinding.ActivityPinLockBinding
import com.cashbk.app.business.BusinessDetailActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executor

class PinLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinLockBinding
    private var enteredPin = StringBuilder()
    private lateinit var savedPin: String
    private var isBiometricsEnabled = false

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
        savedPin = sharedPrefs.getString("app_lock_pin", "") ?: ""
        isBiometricsEnabled = sharedPrefs.getBoolean("app_lock_biometrics", false)

        if (savedPin.isEmpty()) {
            navigateToMain()
            return
        }

        setupKeypad()
        setupBackupOptions()

        if (isBiometricsEnabled) {
            binding.btnBiometric.visibility = View.VISIBLE
            setupBiometricPrompt()
            // Automatically trigger biometric unlock on launch
            triggerBiometricPrompt()
        } else {
            binding.btnBiometric.visibility = View.INVISIBLE
        }
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                if (enteredPin.length < 4) {
                    enteredPin.append(button.text)
                    updatePinDots()
                    if (enteredPin.length == 4) {
                        verifyPin()
                    }
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteCharAt(enteredPin.length - 1)
                updatePinDots()
            }
        }

        binding.btnBiometric.setOnClickListener {
            triggerBiometricPrompt()
        }
    }

    private fun updatePinDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        for (i in 0 until 4) {
            if (i < enteredPin.length) {
                dots[i].setBackgroundResource(R.drawable.bg_calendar_dot)
                dots[i].backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_color)
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_calendar_dot)
                dots[i].backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
            }
        }
    }

    private fun verifyPin() {
        if (enteredPin.toString() == savedPin) {
            navigateToMain()
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            enteredPin.clear()
            binding.tvLockStatus.text = "Incorrect PIN, try again"
            binding.tvLockStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
            updatePinDots()
        }
    }

    private fun setupBiometricPrompt() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    navigateToMain()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Biometric failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Unlock")
            .setSubtitle("Unlock CashBook Business using biometric credentials")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    private fun triggerBiometricPrompt() {
        if (isBiometricsEnabled) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupBackupOptions() {
        binding.btnSwitchUser.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(this, signInOptions).signOut().addOnCompleteListener {
                val intent = Intent(this, AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, BusinessDetailActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
