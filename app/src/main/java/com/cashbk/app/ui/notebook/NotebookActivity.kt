package com.cashbk.app.ui.notebook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.data.model.Transaction
import com.cashbk.app.databinding.ActivityNotebookBinding
import com.cashbk.app.databinding.ItemTransactionBinding
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import com.cashbk.app.ui.transaction.AddTransactionFragment
import com.google.firebase.database.*
import com.cashbk.app.utils.PdfGenerator
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.util.Pair
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class NotebookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotebookBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionAdapter: TransactionAdapter
    
    // Edit Dialog handling
    private val EDIT_DIALOG_TAG = "EditTransactionDialog"

    // Data Lists and Maps
    private val allTransactionsList = mutableListOf<Transaction>()
    private val displayedTransactionList = mutableListOf<Transaction>()
    private val partyMap = mutableMapOf<String, String>()
    private val categoryMap = mutableMapOf<String, String>()
    private val userMap = mutableMapOf<String, String>()

    private var notebookId: String? = null
    private var currentUserRole: String = "" // "owner", "partner", "writer", "reader"
    private var currentNetBalance = 0.0

    // Filters
    private var filterCategoryId: String? = null
    private var filterStartDate: Long? = null
    private var filterEndDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityNotebookBinding.inflate(layoutInflater)
            setContentView(binding.root)

            notebookId = intent.getStringExtra("notebookId")
            Log.d("NotebookActivity", "Received notebookId: $notebookId")

            if (notebookId.isNullOrEmpty()) {
                Toast.makeText(this, "Notebook ID is missing.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // Initialize Firebase Reference
            database = FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId!!)

            // Setup UI
            setupRecyclerView()

            // Fetch Data
            fetchParties()
            fetchCategories()
            fetchUsers()
            fetchTransactions() // Loads transactions real-time

            // Check Access & Notebook Details
            checkUserRole()
            fetchNotebookDetails()

            // Click Listeners
            binding.btnMenuNotebook.setOnClickListener { showMenuNotebook(it) }
            binding.btnPdf.setOnClickListener {
                val nName = binding.tvTitle.text.toString()
                PdfGenerator.generatePdf(this@NotebookActivity, nName, displayedTransactionList, currentNetBalance)
            }
            binding.btnCashIn.setOnClickListener { showAddTransactionDialog("in") }
            binding.btnCashOut.setOnClickListener { showAddTransactionDialog("out") }
            binding.btnBack.setOnClickListener { finish() }

            binding.chipDate.setOnClickListener { showDateRangePicker() }
            binding.chipFilter.setOnClickListener { showCategoryFilterDialog() }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("NotebookActivity", "Crash in onCreate", e)
        }
    }

    /**
     * This function updates the names in the transaction list
     * using the data currently available in partyMap and categoryMap,
     * then refreshes the RecyclerView.
     */
    private fun refreshTransactionData() {
        if (displayedTransactionList.isEmpty()) {
            binding.layoutEmptyTransactions.visibility = android.view.View.VISIBLE
            binding.transactionsRecyclerView.visibility = android.view.View.GONE
            transactionAdapter.notifyDataSetChanged()
            return
        } else {
            binding.layoutEmptyTransactions.visibility = android.view.View.GONE
            binding.transactionsRecyclerView.visibility = android.view.View.VISIBLE
        }

        displayedTransactionList.forEach { t ->
            // Update Party Name
            t.partyName = partyMap[t.partyId] ?: ""

            // Update Category Name
            t.categoryName = categoryMap[t.categoryId] ?: ""

            // Update CreatedBy Name
            t.createdByName = userMap[t.createdBy] ?: "You"
        }

        transactionAdapter.notifyDataSetChanged()
    }

    private fun fetchNotebookDetails() {
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { snapshot ->
                val notebook = snapshot.getValue(Notebook::class.java)
                binding.tvTitle.text = notebook?.name ?: "Notebook"
                binding.tvSubtitle.text = "Manage your daily entries"
            }
    }

    private fun fetchParties() {
        FirebaseDatabase.getInstance().reference
            .child("parties")
            .child(notebookId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    partyMap.clear()
                    snapshot.children.forEach {
                        val id = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: ""
                        partyMap[id] = name
                    }
                    // FIX: Refresh list immediately after parties are loaded
                    refreshTransactionData()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchCategories() {
        FirebaseDatabase.getInstance().reference
            .child("categories")
            .child(notebookId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    categoryMap.clear()
                    snapshot.children.forEach {
                        val id = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: ""
                        categoryMap[id] = name
                    }
                    // FIX: Refresh list immediately after categories are loaded
                    refreshTransactionData()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchUsers() {
        FirebaseDatabase.getInstance().reference
            .child("users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userMap.clear()
                    snapshot.children.forEach {
                        val uid = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: "You"
                        userMap[uid] = name
                    }
                    // FIX: Refresh list immediately after users are loaded
                    refreshTransactionData()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchTransactions() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempTransactions = mutableListOf<Transaction>()
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        try {
                            val t = child.getValue(Transaction::class.java)
                            t?.id = child.key.orEmpty()
                            t?.let { tempTransactions.add(it) }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                // 1. Sort by CreatedAt ASC to calculate running balance correctly
                tempTransactions.sortBy { getTransactionDateTime(it) }


                var balance = 0.0
                var totalIn = 0.0
                var totalOut = 0.0

                for (t in tempTransactions) {

                    // Resolve names
                    t.partyName = partyMap[t.partyId] ?: ""
                    t.categoryName = categoryMap[t.categoryId] ?: ""
                    t.createdByName = userMap[t.createdBy] ?: "You"

                    val amount = t.amount   // ✅ IMPORTANT

                    if (t.type == "in") {
                        balance += amount
                        totalIn += amount
                    } else {
                        balance -= amount
                        totalOut += amount
                    }

                    t.runningBalance = balance
                }

                currentNetBalance = balance
                // UI summary now updated in applyFilters()
                // updateSummaryUI(balance, totalIn, totalOut, tempTransactions.size)

                // 2. Sort DESC for display (Newest top)
                tempTransactions.reverse()

                // 3. Update main list
                allTransactionsList.clear()
                allTransactionsList.addAll(tempTransactions)

                // 4. Resolve Names (Party/Category) and Apply Filters
                applyFilters()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@NotebookActivity, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateSummaryUI(balance: Double, totalIn: Double, totalOut: Double, count: Int) {
        binding.tvNetBalance.text = formatCurrency(balance)

        if (balance >= 0) {
            binding.tvNetBalance.setTextColor(ContextCompat.getColor(this, R.color.success))
        } else {
            binding.tvNetBalance.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }

        binding.tvTotalIn.text = "+ ${formatCurrency(totalIn)}"
        binding.tvTotalOut.text = "- ${formatCurrency(totalOut)}"
        binding.tvEntriesCount.text = "Showing $count entries"
    }

    private fun applyFilters() {
        var filteredList = allTransactionsList.toList()

        if (filterStartDate != null && filterEndDate != null) {
            filteredList = filteredList.filter { t ->
                val time = getTransactionDateTime(t)
                time in filterStartDate!!..filterEndDate!!
            }
        }

        if (filterCategoryId != null) {
            filteredList = filteredList.filter { t ->
                t.categoryId == filterCategoryId
            }
        }

        // Calculate filtered summary
        var fIn = 0.0
        var fOut = 0.0
        for (t in filteredList) {
            if (t.type == "in") fIn += t.amount else fOut += t.amount
        }
        val fBalance = fIn - fOut
        
        updateSummaryUI(fBalance, fIn, fOut, filteredList.size)

        displayedTransactionList.clear()
        displayedTransactionList.addAll(filteredList)
        
        refreshTransactionData()
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Dates")
            .build()
            
        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            filterStartDate = selection.first
            // End of day
            filterEndDate = selection.second + 86399999L 
            
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            binding.chipDate.text = "${sdf.format(Date(filterStartDate!!))} - ${sdf.format(Date(filterEndDate!!))}"
            applyFilters()
        }
            
        dateRangePicker.show(supportFragmentManager, "DateRangePicker")
    }

    private fun showCategoryFilterDialog() {
        val categories = categoryMap.values.toTypedArray()
        val categoryIds = categoryMap.keys.toTypedArray()
        
        if (categories.isEmpty()) {
            Toast.makeText(this, "No categories found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Let's add an option to clear filter at index 0
        val displayOptions = arrayOf("All Categories") + categories
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Filter by Category")
            .setItems(displayOptions) { _, which ->
                if (which == 0) {
                    filterCategoryId = null
                    binding.chipFilter.text = "Filter"
                } else {
                    filterCategoryId = categoryIds[which - 1]
                    binding.chipFilter.text = categories[which - 1]
                }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTransactionDialog(type: String) {
        if (currentUserRole == "reader") {
            Toast.makeText(this, "You have view-only access", Toast.LENGTH_SHORT).show()
            return
        }
        val fragment = AddTransactionFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("transactionType", type)
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, "AddTransactionFragment")
            .addToBackStack(null)
            .commit()
    }
    
    private fun showEditTransactionDialog(transaction: Transaction) {
        if (currentUserRole == "reader") {
            Toast.makeText(this, "You have view-only access", Toast.LENGTH_SHORT).show()
            return
        }
        val fragment = AddTransactionFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("transactionId", transaction.id)
        args.putDouble("amount", transaction.amount)
        args.putString("remark", transaction.remark)
        args.putString("date", transaction.date)
        args.putString("time", transaction.time)
        args.putString("type", transaction.type)
        args.putString("categoryId", transaction.categoryId)
        args.putString("partyId", transaction.partyId)
        
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, EDIT_DIALOG_TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun getTransactionDateTime(transaction: Transaction): Long {
        return try {
            val sdf = java.text.SimpleDateFormat(
                "yyyy-MM-dd hh:mm a",
                Locale.getDefault()
            )
            val dateTime = "${transaction.date} ${transaction.time}"
            sdf.parse(dateTime)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(displayedTransactionList) { transaction ->
            // Item click (e.g. open details)
            Log.d("NotebookActivity", "Clicked transaction: ${transaction.id}")
        }

        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotebookActivity)
            adapter = transactionAdapter
            setHasFixedSize(true)
        }
    }

    private fun showMenu(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_notebook_options, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                     if (currentUserRole == "reader") {
                         Toast.makeText(this, "You have view-only access", Toast.LENGTH_SHORT).show()
                     } else {
                         Toast.makeText(this, "Rename clicked", Toast.LENGTH_SHORT).show()
                     }
                     true
                }
                R.id.action_delete -> {
                     if (currentUserRole != "owner") {
                         Toast.makeText(this, "Only the business owner can delete this notebook", Toast.LENGTH_SHORT).show()
                     } else {
                         Toast.makeText(this, "Delete clicked", Toast.LENGTH_SHORT).show()
                     }
                     true
                }
                R.id.action_share -> {
                    val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                    intent.putExtra("entityId", notebookId)
                    intent.putExtra("entityType", "notebook")
                    intent.putExtra("currentUserRole", currentUserRole)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showMenuNotebook(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_notebook_more, popup.menu)

        // Force show icons via reflection
        try {
            val field = popup.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            val clazz = Class.forName(helper.javaClass.name)
            val setForceIcons = clazz.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
            setForceIcons.invoke(helper, true)
        } catch (e: Exception) { e.printStackTrace() }

        // Tint icons
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            item.icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.text_color))
                item.icon = wrapped
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> { Toast.makeText(this, "Rename clicked", Toast.LENGTH_SHORT).show(); true }
                R.id.action_report -> {
                    val nName = binding.tvTitle.text.toString()
                    PdfGenerator.generatePdf(this, nName, displayedTransactionList, currentNetBalance)
                    true
                }
                R.id.action_member -> {
                    val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                    intent.putExtra("entityId", notebookId)
                    intent.putExtra("entityType", "notebook")
                    intent.putExtra("currentUserRole", currentUserRole)
                    startActivity(intent)
                    true
                }
                R.id.action_settings -> { Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { notebookSnapshot ->
                val businessId = notebookSnapshot.child("businessId").value as? String ?: return@addOnSuccessListener

                FirebaseDatabase.getInstance().reference.child("businesses").child(businessId).get()
                    .addOnSuccessListener { businessSnapshot ->
                        val ownerId = businessSnapshot.child("ownerId").value as? String
                        if (ownerId == currentUserId) {
                            currentUserRole = "owner"
                            updateUIForRole()
                        } else {
                            FirebaseDatabase.getInstance().reference.child("business_members")
                                .child(businessId).child(currentUserId).get().addOnSuccessListener { pSnap ->
                                    if (pSnap.exists() && pSnap.child("role").value == "partner") {
                                        currentUserRole = "partner"
                                        updateUIForRole()
                                    } else {
                                        FirebaseDatabase.getInstance().reference.child("members")
                                            .child(notebookId!!).child(currentUserId).get().addOnSuccessListener { mSnap ->
                                                if (mSnap.exists()) {
                                                    currentUserRole = mSnap.child("role").value as? String ?: ""
                                                    updateUIForRole()
                                                }
                                            }
                                    }
                                }
                        }
                    }
            }
    }

    private fun updateUIForRole() {
        if (currentUserRole == "reader") {
            binding.btnCashIn.alpha = 0.5f
            binding.btnCashOut.alpha = 0.5f
        } else {
            binding.btnCashIn.alpha = 1.0f
            binding.btnCashOut.alpha = 1.0f
        }
    }

    private fun showTransactionPopupMenu(anchor: View, transaction: Transaction) {
        val wrapper = ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.menu_transaction_options, popup.menu)

        // Force show icons via reflection
        try {
            val field = popup.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            val clazz = Class.forName(helper.javaClass.name)
            val setForceIcons = clazz.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
            setForceIcons.invoke(helper, true)
        } catch (e: Exception) { e.printStackTrace() }

        // Tint icons
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            item.icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.text_color))
                item.icon = wrapped
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    deleteTransaction(transaction)
                    true
                }
                R.id.action_edit -> {
                    showEditTransactionDialog(transaction)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun deleteTransaction(transaction: Transaction) {
        database.child(transaction.id).removeValue()
            .addOnSuccessListener { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show() }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
    }

    // --- INNER ADAPTER CLASS ---
    inner class TransactionAdapter(
        private val transactions: List<Transaction>,
        private val onClick: (Transaction) -> Unit
    ) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

        inner class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return TransactionViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
            val transaction = transactions[position]
            val context = holder.itemView.context

            // DATE HEADER logic
            val showHeader = position == 0 || transaction.date != transactions[position - 1].date
            if (showHeader) {
                holder.binding.tvDateHeader.visibility = View.VISIBLE
                holder.binding.tvDateHeader.text = transaction.date
            } else {
                holder.binding.tvDateHeader.visibility = View.GONE
            }

            // REMARK
            holder.binding.tvRemark.text = if (transaction.remark.isNotEmpty()) transaction.remark else "Transaction"

            // PARTY NAME logic (Correctly hiding if empty)
            if (transaction.partyName.isNotEmpty()) {
                holder.binding.tvParty.text = transaction.partyName
                holder.binding.tvParty.visibility = View.VISIBLE
            } else {
                // If you want to show "Party" when no name exists, use:
                // holder.binding.tvParty.text = "Party"
                // holder.binding.tvParty.visibility = View.VISIBLE

                // OR if you want to hide it completely:
                holder.binding.tvParty.visibility = View.GONE
            }

            // CATEGORY CHIP logic
            if (transaction.categoryName.isNotEmpty()) {
                holder.binding.tvCategoryChip.text = transaction.categoryName
                holder.binding.tvCategoryChip.visibility = View.VISIBLE
            } else {
                holder.binding.tvCategoryChip.visibility = View.GONE
            }

            // AMOUNT COLORING
            val amount = transaction.amount
            holder.binding.tvAmount.text = formatCurrency(amount)
            holder.binding.tvAmount.setTextColor(
                ContextCompat.getColor(context, if (transaction.type == "in") R.color.success else R.color.danger)
            )

            // RUNNING BALANCE
            holder.binding.tvBalanceAfter.text = "Balance: ${formatCurrency(transaction.runningBalance)}"

            // ENTRY INFO
            val entryBy = if (transaction.createdByName.isNotEmpty()) transaction.createdByName else "You"
            holder.binding.tvEntryInfo.text = "Entry by $entryBy at ${transaction.time}"

            // CLICKS
            holder.itemView.setOnClickListener { onClick(transaction) }
            holder.itemView.setOnLongClickListener {
                if (currentUserRole != "reader") {
                    showTransactionPopupMenu(it, transaction)
                }
                true
            }
        }
        override fun getItemCount() = transactions.size
    }
}