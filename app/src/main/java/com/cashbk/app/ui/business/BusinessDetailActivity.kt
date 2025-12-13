package com.cashbk.app.ui.business

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.data.model.Business
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.databinding.ActivityBusinessDetailBinding
import com.cashbk.app.databinding.ItemNotebookBinding
import com.cashbk.app.ui.notebook.NotebookActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Calendar
import java.util.Locale
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.content.ContextCompat


class BusinessDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessDetailBinding
    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val notebookList = mutableListOf<Notebook>()
    private var currentBusinessId: String? = null
    private var currentUserRole: String = "" // "owner", "partner", "member"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBusinessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentBusinessId = intent.getStringExtra("businessId")

        database = FirebaseDatabase.getInstance().reference.child("notebooks")

        setupRecyclerView()

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchBusinessDetails()
            fetchNotebooks()
            checkUserRole()
        } else {
            // Find first available business if ID not passed
            fetchFirstBusiness()
        }

        setupClickListeners()
        setupBottomNavigation()
    }

    private fun setupClickListeners() {
        binding.addNotebookFab.setOnClickListener {
            // Logic to add new notebook
            // For now just show toast or dialog
            Toast.makeText(this, "Add Notebook Clicked", Toast.LENGTH_SHORT).show()
        }

        binding.layoutBusinessSelector.setOnClickListener {
            val bottomSheet = BusinessSelectionBottomSheet(currentBusinessId) { selectedBusiness ->
                if (selectedBusiness.id != currentBusinessId) {
                    currentBusinessId = selectedBusiness.id
                    fetchBusinessDetails()
                    fetchNotebooks()
                    checkUserRole()
                }
            }
            bottomSheet.show(supportFragmentManager, "BusinessSelectionBottomSheet")
        }

        binding.btnAddPartner.setOnClickListener {
             if (!currentBusinessId.isNullOrEmpty()) {
                 val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                 intent.putExtra("entityId", currentBusinessId)
                 intent.putExtra("entityType", "business")
                 startActivity(intent)
             }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_cashbooks -> true
                R.id.nav_payments -> {
                    Toast.makeText(this, "Payments", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_settings -> {
                    Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigation.selectedItemId = R.id.nav_cashbooks
    }

    private fun fetchFirstBusiness() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val businessRef = FirebaseDatabase.getInstance().reference.child("businesses")

        businessRef.orderByChild("ownerId").equalTo(userId).limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val firstChild = snapshot.children.first()
                        val business = firstChild.getValue(Business::class.java)
                        business?.id = firstChild.key.orEmpty()

                        if (business != null) {
                            currentBusinessId = business.id
                            fetchBusinessDetails()
                            fetchNotebooks()
                            checkUserRole()
                        }
                    } else {
                        binding.tvBusinessName.text = "No Business Found"
                        // Maybe redirect to Add Business screen
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchBusinessDetails() {
        val id = currentBusinessId ?: return
        FirebaseDatabase.getInstance().reference.child("businesses").child(id).get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.child("name").value as? String ?: "Unknown"
                binding.tvBusinessName.text = name
            }
    }

    private fun fetchNotebooks() {
        val id = currentBusinessId ?: return
        database.orderByChild("businessId").equalTo(id)
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

    // Role check simplified for brevity - assumes checks owner/partner
    private fun checkUserRole() {
        // Implement full RBAC logic as before if needed to hide/show buttons
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(notebookList,
            onClick = { notebook ->
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

    private fun showNotebookMenu(notebook: Notebook, view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)

        popup.menuInflater.inflate(R.menu.menu_notebook_options, popup.menu)

        // ---- Force icon visibility ----
        try {
            val fields = popup.javaClass.getDeclaredField("mPopup")
            fields.isAccessible = true
            val menuPopupHelper = fields.get(popup)
            val helperClass = Class.forName(menuPopupHelper.javaClass.name)
            val setForceIcons = helperClass.getMethod("setForceShowIcon", Boolean::class.java)
            setForceIcons.invoke(menuPopupHelper, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ---- Apply tint on each icon ----
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            item.icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(
                    wrapped,
                    ContextCompat.getColor(this, R.color.text_color)
                )
                item.icon = wrapped
            }
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
                    Toast.makeText(this, "Sharebook", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    inner class NotebookAdapter(
        private val notebooks: List<Notebook>,
        private val onClick: (Notebook) -> Unit,
        private val onMenuClick: (Notebook, View) -> Unit
    ) : RecyclerView.Adapter<NotebookAdapter.NotebookViewHolder>() {

        inner class NotebookViewHolder(val binding: ItemNotebookBinding) :
            RecyclerView.ViewHolder(binding.root) {
                fun bind(notebook: Notebook) {
                    binding.notebookName.text = notebook.name

                    // Format Date
                    val calendar = Calendar.getInstance(Locale.getDefault())
                    calendar.timeInMillis = notebook.createdAt
                    val dateStr = DateFormat.format("MMM dd yyyy", calendar).toString()
                    binding.notebookDetails.text = "1 Member . Updated on $dateStr"

                    // Dummy Balance logic - in real app, fetch from transactions
                    binding.tvBalance.text = "0"
                }
            }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotebookViewHolder {
            val itemBinding = ItemNotebookBinding.inflate(layoutInflater, parent, false)
            return NotebookViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
            val notebook = notebooks[position]
            holder.bind(notebook)
            holder.itemView.setOnClickListener { onClick(notebook) }
            holder.binding.btnNotebookOptions.setOnClickListener { onMenuClick(notebook, it) }
        }

        override fun getItemCount() = notebooks.size
    }
}
