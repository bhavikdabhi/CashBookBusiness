package com.cashbk.app.business

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.utils.CustomAlertDialog
import androidx.fragment.app.Fragment
import com.cashbk.app.auth.AuthActivity
import com.cashbk.app.databinding.FragmentSettingsBinding
import com.cashbk.app.members.MembersActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchData()
        }

        binding.btnManageMembers.setOnClickListener {
            if (currentBusinessId.isNullOrEmpty()) return@setOnClickListener
            val intent = Intent(requireContext(), MembersActivity::class.java)
            intent.putExtra("entityId", currentBusinessId)
            intent.putExtra("entityType", "business")
            startActivity(intent)
        }

        binding.btnLeaveBusiness.setOnClickListener {
            Toast.makeText(requireContext(), "Leave business not fully implemented yet", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteBusiness.setOnClickListener {
            if (currentBusinessId.isNullOrEmpty()) return@setOnClickListener
            CustomAlertDialog(requireContext())
                .setTitle("Delete Business")
                .setMessage("Are you sure you want to delete this business? All data will be lost.")
                .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(requireContext(), R.color.danger))
                .setPositiveButton("Delete") {
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
            CustomAlertDialog(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setIcon(R.drawable.ic_logout, ContextCompat.getColor(requireContext(), R.color.danger))
                .setPositiveButton("Logout") {
                    FirebaseAuth.getInstance().signOut()

                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(requireActivity(), gso).signOut().addOnCompleteListener {
                        if (isAdded) {
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                            val intent = Intent(context, AuthActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            requireActivity().finish()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
        if (!businessId.isNullOrEmpty()) {
            fetchData()
        }
    }

    private fun fetchData() {
        val bid = currentBusinessId ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Fetch Business Details
        FirebaseDatabase.getInstance().reference.child("businesses").child(bid).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) return@addOnSuccessListener
                if (_binding == null) return@addOnSuccessListener

                val name = snapshot.child("name").getValue(String::class.java) ?: ""
                val ownerId = snapshot.child("ownerId").getValue(String::class.java) ?: ""
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                binding.tvBizName.text = name
                binding.tvManagingBusiness.text = "Managing: $name"

                if (createdAt > 0) {
                    val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                    binding.tvBizEst.text = "Established ${sdf.format(Date(createdAt))}"
                } else {
                    binding.tvBizEst.text = "Established recently"
                }

                if (ownerId == uid) {
                    binding.lblRole.text = "Organization Admin"
                } else {
                    FirebaseDatabase.getInstance().reference.child("business_members")
                        .child(bid).child(uid).get().addOnSuccessListener { mSnap ->
                            if (_binding == null) return@addOnSuccessListener
                            val role = mSnap.child("role").getValue(String::class.java)
                            if (role == "partner") {
                                binding.lblRole.text = "BUSINESS PARTNER"
                            } else if (!role.isNullOrEmpty()) {
                                binding.lblRole.text = role.uppercase()
                            } else {
                                binding.lblRole.text = "MEMBER"
                            }
                        }
                }
            }

        // Fetch Notebooks Count
        FirebaseDatabase.getInstance().reference.child("notebooks")
            .orderByChild("businessId").equalTo(bid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    val count = snapshot.childrenCount
                    binding.tvVaultDesc.text = "$count Active Notebooks"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Fetch Partners Count
        FirebaseDatabase.getInstance().reference.child("business_members").child(bid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    val count = snapshot.childrenCount
                    val totalPartners = count + 1 // Add 1 for the owner
                    binding.tvTotalPartner.text = "$totalPartners Members currently active"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}