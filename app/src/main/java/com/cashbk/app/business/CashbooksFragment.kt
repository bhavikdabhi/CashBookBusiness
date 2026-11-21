package com.cashbk.app.business

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.notebook.adapter.NotebookAdapter
import com.cashbk.app.databinding.FragmentCashbooksBinding
import com.cashbk.app.databinding.PopupDeleteNotebookBinding
import com.cashbk.app.notebook._bean.Notebook
import com.cashbk.app.notebook.filter.NotebookFilterBottomSheet
import com.cashbk.app.notebook.filter.NotebookFilterCriteria
import com.cashbk.app.notebook.AddNotebookDialogFragment
import com.cashbk.app.notebook.NotebookActivity
import com.cashbk.app.utils.CustomOptionsMenu
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

import com.cashbk.app.utils.showToast

class CashbooksFragment : Fragment() {

    private var _binding: FragmentCashbooksBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val allNotebookList = mutableListOf<Notebook>()
    private val notebookList = mutableListOf<Notebook>()
    private var currentBusinessId: String? = null
    private var notebooksQuery: Query? = null
    private var notebooksListener: ValueEventListener? = null
    private var notebookJob: Job? = null

    // Filter & Search State
    private var activeFilter = NotebookFilterCriteria()
    private var currentSearchQuery = ""

