package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.dataclass.Transaction
import com.cashbk.app.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.util.Locale

class TransactionAdapter(
    private val transactions: List<Transaction>,
    private val onClick: (Transaction) -> Unit,
    private val onLongClick: (View, Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    private fun formatCurrency(amount: Double): String {
        return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount)
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

        // PARTY NAME logic
        if (transaction.partyName.isNotEmpty()) {
            holder.binding.tvParty.text = transaction.partyName
            holder.binding.tvParty.visibility = View.VISIBLE
        } else {
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
            onLongClick(it, transaction)
            true 
        }
    }

    override fun getItemCount() = transactions.size
}
