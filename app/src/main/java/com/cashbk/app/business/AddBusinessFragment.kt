package com.cashbk.app.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cashbk.app.databinding.FragmentAddBusinessBinding
import com.cashbk.app.business._bean.Business
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddBusinessFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentAddBusinessBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference.child("businesses")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddBusinessBinding.inflate(inflater, container, false)

        binding.btnSaveBusiness.setOnClickListener {
            saveBusiness()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    private fun saveBusiness() {
        val name = binding.etBusinessName.text.toString().trim()

        if (name.isEmpty()) {
            binding.etBusinessName.error = "Enter business name"
            return
        }

        val userId = auth.currentUser?.uid ?: return

        val newId = database.push().key.toString()

        val business = Business(
            id = newId,
            name = name,
            ownerId = userId,
            createdAt = System.currentTimeMillis()
        )

        database.child(newId).setValue(business).addOnSuccessListener {
            Toast.makeText(requireContext(), "Business Added", Toast.LENGTH_SHORT).show()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}