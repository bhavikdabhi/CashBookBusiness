package com.cashbk.app.party

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.utils.CustomAlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.partner.adapter.PartnerAdapter
import com.cashbk.app.databinding.ActivityManagePartnersBinding
import com.cashbk.app.databinding.DialogAddMemberBinding
import com.cashbk.app.partner.Partner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.cashbk.app.utils.setupStatusAndNavBar

import com.cashbk.app.utils.showToast

class ManagePartnersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagePartnersBinding
    private lateinit var database: DatabaseReference
    private lateinit var partnerAdapter: PartnerAdapter
    private val partnersList = mutableListOf<Partner>()
    private var businessId: String? = null
    private var currentUserRole: String = "reader"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagePartnersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupStatusAndNavBar()

        businessId = intent.getStringExtra("businessId")

        if (businessId.isNullOrEmpty()) {
            showToast("Error: Missing Business ID")
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("business_members").child(businessId!!)

        // Hide FAB until role is determined
        binding.addPartnerFab.visibility = View.GONE

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        fetchPartners()
        checkUserRole()

        binding.addPartnerFab.setOnClickListener {
            showAddPartnerDialog()
        }
    }

    private fun setupRecyclerView() {
        partnerAdapter = PartnerAdapter(partnersList) { partner ->
            removePartner(partner)
        }
        binding.partnersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ManagePartnersActivity)
            adapter = partnerAdapter
        }
    }

    private fun fetchPartners() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    partnersList.clear()
                    partnerAdapter.updateData(partnersList)
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.partnersRecyclerView.visibility = View.GONE
                    return
                }

                val validChildren = snapshot.children.filter { it.key != null }
                val childrenCount = validChildren.size
                var loadedCount = 0

                if (childrenCount == 0) {
                    partnersList.clear()
                    partnerAdapter.updateData(partnersList)
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.partnersRecyclerView.visibility = View.GONE
                    return
                }

                binding.tvEmptyState.visibility = View.GONE
                binding.partnersRecyclerView.visibility = View.VISIBLE

                val tempPartners = mutableListOf<Partner>()

                for (child in validChildren) {
                    val uid = child.key!!
                    val role = child.child("role").value as? String ?: "partner"
                    val addedAt = child.child("addedAt").value as? Long ?: 0L

                    FirebaseDatabase.getInstance().reference.child("users").child(uid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                val name = userSnapshot.child("name").value as? String ?: "Unknown"
                                val phone = userSnapshot.child("phone").value as? String ?: ""

                                val partner = Partner(
                                    id = uid,
                                    name = name,
                                    phone = phone,
                                    uid = uid,
                                    role = role,
                                    addedAt = addedAt
                                )
                                tempPartners.add(partner)

                                loadedCount++
                                if (loadedCount >= childrenCount) {
                                    partnersList.clear()
                                    partnersList.addAll(tempPartners.sortedByDescending { it.addedAt })
                                    partnerAdapter.updateData(partnersList)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                loadedCount++
                                if (loadedCount >= childrenCount) {
                                    partnersList.clear()
                                    partnersList.addAll(tempPartners.sortedByDescending { it.addedAt })
                                    partnerAdapter.updateData(partnersList)
                                }
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Failed to load partners")
            }
        })
    }

    private fun showAddPartnerDialog() {
        if (currentUserRole != "owner" && currentUserRole != "admin") {
            showToast("Only owners or admins can add partners")
            return
        }
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilRole.visibility = View.GONE
        dialogBinding.tvTitle.text = "Add Partner"

        dialogBinding.btnAddMember.setOnClickListener {
            val phone = dialogBinding.etMemberPhone.text.toString().trim()

            if (phone.length != 10) {
                showToast("Enter valid 10-digit phone")
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
                                    showToast("Partner added")
                                }
                                .addOnFailureListener {
                                    showToast("Failed to add partner")
                                }
                            return
                        }
                    } else {
                        showToast("User not found with this phone", Toast.LENGTH_LONG)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Error searching user")
                }
            })
    }

    private fun removePartner(partner: Partner) {
        if (currentUserRole != "owner" && currentUserRole != "admin") {
            showToast("Only owners or admins can remove partners")
            return
        }
        CustomAlertDialog(this)
            .setTitle("Remove Partner")
            .setMessage("Are you sure you want to remove ${partner.name}?")
            .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(this, R.color.danger))
            .setPositiveButton("Yes") {
                database.child(partner.id).removeValue()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val bId = businessId ?: return

        FirebaseDatabase.getInstance().reference.child("businesses").child(bId).get()
            .addOnSuccessListener { businessSnapshot ->
                val ownerId = businessSnapshot.child("ownerId").value as? String
                if (ownerId == currentUserId) {
                    currentUserRole = "owner"
                    updateUIForRole()
                    return@addOnSuccessListener
                }

                FirebaseDatabase.getInstance().reference.child("business_members").child(bId).child(currentUserId).get()
                    .addOnSuccessListener { memberSnapshot ->
                        if (memberSnapshot.exists()) {
                            val role = memberSnapshot.child("role").value as? String ?: "partner"
                            currentUserRole = role
                        }
                        updateUIForRole()
                    }
            }
    }

    private fun updateUIForRole() {
        val canManage = currentUserRole == "owner" || currentUserRole == "admin"
        binding.addPartnerFab.visibility = if (canManage) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        setupStatusAndNavBar()
    }
}