package com.cashbk.app.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.databinding.FragmentAddBusinessBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddNotebookDialogFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentAddBusinessBinding
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference.child("notebooks")

    private var businessId: String? = null
    private var isRename = false
    private var notebookId: String? = null
    private var oldNotebookName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            businessId = it.getString("businessId")
            isRename = it.getBoolean("isRename", false)
            notebookId = it.getString("notebookId")
            oldNotebookName = it.getString("notebookName", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddBusinessBinding.inflate(inflater, container, false)

        // ---- UI SETUP ----
        binding.tilBusinessName.hint = "Notebook Name"

        if (isRename) {
            // ✏️ RENAME MODE
            binding.tvDialogTitle.text = "Rename Notebook"
            binding.btnSaveBusiness.text = "Update"
            binding.etBusinessName.setText(oldNotebookName)
            binding.etBusinessName.setSelection(oldNotebookName.length)
        } else {
            // ➕ CREATE MODE
            binding.tvDialogTitle.text = "Add Notebook"
            binding.btnSaveBusiness.text = "Create Notebook"
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSaveBusiness.setOnClickListener {
            val name = binding.etBusinessName.text.toString().trim()

            if (name.isEmpty()) {
                binding.etBusinessName.error = "Enter notebook name"
                return@setOnClickListener
            }

            if (isRename) {
                renameNotebook(name)
            } else {
                saveNotebook(name)
            }
        }

        return binding.root
    }

    /* ---------------- CREATE ---------------- */

    private fun saveNotebook(name: String) {

        val userId = auth.currentUser?.uid ?: return

        if (businessId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Business ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        val newId = database.push().key ?: return

        val notebook = Notebook(
            id = newId,
            name = name,
            businessId = businessId!!,
            createdBy = userId,
            createdAt = System.currentTimeMillis()
        )

        database.child(newId)
            .setValue(notebook)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook Created", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }

    /* ---------------- RENAME ---------------- */

    private fun renameNotebook(newName: String) {

        val id = notebookId
        if (id.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        database.child(id).child("name")
            .setValue(newName)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook Renamed", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }
}
