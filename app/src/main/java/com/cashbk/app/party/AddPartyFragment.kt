package com.cashbk.app.party

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentAddPartyBinding
import com.cashbk.app.party._bean.Party
import com.google.firebase.database.FirebaseDatabase

import com.cashbk.app.utils.showToast

class AddPartyFragment : Fragment() {

    private var _binding: FragmentAddPartyBinding? = null
    private val binding get() = _binding!!

    private var notebookId: String? = null
    private var selectedRole = "CUSTOMER" // Default as per screenshot

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                readContactFromUri(contactUri)
            }
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchContactPicker()
        } else {
            showToast("Contacts permission is needed to pick a contact")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.cashbk.app.notebook.NotebookActivity)?.setBottomNavVisible(false)

        notebookId = arguments?.getString("notebookId")
        if (notebookId.isNullOrEmpty()) {
            showToast("Notebook error")
            parentFragmentManager.popBackStack()
            return
        }

        setupRoleSelection()
        
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSave.setOnClickListener { saveParty() }
        
        binding.btnSelectContacts.setOnClickListener {
            pickContactWithPermissionCheck()
        }
    }

    private fun pickContactWithPermissionCheck() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchContactPicker()
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun readContactFromUri(contactUri: Uri) {
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    val name = if (nameIdx != -1) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numberIdx != -1) cursor.getString(numberIdx)?.trim().orEmpty() else ""
                    val cleanedNumber = rawNumber.replace(Regex("[\\s()-]"), "")

                    if (name.isNotEmpty()) {
                        binding.etName.setText(name)
                    }
                    if (cleanedNumber.isNotEmpty()) {
                        binding.etContact.setText(cleanedNumber)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Unable to read contact: ${e.message}")
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
            showToast("Full name is required")
            return
        }

        binding.btnSave.isEnabled = false
        val database = FirebaseDatabase.getInstance().reference.child("parties").child(notebookId!!)
        val partyId = database.push().key ?: ""

        val defaultColorHex = String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(requireContext(), R.color.color_80deea))

        val party = Party(
            id = partyId,
            name = name,
            contact = contact,
            role = selectedRole,
            colorHex = defaultColorHex,
            iconResName = when(selectedRole) {
                "VENDOR" -> "ic_party_vendor"
                "CUSTOMER" -> "ic_party_group"
                "CONTRACTOR" -> "ic_party_contractor"
                "EMPLOYEE" -> "ic_party_employee"
                "PARTNER" -> "ic_party_building"
                else -> "ic_party_group"
            }
        )

        database.child(partyId).setValue(party)
            .addOnSuccessListener {
                showToast("Partner created successfully!")
                
                if (isAdded && !parentFragmentManager.isStateSaved) {
                    parentFragmentManager.popBackStack()
                }
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                showToast("Error: ${e.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? com.cashbk.app.notebook.NotebookActivity)?.setBottomNavVisible(true)
        _binding = null
    }
}
