package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentAddPartyBinding
import com.cashbk.app.dataclass.Party
import com.google.firebase.database.FirebaseDatabase

class AddPartyFragment : Fragment() {

    private var _binding: FragmentAddPartyBinding? = null
    private val binding get() = _binding!!

    private var notebookId: String? = null
    private var selectedRole = "CUSTOMER" // Default as per screenshot

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        if (notebookId == null) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setupRoleSelection()
        
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSave.setOnClickListener { saveParty() }
        
        binding.btnSelectContacts.setOnClickListener {
            Toast.makeText(requireContext(), "Contact integration coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRoleSelection() {
        val roleMap = mapOf(
            binding.btnRoleVendor to "VENDOR",
            binding.btnRoleCustomer to "CUSTOMER",
            binding.btnRoleContractor to "CONTRACTOR",
            binding.btnRoleEmployee to "EMPLOYEE"
        )

        roleMap.forEach { (view, role) ->
            view.setOnClickListener {
                selectedRole = role
                updateRoleUI()
            }
        }
        updateRoleUI() // Initial state
    }

    private fun updateRoleUI() {
        val roleViews = listOf(
            binding.btnRoleVendor,
            binding.btnRoleCustomer,
            binding.btnRoleContractor,
            binding.btnRoleEmployee
        )

        val selectedView = when (selectedRole) {
            "VENDOR" -> binding.btnRoleVendor
            "CUSTOMER" -> binding.btnRoleCustomer
            "CONTRACTOR" -> binding.btnRoleContractor
            "EMPLOYEE" -> binding.btnRoleEmployee
            else -> binding.btnRoleCustomer
        }

        roleViews.forEach { view ->
            if (view == selectedView) {
                view.setBackgroundResource(R.drawable.bg_role_selected)
            } else {
                view.setBackgroundResource(R.drawable.bg_role_card)
            }
        }
    }

    private fun saveParty() {
        val name = binding.etName.text.toString().trim()
        val contact = binding.etContact.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Full name is required", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        val database = FirebaseDatabase.getInstance().reference.child("parties").child(notebookId!!)
        val partyId = database.push().key ?: ""

        val party = Party(
            id = partyId,
            name = name,
            contact = contact,
            role = selectedRole,
            colorHex = "#80DEEA", // Keep default color or map from role
            iconResName = when(selectedRole) {
                "VENDOR" -> "ic_party_shop"
                "CUSTOMER" -> "ic_party_person"
                "CONTRACTOR" -> "ic_party_tool"
                "EMPLOYEE" -> "ic_party_employee"
                else -> "ic_party_person"
            }
        )

        database.child(partyId).setValue(party)
            .addOnSuccessListener {
                val context = context ?: return@addOnSuccessListener
                Toast.makeText(context, "Partner created successfully!", Toast.LENGTH_SHORT).show()
                
                if (isAdded && !parentFragmentManager.isStateSaved) {
                    parentFragmentManager.popBackStack()
                }
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
