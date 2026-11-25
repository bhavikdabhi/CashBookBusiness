package com.cashbk.app.notebook

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.transaction.adapter.TransactionAdapter
import com.cashbk.app.databinding.FragmentNotebookHomeBinding
import com.cashbk.app.transaction._bean.Transaction
import com.cashbk.app.transaction.AddTransactionFragment
import com.cashbk.app.utils.PdfGenerator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.cashbk.app.notebook.adapter.FilterAdapter
import com.cashbk.app.notebook.adapter.FilterItem
import com.cashbk.app.notebook._bean.Notebook
import com.cashbk.app.members.MembersActivity
import com.cashbk.app.utils.CustomOptionsMenu
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation

import com.cashbk.app.utils.showToast

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
    private var isFabMenuOpen = false

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
            showToast("Notebook ID missing")
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

        setupFabMenu()
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
                val notebook = snapshot.getValue(Notebook::class.java)
                binding.tvTitle.text = notebook?.name ?: "Notebook"
                binding.tvSubtitle.text = "Manage your daily entries"
            }
    }

    private fun showMenuNotebook(view: View) {
        val popup = CustomOptionsMenu(requireContext(), view)
        popup.setOnMemberClickListener {
            val intent = Intent(requireContext(), MembersActivity::class.java)
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
        if (_binding != null) {
            binding.layoutShimmerTransactions.visibility = View.VISIBLE
            binding.layoutShimmerTransactions.startPulseAnimation()
            binding.transactionsRecyclerView.visibility = View.GONE
            binding.layoutEmptyTransactions.visibility = View.GONE
        }

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

                // Artificially delay hiding the shimmer by 2 seconds so the effect is visible
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerTransactions.stopPulseAnimation()
                    binding.layoutShimmerTransactions.visibility = View.GONE
                    applyFilters()
                }, 2000)
            }

            override fun onCancelled(error: DatabaseError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerTransactions.stopPulseAnimation()
                    binding.layoutShimmerTransactions.visibility = View.GONE
                    showToast("Failed to load: ${error.message}")
                }, 2000)
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
            binding.layoutEmptyTransactions.visibility = View.GONE
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
        val currentText = filterItemList[position].displayText
        val selectedIndex = when {
            filterStartDate == null -> 0
            currentText in options -> options.indexOf(currentText)
            else -> 6
        }
        showFilterOptionsBottomSheet("Filter by Date", options, selectedIndex) { which ->
            val calendar = Calendar.getInstance()
            when (which) {
                0 -> { // All Time
                    filterStartDate = null
                    filterEndDate = null
                    filterAdapter.updateItem(position, "Date", false)
                    applyFilters()
                }
                1 -> { // Today
                    setStartOfDay(calendar)
                    filterStartDate = calendar.timeInMillis
                    setEndOfDay(calendar)
                    filterEndDate = calendar.timeInMillis
                    filterAdapter.updateItem(position, "Today", true)
                    applyFilters()
                }
                2 -> { // Yesterday
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    setStartOfDay(calendar)
                    filterStartDate = calendar.timeInMillis
                    setEndOfDay(calendar)
                    filterEndDate = calendar.timeInMillis
                    filterAdapter.updateItem(position, "Yesterday", true)
                    applyFilters()
                }
                3 -> { // This Month
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    setStartOfDay(calendar)
                    filterStartDate = calendar.timeInMillis
                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    setEndOfDay(calendar)
                    filterEndDate = calendar.timeInMillis
                    filterAdapter.updateItem(position, "This Month", true)
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
                    filterAdapter.updateItem(position, "Last Month", true)
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
                    filterAdapter.updateItem(position, "This Year", true)
                    applyFilters()
                }
                6 -> { // Custom
                    showDateRangePicker(position)
                }
            }
        }
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
            filterAdapter.updateItem(position, "${sdf.format(Date(filterStartDate!!))} - ${sdf.format(Date(filterEndDate!!))}", true)
            applyFilters()
        }
            
        dateRangePicker.show(parentFragmentManager, "DateRangePicker")
    }

    private fun showCategoryFilterDialog(position: Int) {
        val categories = categoryMap.values.toTypedArray()
        val categoryIds = categoryMap.keys.toTypedArray()

        if (categories.isEmpty()) {
            showToast("No categories found")
            return
        }

        val displayOptions = arrayOf("All Categories") + categories
        val selectedIndex = if (filterCategoryId == null) 0 else (categoryIds.indexOf(filterCategoryId) + 1).coerceAtLeast(0)

        showFilterOptionsBottomSheet("Filter by Category", displayOptions, selectedIndex) { which ->
            if (_binding == null) return@showFilterOptionsBottomSheet
            if (which == 0) {
                filterCategoryId = null
                filterAdapter.updateItem(position, "Category", false)
            } else {
                filterCategoryId = categoryIds[which - 1]
                filterAdapter.updateItem(position, categories[which - 1], true)
            }
            applyFilters()
        }
    }

    private fun showPartyFilterDialog(position: Int) {
        val parties = partyMap.values.toTypedArray()
        val partyIds = partyMap.keys.toTypedArray()

        if (parties.isEmpty()) {
            showToast("No parties found")
            return
        }

        val displayOptions = arrayOf("All Parties") + parties
        val selectedIndex = if (filterPartyId == null) 0 else (partyIds.indexOf(filterPartyId) + 1).coerceAtLeast(0)

        showFilterOptionsBottomSheet("Filter by Party", displayOptions, selectedIndex) { which ->
            if (_binding == null) return@showFilterOptionsBottomSheet
            if (which == 0) {
                filterPartyId = null
                filterAdapter.updateItem(position, "Party", false)
            } else {
                filterPartyId = partyIds[which - 1]
                filterAdapter.updateItem(position, parties[which - 1], true)
            }
            applyFilters()
        }
    }

    private fun showTypeFilterDialog(position: Int) {
        val options = arrayOf("All Types", "Cash In", "Cash Out")
        val selectedIndex = when (filterType) {
            "in" -> 1
            "out" -> 2
            else -> 0
        }
        showFilterOptionsBottomSheet("Filter by Type", options, selectedIndex) { which ->
            if (_binding == null) return@showFilterOptionsBottomSheet
            when (which) {
                0 -> {
                    filterType = null
                    filterAdapter.updateItem(position, "Type", false)
                }
                1 -> {
                    filterType = "in"
                    filterAdapter.updateItem(position, "Cash In", true)
                }
                2 -> {
                    filterType = "out"
                    filterAdapter.updateItem(position, "Cash Out", true)
                }
            }
            applyFilters()
        }
    }

    private fun showEntryByFilterDialog(position: Int) {
        val users = userMap.values.toTypedArray()
        val userIds = userMap.keys.toTypedArray()

        if (users.isEmpty()) {
            showToast("No members found")
            return
        }

        val displayOptions = arrayOf("All Members") + users
        val selectedIndex = if (filterEntryBy == null) 0 else (userIds.indexOf(filterEntryBy) + 1).coerceAtLeast(0)

        showFilterOptionsBottomSheet("Filter by Entry By", displayOptions, selectedIndex) { which ->
            if (_binding == null) return@showFilterOptionsBottomSheet
            if (which == 0) {
                filterEntryBy = null
                filterAdapter.updateItem(position, "Entry By", false)
            } else {
                filterEntryBy = userIds[which - 1]
                filterAdapter.updateItem(position, users[which - 1], true)
            }
            applyFilters()
        }
    }

    private fun showFilterOptionsBottomSheet(
        title: String,
        options: Array<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = com.cashbk.app.databinding.BottomSheetTransactionFilterBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // Ensure transparent window background so rounded bg_bottom_sheet_glass renders cleanly
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        sheetBinding.tvFilterTitle.text = title
        sheetBinding.btnClose.setOnClickListener { dialog.dismiss() }

        options.forEachIndexed { index, label ->
            val optionBinding = com.cashbk.app.databinding.ItemFilterOptionBinding.inflate(
                layoutInflater,
                sheetBinding.layoutOptionsContainer,
                false
            )
            val isSelected = index == selectedIndex
            optionBinding.tvOptionTitle.text = label
            optionBinding.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (isSelected) {
                optionBinding.cardOption.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.bottom_nav_active_bg)
                )
                optionBinding.cardOption.strokeColor =
                    ContextCompat.getColor(requireContext(), R.color.primary)
                optionBinding.tvOptionTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            } else {
                optionBinding.cardOption.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.stitch_surface_container_low)
                )
                optionBinding.cardOption.strokeColor =
                    ContextCompat.getColor(requireContext(), R.color.glass_border)
                optionBinding.tvOptionTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary)
                )
            }

            optionBinding.cardOption.setOnClickListener {
                dialog.dismiss()
                onSelect(index)
            }
            sheetBinding.layoutOptionsContainer.addView(optionBinding.root)
        }

        dialog.show()
    }

    private fun showAddTransactionDialog(type: String) {
        if (currentUserRole == "reader") {
            showToast("You have view-only access")
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

    private fun setupFabMenu() {
        isFabMenuOpen = false
        // Smooth entrance animation for the round + floating button
        binding.fabAddTransaction.apply {
            scaleX = 0.6f
            scaleY = 0.6f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(if (currentUserRole == "reader") 0.5f else 1f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }

        binding.fabAddTransaction.setOnClickListener {
            if (currentUserRole == "reader") {
                showToast("You have view-only access")
                return@setOnClickListener
            }
            toggleFabMenu()
        }

        binding.fabScrim.setOnClickListener { closeFabMenu() }

        binding.fabCashInRow.setOnClickListener {
            closeFabMenu()
            showAddTransactionDialog("in")
        }

        binding.fabCashOutRow.setOnClickListener {
            closeFabMenu()
            showAddTransactionDialog("out")
        }
    }

    private fun toggleFabMenu() {
        if (isFabMenuOpen) closeFabMenu() else openFabMenu()
    }

    private fun openFabMenu() {
        if (_binding == null || isFabMenuOpen) return
        isFabMenuOpen = true

        binding.fabScrim.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220).start()
        }

        binding.ivFabIcon.animate()
            .rotation(45f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        binding.fabAddTransaction.animate()
            .scaleX(0.92f).scaleY(0.92f).setDuration(100)
            .withEndAction {
                if (_binding != null) {
                    binding.fabAddTransaction.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                }
            }.start()

        animateFabRowIn(binding.fabCashInRow, 0L)
        animateFabRowIn(binding.fabCashOutRow, 50L)
    }

    private fun animateFabRowIn(row: View, delay: Long) {
        row.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 48f
            scaleX = 0.85f
            scaleY = 0.85f
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun closeFabMenu() {
        if (_binding == null || !isFabMenuOpen) return
        isFabMenuOpen = false

        binding.fabScrim.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                if (_binding != null && !isFabMenuOpen) {
                    binding.fabScrim.visibility = View.GONE
                }
            }.start()

        binding.ivFabIcon.animate()
            .rotation(0f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        animateFabRowOut(binding.fabCashOutRow)
        animateFabRowOut(binding.fabCashInRow)
    }

    private fun animateFabRowOut(row: View) {
        row.animate()
            .alpha(0f)
            .translationY(36f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setStartDelay(0L)
            .setDuration(180)
            .withEndAction {
                if (_binding != null && !isFabMenuOpen) {
                    row.visibility = View.GONE
                }
            }.start()
    }

    private fun updateUIForRole() {
        if (_binding == null) return
        if (currentUserRole == "reader") {
            binding.fabAddTransaction.alpha = 0.5f
        } else {
            binding.fabAddTransaction.alpha = 1.0f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFabMenuOpen = false
        _binding = null
    }
}
