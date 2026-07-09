package com.cashbk.app.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cashbk.app.databinding.FragmentAddBusinessBinding
import com.cashbk.app.notebook._bean.Notebook
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
        val bid = businessId

        if (bid.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Business ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        val newId = database.push().key ?: return
        val notebook = Notebook(
            id = newId,
            name = name,
            businessId = bid,
            createdBy = userId,
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            try {
                com.cashbk.app.data.di.AppContainer.getRepository(requireContext()).saveNotebook(notebook)
                Toast.makeText(requireContext(), "Notebook Created", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /* ---------------- RENAME ---------------- */

    private fun renameNotebook(newName: String) {
        val id = notebookId
        if (id.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val repository = com.cashbk.app.data.di.AppContainer.getRepository(requireContext())
                val notebook = repository.getNotebookById(id)
                if (notebook != null) {
                    val updated = notebook.copy(name = newName)
                    repository.saveNotebook(updated)
                    Toast.makeText(requireContext(), "Notebook Renamed", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "Notebook not found locally", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}