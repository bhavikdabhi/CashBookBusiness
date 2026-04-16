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
        binding.chipDate.setOnClickListener { showDateRangePicker() }
        binding.chipFilter.setOnClickListener { showCategoryFilterDialog() }
        
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

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Dates")
            .build()
            
        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            if (_binding == null) return@addOnPositiveButtonClickListener
            filterStartDate = selection.first
            filterEndDate = selection.second + 86399999L 
            
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            binding.chipDate.text = "${sdf.format(Date(filterStartDate!!))} - ${sdf.format(Date(filterEndDate!!))}"
            applyFilters()
        }
            
        dateRangePicker.show(parentFragmentManager, "DateRangePicker")
    }

    private fun showCategoryFilterDialog() {
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
