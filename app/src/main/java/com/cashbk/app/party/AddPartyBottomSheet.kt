package com.cashbk.app.party

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.databinding.AddPartyBottomSheetBinding
import com.cashbk.app.party._bean.Party
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddPartyBottomSheet : BottomSheetDialogFragment() {

    private var _binding: AddPartyBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AddPartyBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.savePartyButton.setOnClickListener {

            val name = binding.partyNameEditText.text.toString().trim()
            val phone = binding.partyPhoneEditText.text.toString().trim()

            if (name.isEmpty()) {
                binding.partyNameEditText.error = "Enter name"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                binding.partyPhoneEditText.error = "Enter phone"
                return@setOnClickListener
            }

            val id = FirebaseDatabase.getInstance().reference.push().key!!

            val defaultColorHex = String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(requireContext(), R.color.color_80deea))

            val party = Party(
                id = id,
                name = name,
                contact = phone,
                role = "CUSTOMER",
                colorHex = defaultColorHex,
                iconResName = "ic_party_person"
            )

            val notebookId = arguments?.getString("notebookId") ?: ""

            FirebaseDatabase.getInstance().reference
                .child("parties")
                .child(notebookId)
                .child(id)
                .setValue(party)

            dismiss()
        }

        binding.cancelPartyButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}