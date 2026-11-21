package com.cashbk.app.notebook.filter

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cashbk.app.R
import com.cashbk.app.databinding.BottomSheetNotebookFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NotebookFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNotebookFilterBinding? = null
    private val binding get() = _binding!!

    private var initialCriteria: NotebookFilterCriteria = NotebookFilterCriteria()
    private var onFilterApplied: ((NotebookFilterCriteria) -> Unit)? = null

    companion object {
        fun newInstance(
            currentCriteria: NotebookFilterCriteria,
            onFilterApplied: (NotebookFilterCriteria) -> Unit
        ): NotebookFilterBottomSheet {
            val sheet = NotebookFilterBottomSheet()
            sheet.initialCriteria = currentCriteria.copy()
            sheet.onFilterApplied = onFilterApplied
            return sheet
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNotebookFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindInitialCriteria(initialCriteria)
        setupClickListeners()
    }

    private fun bindInitialCriteria(criteria: NotebookFilterCriteria) {
        // Sort by
        when (criteria.sortBy) {
            NotebookFilterCriteria.SortBy.DATE_NEWEST -> binding.chipSortNewest.isChecked = true
            NotebookFilterCriteria.SortBy.DATE_OLDEST -> binding.chipSortOldest.isChecked = true
            NotebookFilterCriteria.SortBy.NAME_ASC -> binding.chipSortNameAz.isChecked = true
            NotebookFilterCriteria.SortBy.NAME_DESC -> binding.chipSortNameZa.isChecked = true
            NotebookFilterCriteria.SortBy.BALANCE_HIGH -> binding.chipSortBalanceHigh.isChecked = true
            NotebookFilterCriteria.SortBy.BALANCE_LOW -> binding.chipSortBalanceLow.isChecked = true
        }

        // Balance status
        when (criteria.balanceStatus) {
            NotebookFilterCriteria.BalanceStatus.ALL -> binding.chipBalanceAll.isChecked = true
            NotebookFilterCriteria.BalanceStatus.CREDIT -> binding.chipBalanceCredit.isChecked = true
            NotebookFilterCriteria.BalanceStatus.DEBIT -> binding.chipBalanceDebit.isChecked = true
            NotebookFilterCriteria.BalanceStatus.SETTLED -> binding.chipBalanceSettled.isChecked = true
        }

        // Time period
        when (criteria.timePeriod) {
            NotebookFilterCriteria.TimePeriod.ALL_TIME -> binding.chipTimeAll.isChecked = true
            NotebookFilterCriteria.TimePeriod.THIS_MONTH -> binding.chipTimeMonth.isChecked = true
            NotebookFilterCriteria.TimePeriod.THIS_YEAR -> binding.chipTimeYear.isChecked = true
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            bindInitialCriteria(NotebookFilterCriteria())
        }

        binding.btnApplyFilter.setOnClickListener {
            val selectedSort = when (binding.chipGroupSort.checkedChipId) {
                R.id.chip_sort_oldest -> NotebookFilterCriteria.SortBy.DATE_OLDEST
                R.id.chip_sort_name_az -> NotebookFilterCriteria.SortBy.NAME_ASC
                R.id.chip_sort_name_za -> NotebookFilterCriteria.SortBy.NAME_DESC
                R.id.chip_sort_balance_high -> NotebookFilterCriteria.SortBy.BALANCE_HIGH
                R.id.chip_sort_balance_low -> NotebookFilterCriteria.SortBy.BALANCE_LOW
                else -> NotebookFilterCriteria.SortBy.DATE_NEWEST
            }

            val selectedBalance = when (binding.chipGroupBalance.checkedChipId) {
                R.id.chip_balance_credit -> NotebookFilterCriteria.BalanceStatus.CREDIT
                R.id.chip_balance_debit -> NotebookFilterCriteria.BalanceStatus.DEBIT
                R.id.chip_balance_settled -> NotebookFilterCriteria.BalanceStatus.SETTLED
                else -> NotebookFilterCriteria.BalanceStatus.ALL
            }

            val selectedTime = when (binding.chipGroupTime.checkedChipId) {
                R.id.chip_time_month -> NotebookFilterCriteria.TimePeriod.THIS_MONTH
                R.id.chip_time_year -> NotebookFilterCriteria.TimePeriod.THIS_YEAR
                else -> NotebookFilterCriteria.TimePeriod.ALL_TIME
            }

            val newCriteria = NotebookFilterCriteria(
                sortBy = selectedSort,
                balanceStatus = selectedBalance,
                timePeriod = selectedTime
            )

            onFilterApplied?.invoke(newCriteria)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

