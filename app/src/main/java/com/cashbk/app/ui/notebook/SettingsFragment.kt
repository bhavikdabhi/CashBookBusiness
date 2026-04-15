package com.cashbk.app.ui.notebook

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
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

        setupListeners()
        fetchNotebookAndBusinessInfo()
    }

    private fun setupListeners() {
        binding.btnManageMembers.setOnClickListener {
            val intent = Intent(requireContext(), MembersActivity::class.java)
            intent.putExtra("entityId", notebookId)
            intent.putExtra("entityType", "notebook")
            intent.putExtra("currentUserRole", currentUserRole)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            requireActivity().finishAffinity()
            // Assume there's a LoginActivity or similar to redirect to
            // val intent = Intent(requireContext(), LoginActivity::class.java)
            // startActivity(intent)
        }

        binding.btnLeaveBusiness.setOnClickListener {
            Toast.makeText(requireContext(), "Feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteBusiness.setOnClickListener {
            if (currentUserRole == "owner") {
                deleteNotebook()
            } else {
                Toast.makeText(requireContext(), "Only owners can delete notebooks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchNotebookAndBusinessInfo() {
        if (notebookId == null) return

        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val name = snapshot.child("name").value as? String ?: "Notebook"
                binding.tvManagingBusiness.text = "Managing: $name"
                binding.tvBizName.text = name

                val businessId = snapshot.child("businessId").value as? String ?: return@addOnSuccessListener
                FirebaseDatabase.getInstance().reference.child("businesses").child(businessId).get()
                    .addOnSuccessListener { bizSnapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        val bizName = bizSnapshot.child("name").value as? String ?: "Business"
                        binding.tvManagingBusiness.text = "Managing: $bizName"
                        binding.tvBizName.text = bizName
                    }
            }
    }

    private fun deleteNotebook() {
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook deleted", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
