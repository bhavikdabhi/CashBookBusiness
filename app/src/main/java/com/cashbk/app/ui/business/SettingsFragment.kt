package com.cashbk.app.ui.business

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.cashbk.app.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var currentBusinessId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnManageMembers.setOnClickListener {
            if (currentBusinessId.isNullOrEmpty()) return@setOnClickListener
            val intent = Intent(requireContext(), com.cashbk.app.ui.members.MembersActivity::class.java)
            intent.putExtra("entityId", currentBusinessId)
            intent.putExtra("entityType", "business")
            startActivity(intent)
        }
        
        binding.btnLeaveBusiness.setOnClickListener {
            Toast.makeText(requireContext(), "Leave business not fully implemented yet", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnDeleteBusiness.setOnClickListener {
            if (currentBusinessId.isNullOrEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Business")
                .setMessage("Are you sure you want to delete this business? All data will be lost.")
                .setPositiveButton("Delete") { _, _ ->
                    FirebaseDatabase.getInstance().reference.child("businesses").child(currentBusinessId!!).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Business Deleted", Toast.LENGTH_SHORT).show()
                            requireActivity().finish()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), com.cashbk.app.ui.auth.AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
