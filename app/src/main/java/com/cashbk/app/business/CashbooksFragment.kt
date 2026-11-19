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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.notebook.adapter.NotebookAdapter
import com.cashbk.app.databinding.FragmentCashbooksBinding
import com.cashbk.app.databinding.PopupDeleteNotebookBinding
import com.cashbk.app.notebook._bean.Notebook
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
    private val notebookList = mutableListOf<Notebook>()
    private var currentBusinessId: String? = null
    private var notebooksQuery: Query? = null
    private var notebooksListener: ValueEventListener? = null
    private var notebookJob: Job? = null

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
            notebookList.clear()
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
                notebookList.clear()
                notebookList.addAll(notebooks)

                if (_binding != null) {
                    binding.tvActiveBooksCount.text = "${notebookList.size} active notebooks"
                    binding.tvStatTotalBooks.text = notebookList.size.toString()
                }

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
                            notebookAdapter.notifyDataSetChanged()
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

                    if (notebookList.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.notebooksRecyclerView.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.notebooksRecyclerView.visibility = View.VISIBLE
                    }
                }
                notebookAdapter.notifyDataSetChanged()
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
            val sheet = AddNotebookDialogFragment()
            sheet.arguments = Bundle().apply {
                val businessId = currentBusinessId
                putString("businessId", businessId)
            }
            sheet.show(parentFragmentManager, "AddNotebook")
        }

        binding.btnCreateFirstBook.setOnClickListener {
            val sheet = AddNotebookDialogFragment()
            sheet.arguments = Bundle().apply {
                val businessId = currentBusinessId
                putString("businessId", businessId)
            }
            sheet.show(parentFragmentManager, "AddNotebook")
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