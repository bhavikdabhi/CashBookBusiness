package com.cashbk.app.ui.auth

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.cashbk.app.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Already logged in
        if (auth.currentUser != null) {
            startActivity(Intent(this, com.cashbk.app.ui.business.BusinessDetailActivity::class.java))
            finish()
            return
        }

        // Send OTP
        binding.sendOtpButton.setOnClickListener {
            sendOtp()
        }

        // Verify OTP
        binding.verifyOtpButton.setOnClickListener {
            verifyOtp()
        }
    }

    //------------------------------------------------------
    // SEND OTP
    //------------------------------------------------------
    private fun sendOtp() {
        val phoneNumber = binding.phoneNumberEditText.text.toString().trim()

        if (phoneNumber.isNotEmpty()) {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+91$phoneNumber") // Add country code
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)

        } else {
            Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
        }
    }

    //------------------------------------------------------
    // CALLBACKS
    //------------------------------------------------------
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Toast.makeText(
                applicationContext,
                "Failed to verify phone number: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            storedVerificationId = verificationId
            Toast.makeText(applicationContext, "OTP sent", Toast.LENGTH_SHORT).show()
        }
    }

    //------------------------------------------------------
    // VERIFY OTP
    //------------------------------------------------------
    private fun verifyOtp() {
        val code = binding.otpEditText.text.toString().trim()

        storedVerificationId?.let {
            val credential = PhoneAuthProvider.getCredential(it, code)
            signInWithPhoneAuthCredential(credential)
        } ?: Toast.makeText(this, "Please send OTP first", Toast.LENGTH_SHORT).show()
    }

    //------------------------------------------------------
    // SIGN-IN
    //------------------------------------------------------
    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(applicationContext, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, com.cashbk.app.ui.business.BusinessDetailActivity::class.java))
                    finish()

                } else {
                    Toast.makeText(
                        applicationContext,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
