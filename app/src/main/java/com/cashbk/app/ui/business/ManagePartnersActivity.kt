package com.cashbk.app.ui.business

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.adapter.PartnerAdapter
import com.cashbk.app.dataclass.Partner
import com.cashbk.app.databinding.DialogAddMemberBinding
import com.cashbk.app.databinding.ActivityManagePartnersBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class ManagePartnersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagePartnersBinding
    private lateinit var database: DatabaseReference
    private lateinit var partnerAdapter: PartnerAdapter
    private val partnersList = mutableListOf<Partner>()
    private var businessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagePartnersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        businessId = intent.getStringExtra("businessId")

        if (businessId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing Business ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("business_members").child(businessId!!)

        binding.btnBack.setOnClickListener {
            finish()
        }

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

                                val partner = Partner(id = uid, name = name, phone = phone, uid = uid, role = role, addedAt = addedAt)
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
                Toast.makeText(this@ManagePartnersActivity, "Failed to load partners", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddPartnerDialog() {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tilRole.visibility = View.GONE
        dialogBinding.tvTitle.text = "Add Partner"

        dialogBinding.btnAddMember.setOnClickListener {
            val phone = dialogBinding.etMemberPhone.text.toString().trim()

            if (phone.length != 10) {
                Toast.makeText(this, "Enter valid 10-digit phone", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(this@ManagePartnersActivity, "Partner added", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@ManagePartnersActivity, "Failed to add partner", Toast.LENGTH_SHORT).show()
                                }
                            return
                        }
                    } else {
                        Toast.makeText(this@ManagePartnersActivity, "User not found with this phone", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ManagePartnersActivity, "Error searching user", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun removePartner(partner: Partner) {
        AlertDialog.Builder(this)
            .setTitle("Remove Partner")
            .setMessage("Are you sure you want to remove ${partner.name}?")
            .setPositiveButton("Yes") { _, _ ->
                database.child(partner.id).removeValue()
            }
            .setNegativeButton("No", null)
            .show()
    }
}
