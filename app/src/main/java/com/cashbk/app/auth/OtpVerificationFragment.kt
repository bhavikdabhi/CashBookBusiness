package com.cashbk.app.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentOtpVerificationBinding
import com.cashbk.app.business.BusinessDetailActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

import com.cashbk.app.utils.showToast

class OtpVerificationFragment : Fragment() {

    private var _binding: FragmentOtpVerificationBinding? = null
    private val binding get() = _binding!!

    private var name: String = ""
    private var email: String = ""
    private var phone: String = ""

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_EMAIL = "email"
        private const val ARG_PHONE = "phone"

        fun newInstance(name: String, email: String, phone: String): OtpVerificationFragment {
            val fragment = OtpVerificationFragment()
            val args = Bundle()
            args.putString(ARG_NAME, name)
            args.putString(ARG_EMAIL, email)
            args.putString(ARG_PHONE, phone)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            name = it.getString(ARG_NAME, "")
            email = it.getString(ARG_EMAIL, "")
            phone = it.getString(ARG_PHONE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSubtitle.text = "Please enter the 6-digit OTP code sent to\n$email"

        binding.btnVerify.setOnClickListener {
            verifyOtp()
        }

        binding.tvResend.setOnClickListener {
            resendOtp()
        }
    }

    private fun verifyOtp() {
        val otpCode = binding.etOtp.text.toString().trim()

        if (otpCode.isEmpty() || otpCode.length < 6) {
            showToast("Please enter a valid 6-digit OTP code")
            return
        }

        binding.btnVerify.isEnabled = false
        binding.btnVerify.text = "Verifying..."

        AuthManager.registerAndLogin(name, email, otpCode) { success, message ->
            if (success) {
                // Laravel and Firebase Auth are successful!
                // Save user details to Firebase Realtime Database
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    val queryPhone = if (phone.startsWith("+91")) phone else "+91$phone"
                    val userMap = mapOf(
                        "email" to email,
                        "phone" to queryPhone
                    )
                    
                    FirebaseDatabase.getInstance().getReference("users").child(firebaseUser.uid)
                        .setValue(userMap)
                        .addOnCompleteListener { dbTask ->
                            binding.btnVerify.isEnabled = true
                            binding.btnVerify.text = "Verify & Register"
                            
                            if (dbTask.isSuccessful) {
                                showToast("Registration successful")
                                startActivity(Intent(requireContext(), BusinessDetailActivity::class.java))
                                requireActivity().finish()
                            } else {
                                showToast("Saved user, but failed to sync: ${dbTask.exception?.message}")
                                startActivity(Intent(requireContext(), BusinessDetailActivity::class.java))
                                requireActivity().finish()
                            }
                        }
                } else {
                    binding.btnVerify.isEnabled = true
                    binding.btnVerify.text = "Verify & Register"
                    showToast("Firebase user session not found")
                }
            } else {
                binding.btnVerify.isEnabled = true
                binding.btnVerify.text = "Verify & Register"
                showToast("Verification failed: $message")
            }
        }
    }

    private fun resendOtp() {
        showToast("Resending OTP...")
        AuthManager.sendOtp(email) { success, message ->
            if (success) {
                showToast("OTP resent successfully")
            } else {
                showToast("Resend failed: $message")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
