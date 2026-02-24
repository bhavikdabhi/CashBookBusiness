package com.cashbk.app.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.adapter.PartnerAdapter
import com.cashbk.app.dataclass.Partner
import com.cashbk.app.databinding.DialogAddMemberBinding
import com.cashbk.app.databinding.FragmentManagePartnersBinding
import com.google.firebase.database.*

class ManagePartnersFragment : Fragment() {

    private var _binding: FragmentManagePartnersBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: DatabaseReference
    private lateinit var partnerAdapter: PartnerAdapter
    private val partnersList = mutableListOf<Partner>()
    private var businessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        businessId = arguments?.getString("businessId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagePartnersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (businessId.isNullOrEmpty()) {
            Toast.makeText(context, "Error: Missing Business ID", Toast.LENGTH_SHORT).show()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("business_members").child(businessId!!)

        setupRecyclerView()
        fetchPartners()

        binding.addPartnerFab.setOnClickListener {
            showAddPartnerDialog()
        }
    }

    private fun setupRecyclerView() {
        partnerAdapter = PartnerAdapter(partnersList) { partner ->
            removePartner(partner)
        }
        binding.partnersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = partnerAdapter
        }
    }

    private fun fetchPartners() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                partnersList.clear()
                val tempPartners = mutableListOf<Partner>()
                val childrenCount = snapshot.childrenCount.toInt()

                if (childrenCount == 0) {
                    partnerAdapter.updateData(partnersList)
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.partnersRecyclerView.visibility = View.GONE
                    return
                }

                binding.tvEmptyState.visibility = View.GONE
                binding.partnersRecyclerView.visibility = View.VISIBLE

                var loadedCount = 0

                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val role = child.child("role").value as? String ?: "partner"
                    val addedAt = child.child("addedAt").value as? Long ?: 0L

                    // Fetch User Details from users/{uid}
                    FirebaseDatabase.getInstance().reference.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val name = userSnapshot.child("name").value as? String ?: "Unknown"
                            val phone = userSnapshot.child("phone").value as? String ?: ""

                            val partner = Partner(id = uid, name = name, phone = phone, uid = uid, role = role, addedAt = addedAt)
                            tempPartners.add(partner)

                            loadedCount++
                            if (loadedCount >= childrenCount) {
                                partnersList.clear()
                                partnersList.addAll(tempPartners)
                                partnerAdapter.updateData(partnersList)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            loadedCount++
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load partners", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddPartnerDialog() {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.rbPartner.visibility = View.GONE
        dialogBinding.rbWriter.visibility = View.GONE
        dialogBinding.rbReader.visibility = View.GONE
        dialogBinding.rgRoles.visibility = View.GONE
        dialogBinding.tvRoleLabel.visibility = View.GONE // If exists in layout, hide it

        // Assuming we are only adding "partners" in this fragment
        dialogBinding.tvTitle.text = "Add Partner"


        dialogBinding.btnAddMember.setOnClickListener {
            val phone = dialogBinding.etMemberPhone.text.toString().trim()

            if (phone.length != 10) {
                Toast.makeText(context, "Enter valid 10-digit phone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addPartnerByPhone(phone)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addPartnerByPhone(phone: String) {
        val queryPhone = if (phone.startsWith("+91")) phone else "+91$phone"

        FirebaseDatabase.getInstance().reference.child("users").orderByChild("phone").equalTo(queryPhone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val uid = userSnapshot.key ?: continue

                            val memberData = mapOf(
                                "uid" to uid,
                                "role" to "partner",
                                "addedAt" to ServerValue.TIMESTAMP
                            )
                            database.child(uid).setValue(memberData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Partner added", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to add partner", Toast.LENGTH_SHORT).show()
                                }
                            return
                        }
                    } else {
                        Toast.makeText(context, "User not found with this phone", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Error searching user", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun removePartner(partner: Partner) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Partner")
            .setMessage("Are you sure you want to remove ${partner.name}?")
            .setPositiveButton("Yes") { _, _ ->
                database.child(partner.id).removeValue()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
