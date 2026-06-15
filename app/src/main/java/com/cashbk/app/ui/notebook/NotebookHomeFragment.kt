package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.adapter.TransactionAdapter
import com.cashbk.app.databinding.FragmentNotebookHomeBinding
import com.cashbk.app.dataclass.Transaction
import com.cashbk.app.ui.transaction.AddTransactionFragment
import com.cashbk.app.utils.PdfGenerator
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.cashbk.app.adapter.FilterAdapter
import com.cashbk.app.adapter.FilterItem

class NotebookHomeFragment : Fragment() {

    private var _binding: FragmentNotebookHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var transactionAdapter: TransactionAdapter

    private val allTransactionsList = mutableListOf<Transaction>()
    private val displayedTransactionList = mutableListOf<Transaction>()
    private val partyMap = mutableMapOf<String, String>()
    private val categoryMap = mutableMapOf<String, String>()
    private val userMap = mutableMapOf<String, String>()

    private var notebookId: String? = null
    private var currentUserRole: String = ""
    private var currentNetBalance = 0.0

    private var filterCategoryId: String? = null
    private var filterStartDate: Long? = null
    private var filterEndDate: Long? = null
    private var filterPartyId: String? = null
    private var filterType: String? = null
    private var filterEntryBy: String? = null

    private lateinit var filterAdapter: FilterAdapter
    private val filterItemList = mutableListOf(
        FilterItem("date", "Date"),
        FilterItem("category", "Category"),
        FilterItem("party", "Party"),
        FilterItem("type", "Type"),
        FilterItem("entry_by", "Entry By")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotebookHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        currentUserRole = arguments?.getString("currentUserRole") ?: (requireActivity() as? NotebookActivity)?.getUserRole() ?: ""

        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId!!)

        setupRecyclerView()
        fetchParties()
        fetchCategories()
        fetchUsers()
        fetchTransactions()
        fetchNotebookDetails()

        binding.btnBack.setOnClickListener { requireActivity().finish() }
        binding.btnMenuNotebook.setOnClickListener { showMenuNotebook(it) }
        binding.btnPdf.setOnClickListener {
            val notebookName = binding.tvTitle.text.toString()
            PdfGenerator.generatePdf(requireActivity(), notebookName, displayedTransactionList, currentNetBalance)
        }

        binding.btnCashIn.setOnClickListener { showAddTransactionDialog("in") }
        binding.btnCashOut.setOnClickListener { showAddTransactionDialog("out") }
        setupFilterRecyclerView()
        
        binding.btnViewReports.setOnClickListener {
            val notebookName = binding.tvTitle.text.toString()
            PdfGenerator.generatePdf(requireActivity(), notebookName, displayedTransactionList, currentNetBalance)
        }

