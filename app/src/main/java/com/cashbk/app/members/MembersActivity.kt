package com.cashbk.app.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cashbk.app.utils.CustomAlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.members._bean.Member
import com.cashbk.app.members.adapter.MembersAdapter
import com.cashbk.app.databinding.ActivityMembersBinding
import com.cashbk.app.databinding.DialogAddMemberBinding
import com.cashbk.app.databinding.ItemMemberBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
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

        if (entityId.isNullOrEmpty() || entityType.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing Entity ID or Type", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Hide FAB by default until verified from database
        binding.addMemberFab.visibility = View.GONE

        // Correct Paths based on JSON:
        // Business Members -> business_members/{businessId}
        // Notebook Members -> members/{notebookId}
        val path = if (entityType == "business") "business_members" else "members"
        database = FirebaseDatabase.getInstance().reference.child(path).child(entityId!!)

        setupRecyclerView()
        fetchMembers()
        fetchCurrentUserRole()

        binding.btnBack.setOnClickListener {
            finish()
        }

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
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.membersRecyclerView.visibility = View.GONE
                    return
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.membersRecyclerView.visibility = View.VISIBLE
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
        val canAdd = if (entityType == "business") {
            currentUserRole == "owner" || currentUserRole == "partner"
        } else {
            currentUserRole == "owner" || currentUserRole == "admin"
        }
        if (!canAdd) {
            Toast.makeText(this, "You do not have permission to add members", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this, R.style.CashbkAlertDialog)
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
        val adapter = ArrayAdapter(this, R.layout.item_dropdown, roleDisplayNames)
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
        val canManage = if (entityType == "business") {
            currentUserRole == "owner" || currentUserRole == "partner"
        } else {
            currentUserRole == "owner" || currentUserRole == "admin"
        }
        if (!canManage) {
            Toast.makeText(this, "You do not have permission to remove members", Toast.LENGTH_SHORT).show()
            return
        }
        if (member.role == "admin" && currentUserRole != "owner") {
            Toast.makeText(this, "Only the business owner can remove an Admin", Toast.LENGTH_SHORT).show()
            return
        }
        CustomAlertDialog(this)
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${member.name}?")
            .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(this, R.color.danger))
            .setPositiveButton("Yes") {
                database.child(member.id).removeValue()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun fetchCurrentUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Check ownership first
        val checkRef = if (entityType == "business") {
            FirebaseDatabase.getInstance().reference.child("businesses").child(entityId!!)
        } else {
            FirebaseDatabase.getInstance().reference.child("notebooks").child(entityId!!)
        }

        checkRef.get().addOnSuccessListener { snapshot ->
            val ownerId = snapshot.child("ownerId").value as? String
            if (ownerId == currentUserId) {
                currentUserRole = "owner"
                updateUIForRole()
                return@addOnSuccessListener
            }

            // Fallback to checking the member list
            database.child(currentUserId).child("role").get()
                .addOnSuccessListener { roleSnapshot ->
                    currentUserRole = roleSnapshot.value as? String ?: "reader"
                    updateUIForRole()
                }
                .addOnFailureListener {
                    currentUserRole = "reader"
                    updateUIForRole()
                }
        }.addOnFailureListener {
            currentUserRole = "reader"
            updateUIForRole()
        }
    }

    private fun updateUIForRole() {
        val canAdd = if (entityType == "business") {
            currentUserRole == "owner" || currentUserRole == "partner"
        } else {
            currentUserRole == "owner" || currentUserRole == "admin"
        }
        binding.addMemberFab.visibility = if (canAdd) View.VISIBLE else View.GONE
    }


}
