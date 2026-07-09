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
import com.cashbk.app.databinding.FragmentRegisterBinding
import com.cashbk.app.business.BusinessDetailActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        setupPasswordToggle()

        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        binding.layoutSignInLink.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_auth_navigation)
            bottomNav.selectedItemId = R.id.nav_login
        }
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val mobile = binding.etMobile.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || mobile.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Sending OTP..."

        AuthManager.sendOtp(email) { success, message ->
            binding.btnRegister.isEnabled = true
            binding.btnRegister.text = "Create Account"
            if (success) {
                val otpFragment = OtpVerificationFragment.newInstance(
                    name = email.substringBefore("@"),
                    email = email,
                    phone = mobile
                )
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, otpFragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                Toast.makeText(requireContext(), "Failed to send OTP: $message", Toast.LENGTH_LONG).show()
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
                        } else {
                            binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
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