package com.cashbk.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.dataclass.Member
import com.cashbk.app.adapter.MembersAdapter
import com.cashbk.app.databinding.ActivityMembersBinding
import com.cashbk.app.databinding.DialogAddMemberBinding
import com.cashbk.app.databinding.ItemMemberBinding
import com.google.firebase.database.*

class MembersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMembersBinding
    private lateinit var database: DatabaseReference
    private lateinit var membersAdapter: MembersAdapter
    private val membersList = mutableListOf<Member>()
    
    private var entityId: String? = null
    private var entityType: String? = null // "business" or "notebook"
    private var currentUserRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMembersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        entityId = intent.getStringExtra("entityId")
        entityType = intent.getStringExtra("entityType")
        currentUserRole = intent.getStringExtra("currentUserRole")

        if (entityId.isNullOrEmpty() || entityType.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing Entity ID or Type", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Correct Paths based on JSON:
        // Business Members -> business_members/{businessId}
        // Notebook Members -> members/{notebookId}
        val path = if (entityType == "business") "business_members" else "members"
        database = FirebaseDatabase.getInstance().reference.child(path).child(entityId!!)

        setupRecyclerView()
        fetchMembers()

        binding.addMemberFab.setOnClickListener {
            showAddMemberDialog()
        }
    }

    private fun setupRecyclerView() {
        membersAdapter = MembersAdapter(membersList) { member ->
            // Handle delete
            deleteMember(member)
        }
        binding.membersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MembersActivity)
            adapter = membersAdapter
        }
    }

    private fun fetchMembers() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membersList.clear()
                val tempMembers = mutableListOf<Member>()
                val childrenCount = snapshot.childrenCount.toInt()
                
                if (childrenCount == 0) {
                    membersAdapter.notifyDataSetChanged()
                    binding.layoutEmpty.visibility = android.view.View.VISIBLE
                    binding.membersRecyclerView.visibility = android.view.View.GONE
                    return
                } else {
                    binding.layoutEmpty.visibility = android.view.View.GONE
                    binding.membersRecyclerView.visibility = android.view.View.VISIBLE
                }

                var loadedCount = 0

                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val role = child.child("role").value as? String ?: "member"
                    val addedAt = child.child("addedAt").value as? Long ?: 0L

                    // Fetch User Details from users/{uid}
                    FirebaseDatabase.getInstance().reference.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot) {
                            val name = userSnapshot.child("name").value as? String ?: "Unknown"
                            val phone = userSnapshot.child("phone").value as? String ?: ""
                            
                            // Create Member object with UID as ID
                            val member = Member(id = uid, name = name, phone = phone, role = role, addedAt = addedAt)
                            tempMembers.add(member)
                            
                            loadedCount++
                            if (loadedCount >= childrenCount) {
                                membersList.clear()
                                membersList.addAll(tempMembers)
                                membersAdapter.notifyDataSetChanged()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            loadedCount++ // Still increment to avoid hanging
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MembersActivity, "Failed to load members", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddMemberDialog() {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Configure roles based on entity type
        val roles = mutableListOf<String>()
        if (entityType == "business") {
            roles.add("partner")
        } else {
            if (currentUserRole == "owner") {
                roles.add("admin")
            }
            roles.add("writer")
            roles.add("reader")
        }
        
        val roleDisplayNames = roles.map {
            when(it) {
                "partner" -> "Partner (Full Access, No Delete Business)"
                "admin" -> "Admin (Manage All except business)"
                "writer" -> "Writer (Add/Remove Transactions)"
                "reader" -> "Reader (View Only)"
                else -> it
            }
        }
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roleDisplayNames)
        dialogBinding.actvRole.setAdapter(adapter)

        // Select default
        if (roleDisplayNames.isNotEmpty()) {
            val defaultIndex = if (entityType != "business" && currentUserRole == "owner") 1 else 0
            dialogBinding.actvRole.setText(roleDisplayNames[defaultIndex], false)
        }

        dialogBinding.btnAddMember.setOnClickListener {
            val phone = dialogBinding.etMemberPhone.text.toString().trim()
            val roleDisplay = dialogBinding.actvRole.text.toString()
            
            if (phone.length != 10) {
                dialogBinding.etMemberPhone.error = "Enter valid 10-digit phone"
                return@setOnClickListener
            } else {
                dialogBinding.etMemberPhone.error = null
            }

            val role = when {
                roleDisplay.startsWith("Partner") -> "partner"
                roleDisplay.startsWith("Admin") -> "admin"
                roleDisplay.startsWith("Writer") -> "writer"
                roleDisplay.startsWith("Reader") -> "reader"
                else -> ""
            }

            if (role.isEmpty()) {
                Toast.makeText(this, "Select a role", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (role == "admin" && membersList.count { it.role == "admin" } >= 3) {
                Toast.makeText(this, "You can fully appoint a maximum of 3 Admins per notebook", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            addMemberByPhone(phone, role)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addMemberByPhone(phone: String, role: String) {
        // 1. Find user by phone
        val queryPhone = if (phone.startsWith("+91")) phone else "+91$phone"
        
        FirebaseDatabase.getInstance().reference.child("users").orderByChild("phone").equalTo(queryPhone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (userSnapshot in snapshot.children) {
                            val uid = userSnapshot.key ?: continue
                            
                            // 2. Add to members list
                            val memberData = mapOf(
                                "uid" to uid,
                                "role" to role,
                                "addedAt" to ServerValue.TIMESTAMP
                            )
                            database.child(uid).setValue(memberData)
                                .addOnSuccessListener {
                                    Toast.makeText(this@MembersActivity, "Member added", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@MembersActivity, "Failed to add member", Toast.LENGTH_SHORT).show()
                                }
                            return // Added first match
                        }
                    } else {
                        Toast.makeText(this@MembersActivity, "User not found with this phone", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MembersActivity, "Error searching user", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun deleteMember(member: Member) {
        if (member.role == "admin" && currentUserRole != "owner") {
            Toast.makeText(this, "Only the business owner can remove an Admin", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${member.name}?")
            .setPositiveButton("Yes") { _, _ ->
                database.child(member.id).removeValue()
            }
            .setNegativeButton("No", null)
            .show()
    }


}
