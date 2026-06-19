package com.cashbk.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cashbk.app.databinding.DialogEditProfileBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditProfileBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    private var currentName: String? = null
    private var currentPhone: String? = null
    private var onUpdate: ((String, String) -> Unit)? = null

    fun setOnUpdateListener(listener: (String, String) -> Unit) {
        this.onUpdate = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentName = it.getString("currentName")
            currentPhone = it.getString("currentPhone")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etName.setText(currentName)

        // Remove prefix if exists for editing
        val displayPhone = currentPhone?.removePrefix("+91")?.trim()
        binding.etPhone.setText(displayPhone)

        binding.btnUpdate.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (name.isEmpty()) {
                binding.tilName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            binding.tilName.error = null

            if (phone.isEmpty() || phone.length < 10) {
                binding.tilPhone.error = "Enter valid 10-digit number"
                return@setOnClickListener
            }
            binding.tilPhone.error = null

            // Format phone with prefix
            val finalPhone = if (phone.startsWith("+91")) phone else "+91$phone"

            binding.btnUpdate.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            onUpdate?.invoke(name, finalPhone)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(currentName: String?, currentPhone: String?): EditProfileBottomSheet {
            val fragment = EditProfileBottomSheet()
            val args = Bundle()
            args.putString("currentName", currentName)
            args.putString("currentPhone", currentPhone)
            fragment.arguments = args
            return fragment
        }
    }
}