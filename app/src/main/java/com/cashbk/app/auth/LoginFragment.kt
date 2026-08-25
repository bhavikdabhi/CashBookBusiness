package com.cashbk.app.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentLoginBinding
import com.cashbk.app.business.BusinessDetailActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

import com.cashbk.app.utils.showToast

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        setupPasswordToggle()

        binding.btnSignIn.setOnClickListener {
            performLogin()
        }

        binding.tvSignUp.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_auth_navigation)
            bottomNav.selectedItemId = R.id.nav_register
        }

        binding.btnGoogleSignIn.setOnClickListener {
            showToast("Google Sign In coming soon")
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(requireContext(), ForgotPasswordActivity::class.java))
        }
    }

    private fun performLogin() {
        val email = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showToast("Please enter all fields")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    showToast("Login successful")
                    startActivity(Intent(requireContext(), BusinessDetailActivity::class.java))
                    requireActivity().finish()
                } else {
                    showToast("Login failed: ${task.exception?.message}")
                }
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPasswordToggle() {
        binding.etPassword.setOnTouchListener { _, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = binding.etPassword.compoundDrawables[DRAWABLE_RIGHT]
                if (drawable != null) {
                    val clickAreaStart = binding.etPassword.width - binding.etPassword.paddingEnd - drawable.bounds.width() - 50
                    if (event.x >= clickAreaStart) {
                        val isPasswordVisible = binding.etPassword.transformationMethod is HideReturnsTransformationMethod
                        if (isPasswordVisible) {
                            binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                            binding.etPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_auth_lock, 0, R.drawable.ic_eye, 0)
                        } else {
                            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                            binding.etPassword.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_auth_lock, 0, R.drawable.ic_eye_off, 0)
                        }
                        binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
