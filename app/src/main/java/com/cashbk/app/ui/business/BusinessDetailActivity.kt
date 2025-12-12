package com.cashbk.app.ui.business

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.databinding.ActivityBusinessDetailBinding
import com.cashbk.app.databinding.ItemNotebookBinding
import com.cashbk.app.ui.notebook.NotebookActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BusinessDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessDetailBinding
    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val notebookList = mutableListOf<Notebook>()
    private var businessId: String? = null
    private var currentUserRole: String = "" // "owner", "partner", "member"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBusinessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        businessId = intent.getStringExtra("businessId")
        Log.d("BusinessDetailActivity", "Received businessId: $businessId")

        if (businessId.isNullOrEmpty()) {
            Toast.makeText(this, "Business ID is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("notebooks")

        setupRecyclerView()
        fetchNotebooks()
        checkUserRole()

        binding.addNotebookFab.setOnClickListener {
            Toast.makeText(this, "Add new notebook clicked", Toast.LENGTH_SHORT).show()
        }

        binding.btnMenu.setOnClickListener {
            showBusinessMenu(it)
        }
    }

    private fun showBusinessMenu(view: android.view.View) {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_business_options, popup.menu)

        // Role check
        if (currentUserRole != "owner") {
            popup.menu.findItem(R.id.action_delete).isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_manage_partner -> {
                    val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                    intent.putExtra("entityId", businessId)
                    intent.putExtra("entityType", "business")
                    startActivity(intent)
                    true
                }
                R.id.action_delete -> {
                    Toast.makeText(this, "Delete Business Clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showNotebookMenu(notebook: Notebook, view: android.view.View) {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_notebook_options, popup.menu)

        // Role check
        if (currentUserRole != "owner" && currentUserRole != "partner") {
             // Hide all or show limited? User said "when user clikc not three dots that time open a menu that menu data not visible"
             // Assuming we just want to fix visibility. Logic remains: only owner/partner can act.
             // If neither, maybe we shouldn't even show the button (already handled in adapter).
             // But if we are here, we show options.
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    Toast.makeText(this, "Rename ${notebook.name}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_delete -> {
                    Toast.makeText(this, "Delete ${notebook.name}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_share -> {
                    val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                    intent.putExtra("entityId", notebook.id)
                    intent.putExtra("entityType", "notebook")
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun fetchNotebooks() {
        database.orderByChild("businessId").equalTo(businessId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notebookList.clear()
                    if (snapshot.exists()) {
                        for (notebookSnapshot in snapshot.children) {
                            val notebook = notebookSnapshot.getValue(Notebook::class.java)
                            notebook?.id = notebookSnapshot.key.orEmpty()
                            notebook?.let { notebookList.add(it) }
                        }
                    }
                    notebookAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@BusinessDetailActivity, "Failed to load notebooks", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // 1. Check if Owner
        FirebaseDatabase.getInstance().reference.child("businesses").child(businessId!!).get().addOnSuccessListener { snapshot ->
            val ownerId = snapshot.child("ownerId").value as? String
            if (ownerId == currentUserId) {
                currentUserRole = "owner"
                binding.btnMenu.visibility = android.view.View.VISIBLE
            } else {
                // 2. Check if Partner in business_members/{businessId}/{uid}
                FirebaseDatabase.getInstance().reference.child("business_members").child(businessId!!).child(currentUserId).get().addOnSuccessListener { memberSnapshot ->
                    if (memberSnapshot.exists()) {
                        val role = memberSnapshot.child("role").value as? String
                        if (role == "partner") {
                            currentUserRole = "partner"
                            binding.btnMenu.visibility = android.view.View.VISIBLE
                        } else {
                            currentUserRole = "member"
                            binding.btnMenu.visibility = android.view.View.GONE
                        }
                    } else {
                        binding.btnMenu.visibility = android.view.View.GONE
                    }
                    notebookAdapter.notifyDataSetChanged()
                }
            }
            notebookAdapter.notifyDataSetChanged()
        }
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(notebookList, 
            onClick = { notebook ->
                if (notebook.id.isEmpty()) {
                    Toast.makeText(this, "Error: Notebook ID is empty!", Toast.LENGTH_SHORT).show()
                    return@NotebookAdapter
                }
                val intent = Intent(this, NotebookActivity::class.java)
                intent.putExtra("notebookId", notebook.id)
                startActivity(intent)
            },
            onMenuClick = { notebook, view ->
                showNotebookMenu(notebook, view)
            }
        )

        binding.notebooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BusinessDetailActivity)
            adapter = notebookAdapter
        }
    }

    inner class NotebookAdapter(
        private val notebooks: List<Notebook>,
        private val onClick: (Notebook) -> Unit,
        private val onMenuClick: (Notebook, android.view.View) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<NotebookAdapter.NotebookViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotebookViewHolder {
            val itemBinding = ItemNotebookBinding.inflate(layoutInflater, parent, false)
            return NotebookViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
            val notebook = notebooks[position]
            holder.bind(notebook)
            holder.itemView.setOnClickListener { onClick(notebook) }
            
            // Show/Hide menu button based on role
            if (currentUserRole == "owner" || currentUserRole == "partner") {
                holder.itemBinding.btnNotebookOptions.visibility = android.view.View.VISIBLE
                holder.itemBinding.btnNotebookOptions.setOnClickListener { 
                    onMenuClick(notebook, it) 
                }
            } else {
                holder.itemBinding.btnNotebookOptions.visibility = android.view.View.GONE
            }
        }

        override fun getItemCount() = notebooks.size

        inner class NotebookViewHolder(val itemBinding: ItemNotebookBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(notebook: Notebook) {
                itemBinding.notebookName.text = notebook.name
            }
        }
    }
}
