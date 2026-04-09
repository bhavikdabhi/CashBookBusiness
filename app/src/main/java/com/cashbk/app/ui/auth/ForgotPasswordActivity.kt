package com.cashbk.app.ui.auth

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.cashbk.app.R
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_email)
        val btnSend = findViewById<AppCompatButton>(R.id.btn_send)
        val tvBack = findViewById<TextView>(R.id.tv_back)

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Recovery link sent to $email", Toast.LENGTH_LONG).show()
                        finish() // Go back to login
                    } else {
                        Toast.makeText(this, "Failed to send link: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvBack.setOnClickListener {
            finish()
        }
    }
}