    // V2 Tracker Maps for Dynamic Statistics
    private val notebookBalances = mutableMapOf<String, Double>()
    private val notebookMonthlyBalances = mutableMapOf<String, Double>()
    private val transactionListeners = mutableMapOf<String, ValueEventListener>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCashbooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance().reference.child("notebooks")
        setupRecyclerView()

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchNotebooks()
        }
    }

    fun updateBusinessId(businessId: String?) {
        if (businessId == currentBusinessId) return
        currentBusinessId = businessId

        // If fragment view is not created or fragment is not attached yet,
        // fetchNotebooks() will be called automatically in onViewCreated().
        if (!isAdded || _binding == null) return

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchNotebooks()
        } else {
            allNotebookList.clear()
            notebookList.clear()
            activeFilter = NotebookFilterCriteria()
            currentSearchQuery = ""
            notebookAdapter.notifyDataSetChanged()
            
            // Clear stats and listeners
            for ((notebookId, listener) in transactionListeners) {
                FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId)
                    .removeEventListener(listener)
            }
            transactionListeners.clear()
            notebookBalances.clear()
            notebookMonthlyBalances.clear()
            updateSummaryStats()

            binding.layoutShimmerNotebooks.stopPulseAnimation()
            binding.layoutShimmerNotebooks.visibility = View.GONE
        }
    }

    private fun fetchNotebooks() {
        val id = currentBusinessId ?: return
        val currentContext = context ?: return
        notebookJob?.cancel()

        notebookBalances.clear()
        notebookMonthlyBalances.clear()

        if (_binding != null) {
            binding.layoutShimmerNotebooks.visibility = View.VISIBLE
            binding.layoutShimmerNotebooks.startPulseAnimation()
            binding.notebooksRecyclerView.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
        }

        val repository = com.cashbk.app.data.di.AppContainer.getRepository(currentContext)
        notebookJob = lifecycleScope.launch {
            repository.getNotebooksFlow(id).collectLatest { notebooks ->
                allNotebookList.clear()
                allNotebookList.addAll(notebooks)

                // Collect transactions for each notebook to compute balances dynamically
                notebooks.forEach { notebook ->
                    launch {
                        repository.getTransactionsFlow(notebook.id).collectLatest { transactions ->
                            var total = 0.0
                            var monthly = 0.0
                            val calendar = Calendar.getInstance()
                            val curMonth = calendar.get(Calendar.MONTH)
                            val curYear = calendar.get(Calendar.YEAR)

                            transactions.forEach { trans ->
                                val amount = trans.amount
                                val type = trans.type
                                val createdAt = trans.createdAt

                                val netAmount = if (type == "in") amount else if (type == "out") -amount else 0.0
                                total += netAmount

                                val transCal = Calendar.getInstance()
                                transCal.timeInMillis = createdAt
                                if (transCal.get(Calendar.MONTH) == curMonth && transCal.get(Calendar.YEAR) == curYear) {
                                    monthly += netAmount
                                }
                            }

                            notebookBalances[notebook.id] = total
                            notebookMonthlyBalances[notebook.id] = monthly
                            updateSummaryStats()
                            applyFiltersAndSort()
                        }
                    }
                }

                if (notebooks.isEmpty()) {
                    updateSummaryStats()
                }

                // Hide shimmer loading UI
                if (_binding != null) {
                    binding.layoutShimmerNotebooks.stopPulseAnimation()
                    binding.layoutShimmerNotebooks.visibility = View.GONE
                }
                applyFiltersAndSort()
            }
        }
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(
            notebookList,
            onClick = { notebook ->
                val intent = Intent(context, NotebookActivity::class.java)
                intent.putExtra("notebookId", notebook.id)
                startActivity(intent)
            },
            onMenuClick = { notebook, view ->
                showNotebookMenu(notebook, view)
            }
        )

        binding.notebooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notebookAdapter
        }

        binding.addNotebookFab.setOnClickListener {
            openAddNotebookDialog()
        }

        binding.btnCreateFirstBook.setOnClickListener {
            openAddNotebookDialog()
        }

        // Notebook Filter Button Click
        binding.btnFilter.setOnClickListener {
            val filterSheet = NotebookFilterBottomSheet.newInstance(activeFilter) { newCriteria ->
                activeFilter = newCriteria
                applyFiltersAndSort()
            }
            filterSheet.show(parentFragmentManager, "NotebookFilterBottomSheet")
        }

        // Toggleable Search Box
        binding.btnSearch.setOnClickListener {
            val isVisible = binding.layoutSearchContainer.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutSearchContainer.visibility = View.GONE
                binding.etSearchNotebooks.setText("")
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etSearchNotebooks.windowToken, 0)
            } else {
                binding.layoutSearchContainer.visibility = View.VISIBLE
                binding.etSearchNotebooks.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etSearchNotebooks, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.etSearchNotebooks.doAfterTextChanged { editable ->
            currentSearchQuery = editable?.toString()?.trim().orEmpty()
            binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            applyFiltersAndSort()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchNotebooks.setText("")
        }
    }

    private fun openAddNotebookDialog() {
        val sheet = AddNotebookDialogFragment()
        sheet.arguments = Bundle().apply {
            val businessId = currentBusinessId
            putString("businessId", businessId)
        }
        sheet.show(parentFragmentManager, "AddNotebook")
    }

    private fun applyFiltersAndSort() {
        if (_binding == null) return

        var filtered = allNotebookList.toList()

        // 1. Text Search Filter
        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // 2. Net Balance Filter
        when (activeFilter.balanceStatus) {
            NotebookFilterCriteria.BalanceStatus.CREDIT -> {
                filtered = filtered.filter { (notebookBalances[it.id] ?: 0.0) > 0.0 }
            }
            NotebookFilterCriteria.BalanceStatus.DEBIT -> {
                filtered = filtered.filter { (notebookBalances[it.id] ?: 0.0) < 0.0 }
            }
            NotebookFilterCriteria.BalanceStatus.SETTLED -> {
                filtered = filtered.filter { (notebookBalances[it.id] ?: 0.0) == 0.0 }
            }
            NotebookFilterCriteria.BalanceStatus.ALL -> { /* All */ }
        }

        // 3. Time Period Filter
        val now = Calendar.getInstance()
        when (activeFilter.timePeriod) {
            NotebookFilterCriteria.TimePeriod.THIS_MONTH -> {
                val curMonth = now.get(Calendar.MONTH)
                val curYear = now.get(Calendar.YEAR)
                filtered = filtered.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                    cal.get(Calendar.MONTH) == curMonth && cal.get(Calendar.YEAR) == curYear
                }
            }
            NotebookFilterCriteria.TimePeriod.THIS_YEAR -> {
                val curYear = now.get(Calendar.YEAR)
                filtered = filtered.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.createdAt }
                    cal.get(Calendar.YEAR) == curYear
                }
            }
            NotebookFilterCriteria.TimePeriod.ALL_TIME -> { /* All */ }
        }

        // 4. Sort
        filtered = when (activeFilter.sortBy) {
            NotebookFilterCriteria.SortBy.DATE_NEWEST -> filtered.sortedByDescending { it.createdAt }
            NotebookFilterCriteria.SortBy.DATE_OLDEST -> filtered.sortedBy { it.createdAt }
            NotebookFilterCriteria.SortBy.NAME_ASC -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            NotebookFilterCriteria.SortBy.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            NotebookFilterCriteria.SortBy.BALANCE_HIGH -> filtered.sortedByDescending { notebookBalances[it.id] ?: 0.0 }
            NotebookFilterCriteria.SortBy.BALANCE_LOW -> filtered.sortedBy { notebookBalances[it.id] ?: 0.0 }
        }

        notebookList.clear()
        notebookList.addAll(filtered)
        notebookAdapter.notifyDataSetChanged()

        // Update Active Filter UI Indicator Dot & Icon Tint
        val isFiltered = !activeFilter.isDefault()
        binding.filterActiveIndicator.visibility = if (isFiltered) View.VISIBLE else View.GONE
        binding.ivFilterIcon.imageTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (isFiltered) R.color.primary else R.color.text_secondary
        )

        if (isFiltered || currentSearchQuery.isNotEmpty()) {
            binding.tvActiveBooksCount.text = "${notebookList.size} of ${allNotebookList.size} notebooks"
        } else {
            binding.tvActiveBooksCount.text = "${allNotebookList.size} active notebooks"
        }
        binding.tvStatTotalBooks.text = allNotebookList.size.toString()

        // Empty State Logic
        if (notebookList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.notebooksRecyclerView.visibility = View.GONE
            if (allNotebookList.isNotEmpty()) {
                binding.tvEmptyTitle.text = "No matching notebooks"
                binding.tvEmptySubtitle.text = "Try adjusting or clearing your filters to see results."
                binding.btnCreateFirstBook.text = "Clear Filters"
                binding.btnCreateFirstBook.setOnClickListener {
                    activeFilter = NotebookFilterCriteria()
                    currentSearchQuery = ""
                    binding.etSearchNotebooks.setText("")
                    binding.layoutSearchContainer.visibility = View.GONE
                    applyFiltersAndSort()
                }
            } else {
                binding.tvEmptyTitle.text = "No books yet!"
                binding.tvEmptySubtitle.text = "Create your first notebook to start\ntracking income and expenses."
                binding.btnCreateFirstBook.text = "+ Create Your First Book"
                binding.btnCreateFirstBook.setOnClickListener {
                    openAddNotebookDialog()
                }
            }
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.notebooksRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showNotebookMenu(notebook: Notebook, view: View) {
        val popup = CustomOptionsMenu(requireContext(), view)

        popup.setOnRenameClickListener {
            showRenameNotebookDialog(notebook)
        }
        popup.setOnShareClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Join my notebook \"${notebook.name}\" on CashBook: https://cashbk.app/join/${notebook.id}")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share Notebook via"))
        }
        popup.setOnDeleteClickListener {
            showDeletePopup(view, notebook)
        }

        popup.show()
    }

    override fun onDestroyView() {
        notebookJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun showDeleteNotebookDialog(notebook: Notebook) {
        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CashbkAlertDialog
        )
            .setTitle("Delete Notebook")
            .setMessage(
                "Are you sure you want to delete \"${notebook.name}\"?\nThis action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteNotebook(notebook)
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Red delete button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
    }

    private fun deleteNotebook(notebook: Notebook) {
        lifecycleScope.launch {
            try {
                com.cashbk.app.data.di.AppContainer.getRepository(requireContext()).deleteNotebook(notebook)
                showToast("Notebook deleted")
            } catch (e: Exception) {
                showToast(e.message)
            }
        }
    }

    private fun showDeletePopup(anchor: View, notebook: Notebook) {

        val popupBinding = PopupDeleteNotebookBinding.inflate(
            LayoutInflater.from(requireContext())
        )

        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Set data
        popupBinding.tvNotebookName.text = notebook.name

        // Close button (top-right ❌)
        popupBinding.btnClose.setOnClickListener {
            popupWindow.dismiss()
        }

        // Delete action
        popupBinding.btnDelete.setOnClickListener {
            popupWindow.dismiss()
            deleteNotebook(notebook)
        }

        popupWindow.apply {
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
        }

        // Show near menu button
        popupWindow.showAsDropDown(anchor, -200, 0)
    }

    private fun showRenameNotebookDialog(notebook: Notebook) {

        val sheet = AddNotebookDialogFragment().apply {
            arguments = Bundle().apply {
                putBoolean("isRename", true)
                putString("notebookId", notebook.id)
                putString("notebookName", notebook.name)
                putString("businessId", currentBusinessId) // optional but safe
            }
        }

        sheet.show(parentFragmentManager, "RenameNotebook")
    }




    private fun updateSummaryStats() {
        if (_binding == null) return

        val netTotal = notebookBalances.values.sum()
        val monthlyTotal = notebookMonthlyBalances.values.sum()

        binding.tvStatNetBalance.text = formatBalanceCompact(netTotal, showPositiveSign = false)
        binding.tvStatNetBalance.setTextColor(
            ContextCompat.getColor(requireContext(), if (netTotal >= 0) R.color.success else R.color.danger)
        )

        binding.tvStatThisMonth.text = formatBalanceCompact(monthlyTotal, showPositiveSign = true)
        binding.tvStatThisMonth.setTextColor(
            ContextCompat.getColor(requireContext(), if (monthlyTotal >= 0) R.color.success else R.color.danger)
        )
    }

    private fun formatBalanceCompact(value: Double, showPositiveSign: Boolean): String {
        val absValue = Math.abs(value)
        val prefix = if (value >= 0) {
            if (showPositiveSign) "+" else ""
        } else {
            "-"
        }
        val symbol = "₹"
        val formattedValue = when {
            absValue >= 100000.0 -> {
                val d = absValue / 100000.0
                if (d == d.toInt().toDouble()) String.format(Locale.getDefault(), "%dL", d.toInt())
                else String.format(Locale.getDefault(), "%.2fL", d)
            }
            absValue >= 1000.0 -> {
                val d = absValue / 1000.0
                if (d == d.toInt().toDouble()) String.format(Locale.getDefault(), "%dK", d.toInt())
                else String.format(Locale.getDefault(), "%.1fK", d)
            }
            else -> String.format(Locale.getDefault(), "%.2f", absValue)
        }
        val cleanVal = formattedValue.replace(".00", "")
        return "$prefix$symbol$cleanVal"
    }
}