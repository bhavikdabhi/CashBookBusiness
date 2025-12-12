package com.cashbk.app.ui.dashboard

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.data.model.Business
import com.cashbk.app.databinding.ActivityDashboardBinding
import com.cashbk.app.databinding.ItemBusinessBinding
import com.cashbk.app.fragment.AddBusinessFragment
import com.cashbk.app.ui.business.BusinessDetailActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var businessAdapter: BusinessAdapter
    private val businessList = mutableListOf<Business>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("businesses")

        setupRecyclerView()
        fetchBusinesses()

        // Open Add Business Popup
        binding.addBusinessFab.setOnClickListener {
            AddBusinessFragment().show(supportFragmentManager, "AddBusinessDialogFragment")
        }
    }

    // ------------------------------------------
    // SETUP RECYCLER VIEW
    // ------------------------------------------
    private fun setupRecyclerView() {
        businessAdapter = BusinessAdapter(
            businessList,
            onClick = { business ->
                val intent = Intent(this, BusinessDetailActivity::class.java)
                intent.putExtra("businessId", business.id)
                startActivity(intent)
            },
            onRename = { renameBusiness(it) },
            onDelete = { deleteBusiness(it) },
            onManagePartner = { openPartnerManager(it) }
        )

        binding.businessesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = businessAdapter
        }
    }

    // ------------------------------------------
    // FETCH ALL BUSINESSES FOR CURRENT USER
    // ------------------------------------------
    private fun fetchBusinesses() {
        val userId = auth.currentUser?.uid ?: return

        database.orderByChild("ownerId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    businessList.clear()

                    for (businessSnapshot in snapshot.children) {
                        val business = businessSnapshot.getValue(Business::class.java)
                        business?.id = businessSnapshot.key.orEmpty()
                        business?.let { businessList.add(it) }
                    }

                    businessAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Failed to load businesses: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // ------------------------------------------
    // RENAME BUSINESS
    // ------------------------------------------
    private fun renameBusiness(business: Business) {
        val editText = EditText(this)
        editText.setText(business.name)

        AlertDialog.Builder(this)
            .setTitle("Rename Business")
            .setView(editText)
            .setPositiveButton("Update") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    FirebaseDatabase.getInstance().reference
                        .child("businesses")
                        .child(business.id)
                        .child("name")
                        .setValue(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------
    // DELETE BUSINESS
    // ------------------------------------------
    private fun deleteBusiness(business: Business) {
        AlertDialog.Builder(this)
            .setTitle("Delete Business")
            .setMessage("Do you really want to delete this business?")
            .setPositiveButton("Delete") { _, _ ->
                FirebaseDatabase.getInstance().reference
                    .child("businesses")
                    .child(business.id)
                    .removeValue()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------
    // MANAGE PARTNER
    // ------------------------------------------
    private fun openPartnerManager(business: Business) {
        Toast.makeText(this, "Manage Partner for: ${business.name}", Toast.LENGTH_SHORT).show()
    }

    // ========================================================================
    // BUSINESS ADAPTER (WORKING WITH THREE DOT MENU)
    // ========================================================================
    inner class BusinessAdapter(
        private val businesses: List<Business>,
        private val onClick: (Business) -> Unit,
        private val onRename: (Business) -> Unit,
        private val onDelete: (Business) -> Unit,
        private val onManagePartner: (Business) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<BusinessAdapter.BusinessViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BusinessViewHolder {
            val itemBinding = ItemBusinessBinding.inflate(layoutInflater, parent, false)
            return BusinessViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: BusinessViewHolder, position: Int) {
            val business = businesses[position]
            holder.bind(business)
        }

        override fun getItemCount(): Int = businesses.size

        inner class BusinessViewHolder(private val itemBinding: ItemBusinessBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(business: Business) {

                itemBinding.businessName.text = business.name

                // Click open details
                itemBinding.root.setOnClickListener { onClick(business) }

                // 3 DOT MENU CLICK
                itemBinding.menuMore.setOnClickListener {
                    val popup = androidx.appcompat.widget.PopupMenu(this@DashboardActivity, it)
                    popup.inflate(com.cashbk.app.R.menu.menu_business_options)

                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {

                            com.cashbk.app.R.id.action_rename -> {
                                onRename(business)
                                true
                            }

                            com.cashbk.app.R.id.action_delete -> {
                                onDelete(business)
                                true
                            }

                            com.cashbk.app.R.id.action_manage_partner -> {
                                onManagePartner(business)
                                true
                            }

                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }
    }
}