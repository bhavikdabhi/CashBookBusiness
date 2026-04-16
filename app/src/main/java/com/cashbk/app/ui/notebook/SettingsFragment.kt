package com.cashbk.app.ui.notebook

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentNotebookSettingBinding
import com.cashbk.app.ui.members.MembersActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SettingsFragment : Fragment() {

    private var _binding: FragmentNotebookSettingBinding? = null
    private val binding get() = _binding!!

    private var notebookId: String? = null
    private var currentUserRole: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotebookSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        currentUserRole = arguments?.getString("currentUserRole") ?: ""

        if (notebookId == null) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setupListeners()
        fetchNotebookInfo()
        fetchStats()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSaveName.setOnClickListener {
            val newName = binding.etNotebookName.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateNotebookName(newName)
            } else {
                binding.etNotebookName.error = "Name cannot be empty"
            }
        }

        binding.btnManageMembers.setOnClickListener {
            val intent = Intent(requireContext(), MembersActivity::class.java)
            intent.putExtra("entityId", notebookId)
            intent.putExtra("entityType", "notebook")
            intent.putExtra("currentUserRole", currentUserRole)
            startActivity(intent)
        }

        binding.btnManageCategories.setOnClickListener {
            val fragment = ManageCategoriesFragment()
            val args = Bundle()
            args.putString("notebookId", notebookId)
            fragment.arguments = args
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.notebook_fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        binding.btnDuplicateNotebook.setOnClickListener {
            duplicateNotebook()
        }

        binding.btnDeleteBusiness.setOnClickListener {
            if (currentUserRole == "owner") {
                showDeleteConfirmation()
            } else {
                Toast.makeText(requireContext(), "Only owners can delete notebooks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchNotebookInfo() {
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val name = snapshot.child("name").value as? String ?: ""
                binding.etNotebookName.setText(name)
            }
    }

    private fun fetchStats() {
        // Count Members (Not Partners)
        FirebaseDatabase.getInstance().reference.child("members").child(notebookId!!)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    _binding?.tvMembersCount?.text = snapshot.childrenCount.toString()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })

        // Count Categories
        FirebaseDatabase.getInstance().reference.child("categories").child(notebookId!!)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    _binding?.tvClassesCount?.text = snapshot.childrenCount.toString()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun updateNotebookName(newName: String) {
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!)
            .child("name").setValue(newName)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook name updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun duplicateNotebook() {
        val currentName = binding.etNotebookName.text.toString().trim()
        val newName = "$currentName (Copy)"
        val database = FirebaseDatabase.getInstance().reference
        val newNotebookId = database.child("notebooks").push().key ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        Toast.makeText(requireContext(), "Duplicating notebook...", Toast.LENGTH_SHORT).show()

        // 1. Create Notebook Metadata
        val notebookData = mapOf(
            "id" to newNotebookId,
            "name" to newName,
            "ownerId" to currentUserId,
            "createdAt" to System.currentTimeMillis()
        )

        database.child("notebooks").child(newNotebookId).setValue(notebookData)
            .addOnSuccessListener {
                // 2. Set Current User as Owner in Members
                database.child("members").child(newNotebookId).child(currentUserId).setValue("owner")

                // 3. Clone Categories
                database.child("categories").child(notebookId!!).get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        database.child("categories").child(newNotebookId).setValue(snapshot.value)
                    }
                    
                    Toast.makeText(requireContext(), "Notebook duplicated successfully", Toast.LENGTH_SHORT).show()
                    requireActivity().finish() // Refresh and go back to home to see new book
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Duplication failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Notebook")
            .setMessage("Are you sure you want to permanently delete this notebook? This will erase all categories, parties, and transactions.")
            .setPositiveButton("Delete Everything") { _, _ -> deleteNotebook() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNotebook() {
        val database = FirebaseDatabase.getInstance().reference
        val updates = HashMap<String, Any?>()
        
        // Remove all related nodes
        updates["/notebooks/$notebookId"] = null
        updates["/members/$notebookId"] = null
        updates["/parties/$notebookId"] = null
        updates["/categories/$notebookId"] = null
        updates["/transactions/$notebookId"] = null

        database.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook and all data purged", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Purge failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