        updateUIForRole()
    }

    private fun fetchNotebookDetails() {
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val notebook = snapshot.getValue(com.cashbk.app.dataclass.Notebook::class.java)
                binding.tvTitle.text = notebook?.name ?: "Notebook"
                binding.tvSubtitle.text = "Manage your daily entries"
            }
    }

    private fun showMenuNotebook(view: View) {
        val popup = com.cashbk.app.utils.CustomOptionsMenu(requireContext(), view)
        popup.setOnMemberClickListener {
            val intent = android.content.Intent(requireContext(), com.cashbk.app.ui.members.MembersActivity::class.java)
            intent.putExtra("entityId", notebookId)
            intent.putExtra("entityType", "notebook")
            intent.putExtra("currentUserRole", currentUserRole)
            startActivity(intent)
        }
        popup.show()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(displayedTransactionList,
            onClick = { transaction ->
                Log.d("NotebookHomeFragment", "Clicked transaction: ${transaction.id}")
            },
            onLongClick = { view, transaction ->
                if (currentUserRole != "reader") {
                    (requireActivity() as? NotebookActivity)?.showTransactionPopupMenu(view, transaction)
                }
            }
        )

        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun fetchParties() {
        FirebaseDatabase.getInstance().reference
            .child("parties")
            .child(notebookId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return // Safety check
                    partyMap.clear()
                    snapshot.children.forEach {
                        val id = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: ""
                        partyMap[id] = name
                    }
                    refreshTransactionData()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchCategories() {
        FirebaseDatabase.getInstance().reference
            .child("categories")
            .child(notebookId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return // Safety check
                    categoryMap.clear()
                    // Always include the built-in General category
                    categoryMap["general"] = "General"
                    snapshot.children.forEach {
                        val id = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: ""
                        categoryMap[id] = name
                    }
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
                    if (_binding == null) return // Safety check
                    userMap.clear()
                    snapshot.children.forEach {
                        val uid = it.key ?: return@forEach
                        val name = it.child("name").getValue(String::class.java) ?: "You"
                        userMap[uid] = name
                    }
                    refreshTransactionData()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchTransactions() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return // Safety check
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

                tempTransactions.sortBy { getTransactionDateTime(it) }

                var balance = 0.0
                var totalIn = 0.0
                var totalOut = 0.0

                for (t in tempTransactions) {
                    t.partyName = partyMap[t.partyId] ?: ""
                    t.categoryName = categoryMap[t.categoryId] ?: ""
                    t.createdByName = userMap[t.createdBy] ?: "You"

                    if (t.type == "in") {
                        balance += t.amount
                        totalIn += t.amount
                    } else {
                        balance -= t.amount
                        totalOut += t.amount
                    }
                    t.runningBalance = balance
                }

                currentNetBalance = balance
                tempTransactions.reverse()
                allTransactionsList.clear()
                allTransactionsList.addAll(tempTransactions)
                applyFilters()
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun applyFilters() {
        if (_binding == null) return
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
        if (filterPartyId != null) {
            filteredList = filteredList.filter { t -> t.partyId == filterPartyId }
        }
        if (filterType != null) {
            filteredList = filteredList.filter { t -> t.type == filterType }
        }
        if (filterEntryBy != null) {
            filteredList = filteredList.filter { t -> t.createdBy == filterEntryBy }
        }

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

    private fun refreshTransactionData() {
        if (_binding == null) return
        if (displayedTransactionList.isEmpty()) {
            binding.layoutEmptyTransactions.visibility = View.VISIBLE
            binding.transactionsRecyclerView.visibility = View.GONE
        } else {
            binding.layoutEmptyTransactions.visibility = View.GONE
            binding.transactionsRecyclerView.visibility = View.VISIBLE
        }

        displayedTransactionList.forEach { t ->
            // Use map lookup first, fall back to stored name in the transaction object
            t.partyName = partyMap[t.partyId] ?: t.partyName.ifEmpty { t.partyId }
            t.categoryName = categoryMap[t.categoryId] ?: t.categoryName.ifEmpty { t.categoryId }
            t.createdByName = userMap[t.createdBy] ?: "You"
        }

        transactionAdapter.notifyDataSetChanged()
    }

    private fun updateSummaryUI(balance: Double, totalIn: Double, totalOut: Double, count: Int) {
        if (_binding == null) return
        binding.tvNetBalance.text = formatCurrency(balance)
        binding.tvNetBalance.setTextColor(
            ContextCompat.getColor(requireContext(), if (balance >= 0) R.color.success else R.color.danger)
        )
        binding.tvTotalIn.text = "+ ${formatCurrency(totalIn)}"
        binding.tvTotalOut.text = "- ${formatCurrency(totalOut)}"
        binding.tvEntriesCount.text = "Showing $count entries"
    }

    private fun setupFilterRecyclerView() {
        filterAdapter = FilterAdapter(filterItemList) { item, position ->
            when (item.type) {
                "date" -> showDateFilterDialog(position)
                "category" -> showCategoryFilterDialog(position)
                "party" -> showPartyFilterDialog(position)
                "type" -> showTypeFilterDialog(position)
                "entry_by" -> showEntryByFilterDialog(position)
            }
        }
        binding.filterRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
        }
    }

    private fun showDateFilterDialog(position: Int) {
        val options = arrayOf("All Time", "Today", "Yesterday", "This Month", "Last Month", "This Year", "Custom")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Date")
            .setItems(options) { _, which ->
                val calendar = Calendar.getInstance()
                when (which) {
                    0 -> { // All Time
                        filterStartDate = null
                        filterEndDate = null
                        filterAdapter.updateItem(position, "Date")
                        applyFilters()
                    }
                    1 -> { // Today
                        setStartOfDay(calendar)
                        filterStartDate = calendar.timeInMillis
                        setEndOfDay(calendar)
                        filterEndDate = calendar.timeInMillis
                        filterAdapter.updateItem(position, "Today")
                        applyFilters()
                    }
                    2 -> { // Yesterday
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        setStartOfDay(calendar)
                        filterStartDate = calendar.timeInMillis
                        setEndOfDay(calendar)
                        filterEndDate = calendar.timeInMillis
                        filterAdapter.updateItem(position, "Yesterday")
                        applyFilters()
                    }
                    3 -> { // This Month
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        setStartOfDay(calendar)
                        filterStartDate = calendar.timeInMillis
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        setEndOfDay(calendar)
                        filterEndDate = calendar.timeInMillis
                        filterAdapter.updateItem(position, "This Month")
                        applyFilters()
                    }
                    4 -> { // Last Month
                        calendar.add(Calendar.MONTH, -1)
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        setStartOfDay(calendar)
                        filterStartDate = calendar.timeInMillis
                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        setEndOfDay(calendar)
                        filterEndDate = calendar.timeInMillis
                        filterAdapter.updateItem(position, "Last Month")
                        applyFilters()
                    }
                    5 -> { // This Year
                        calendar.set(Calendar.DAY_OF_YEAR, 1)
                        setStartOfDay(calendar)
                        filterStartDate = calendar.timeInMillis
                        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                        calendar.set(Calendar.DAY_OF_MONTH, 31)
                        setEndOfDay(calendar)
                        filterEndDate = calendar.timeInMillis
                        filterAdapter.updateItem(position, "This Year")
                        applyFilters()
                    }
                    6 -> { // Custom
                        showDateRangePicker(position)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setStartOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun setEndOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
    }

    private fun showDateRangePicker(position: Int) {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Dates")
            .build()
            
        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            if (_binding == null) return@addOnPositiveButtonClickListener
            filterStartDate = selection.first
            filterEndDate = selection.second + 86399999L 
            
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            filterAdapter.updateItem(position, "${sdf.format(Date(filterStartDate!!))} - ${sdf.format(Date(filterEndDate!!))}")
            applyFilters()
        }
            
        dateRangePicker.show(parentFragmentManager, "DateRangePicker")
    }

    private fun showCategoryFilterDialog(position: Int) {
        val categories = categoryMap.values.toTypedArray()
        val categoryIds = categoryMap.keys.toTypedArray()
        
        if (categories.isEmpty()) {
            Toast.makeText(requireContext(), "No categories found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val displayOptions = arrayOf("All Categories") + categories
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Category")
            .setItems(displayOptions) { _, which ->
                if (_binding == null) return@setItems
                if (which == 0) {
                    filterCategoryId = null
                    filterAdapter.updateItem(position, "Category")
                } else {
                    filterCategoryId = categoryIds[which - 1]
                    filterAdapter.updateItem(position, categories[which - 1])
                }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPartyFilterDialog(position: Int) {
        val parties = partyMap.values.toTypedArray()
        val partyIds = partyMap.keys.toTypedArray()
        
        if (parties.isEmpty()) {
            Toast.makeText(requireContext(), "No parties found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val displayOptions = arrayOf("All Parties") + parties
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Party")
            .setItems(displayOptions) { _, which ->
                if (_binding == null) return@setItems
                if (which == 0) {
                    filterPartyId = null
                    filterAdapter.updateItem(position, "Party")
                } else {
                    filterPartyId = partyIds[which - 1]
                    filterAdapter.updateItem(position, parties[which - 1])
                }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTypeFilterDialog(position: Int) {
        val options = arrayOf("All Types", "Cash In", "Cash Out")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Type")
            .setItems(options) { _, which ->
                if (_binding == null) return@setItems
                when (which) {
                    0 -> {
                        filterType = null
                        filterAdapter.updateItem(position, "Type")
                    }
                    1 -> {
                        filterType = "in"
                        filterAdapter.updateItem(position, "Cash In")
                    }
                    2 -> {
                        filterType = "out"
                        filterAdapter.updateItem(position, "Cash Out")
                    }
                }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEntryByFilterDialog(position: Int) {
        val users = userMap.values.toTypedArray()
        val userIds = userMap.keys.toTypedArray()
        
        if (users.isEmpty()) {
            Toast.makeText(requireContext(), "No members found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val displayOptions = arrayOf("All Members") + users
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter by Entry By")
            .setItems(displayOptions) { _, which ->
                if (_binding == null) return@setItems
                if (which == 0) {
                    filterEntryBy = null
                    filterAdapter.updateItem(position, "Entry By")
                } else {
                    filterEntryBy = userIds[which - 1]
                    filterAdapter.updateItem(position, users[which - 1])
                }
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTransactionDialog(type: String) {
        if (currentUserRole == "reader") {
            Toast.makeText(requireContext(), "You have view-only access", Toast.LENGTH_SHORT).show()
            return
        }
        val fragment = AddTransactionFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("transactionType", type)
        fragment.arguments = args
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.notebook_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun getTransactionDateTime(transaction: Transaction): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
            val dateTime = "${transaction.date} ${transaction.time}"
            sdf.parse(dateTime)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
    }

    private fun updateUIForRole() {
        if (_binding == null) return
        if (currentUserRole == "reader") {
            binding.btnCashIn.alpha = 0.5f
            binding.btnCashOut.alpha = 0.5f
        } else {
            binding.btnCashIn.alpha = 1.0f
            binding.btnCashOut.alpha = 1.0f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
