package com.cashbk.app.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.databinding.DialogPinSetupBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PinSetupBottomSheet(
    private val onComplete: (Boolean) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogPinSetupBinding? = null
    private val binding get() = _binding!!

    private var tempPin = ""
    private var isConfirmPhase = false
    private val enteredDigits = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPinSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeypad()
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                if (enteredDigits.length < 4) {
                    enteredDigits.append(button.text)
                    updateDots()
                    if (enteredDigits.length == 4) {
                        handlePinEntered()
                    }
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            if (enteredDigits.isNotEmpty()) {
                enteredDigits.deleteCharAt(enteredDigits.length - 1)
                updateDots()
            }
        }

        binding.btnCancel.setOnClickListener {
            onComplete(false)
            dismiss()
        }
    }

    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        for (i in 0 until 4) {
            if (i < enteredDigits.length) {
                dots[i].setBackgroundResource(R.drawable.bg_calendar_dot)
                dots[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_color)
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_calendar_dot)
                dots[i].backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            }
        }
    }

    private fun handlePinEntered() {
        val pin = enteredDigits.toString()
        enteredDigits.clear()
        updateDots()

        if (!isConfirmPhase) {
            tempPin = pin
            isConfirmPhase = true
            binding.tvTitle.text = "Confirm your PIN"
            binding.tvSubtitle.text = "Re-enter your 4-digit security PIN"
        } else {
            if (pin == tempPin) {
                savePinAndPromptBiometrics(pin)
            } else {
                Toast.makeText(context, "PINs do not match. Start over.", Toast.LENGTH_SHORT).show()
                resetSetup()
            }
        }
    }

    private fun resetSetup() {
        tempPin = ""
        isConfirmPhase = false
        enteredDigits.clear()
        binding.tvTitle.text = "Create a PIN"
        binding.tvSubtitle.text = "Enter a 4-digit security PIN"
        updateDots()
    }

    private fun savePinAndPromptBiometrics(pin: String) {
        val sharedPrefs = requireContext().getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("app_lock_pin", pin)
            putBoolean("app_lock_enabled", true)
            apply()
        }

        // Check if device supports Biometrics (Fingerprint / Face)
        val biometricManager = BiometricManager.from(requireContext())
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
                .setTitle("Enable Biometrics")
                .setMessage("Would you like to enable Fingerprint/Face biometric unlock for quicker access?")
                .setPositiveButton("Yes") { _, _ ->
                    sharedPrefs.edit().putBoolean("app_lock_biometrics", true).apply()
                    Toast.makeText(context, "App Lock enabled with PIN & Biometrics", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                    dismiss()
                }
                .setNegativeButton("No") { _, _ ->
                    sharedPrefs.edit().putBoolean("app_lock_biometrics", false).apply()
                    Toast.makeText(context, "App Lock enabled with PIN only", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                    dismiss()
                }
                .setCancelable(false)
                .show()
        } else {
            // No biometrics available on device
            sharedPrefs.edit().putBoolean("app_lock_biometrics", false).apply()
            Toast.makeText(context, "App Lock enabled with PIN", Toast.LENGTH_SHORT).show()
            onComplete(true)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
