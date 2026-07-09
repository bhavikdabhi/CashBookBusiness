package com.cashbk.app.notebook.adapter

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.databinding.ItemNotebookBinding
import com.cashbk.app.notebook._bean.Notebook
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.Calendar
import java.util.Locale

class NotebookAdapter(
    private val notebooks: List<Notebook>,
    private val onClick: (Notebook) -> Unit,
    private val onMenuClick: (Notebook, View) -> Unit
) : RecyclerView.Adapter<NotebookAdapter.NotebookViewHolder>() {

    inner class NotebookViewHolder(val binding: ItemNotebookBinding) :
        RecyclerView.ViewHolder(binding.root) {
            fun bind(notebook: Notebook, position: Int) {
                binding.notebookName.text = notebook.name

                val calendar = Calendar.getInstance(Locale.getDefault())
                calendar.timeInMillis = notebook.createdAt
                val dateStr = DateFormat.format("MMM dd yyyy", calendar).toString()

                // Fetch Member Count
                FirebaseDatabase.getInstance().reference.child("members").child(notebook.id)
                    .addListenerForSingleValueEvent(object: ValueEventListener {
                         override fun onDataChange(snapshot: DataSnapshot) {
                             val count = snapshot.childrenCount
                             binding.notebookDetails.text = "$count Members . Updated on $dateStr"
                         }
                         override fun onCancelled(error: DatabaseError) {
                             binding.notebookDetails.text = "- Members . Updated on $dateStr"
                         }
                    })

                // Determine dynamic theme colors and icon based on name keywords or fallback position
                val nameLower = notebook.name.lowercase(Locale.getDefault())
                val themeIndex = when {
                    nameLower.contains("family") || nameLower.contains("ledger") || nameLower.contains("sales") -> 0
                    nameLower.contains("office") || nameLower.contains("expense") -> 1
                    nameLower.contains("grocery") || nameLower.contains("house") || nameLower.contains("food") -> 2
                    nameLower.contains("travel") || nameLower.contains("bud") || nameLower.contains("trip") || nameLower.contains("flight") -> 3
                    nameLower.contains("special") || nameLower.contains("occasion") || nameLower.contains("gift") -> 4
                    else -> position % 5
                }

                val (iconRes, bgRes, tintRes) = when (themeIndex) {
                    0 -> Triple(R.drawable.ic_action_group, R.color.icon_bg_blue, R.color.icon_tint_blue)
                    1 -> Triple(R.drawable.ic_work, R.color.icon_bg_green, R.color.icon_tint_green)
                    2 -> Triple(R.drawable.ic_cat_shop, R.color.icon_bg_purple, R.color.icon_tint_purple)
                    3 -> Triple(R.drawable.ic_cat_travel, R.color.icon_bg_yellow, R.color.icon_tint_yellow)
                    else -> Triple(R.drawable.ic_gift, R.color.icon_bg_pink, R.color.icon_tint_pink)
                }

                binding.iconRingContainer.backgroundTintList = ContextCompat.getColorStateList(itemView.context, bgRes)
                binding.iconCard.setCardBackgroundColor(ContextCompat.getColorStateList(itemView.context, bgRes))
                binding.iconImage.setImageResource(iconRes)
                binding.iconImage.imageTintList = ContextCompat.getColorStateList(itemView.context, tintRes)

                // Fetch Balance
                FirebaseDatabase.getInstance().reference.child("transactions").child(notebook.id)
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            var totalBalance = 0.0
                            for (child in snapshot.children) {
                                val type = child.child("type").value as? String ?: ""
                                val amountObj = child.child("amount").value
                                val amount = when (amountObj) {
                                    is Long -> amountObj.toDouble()
                                    is Double -> amountObj
                                    is String -> amountObj.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }

                                if (type == "in") {
                                    totalBalance += amount
                                } else if (type == "out") {
                                    totalBalance -= amount
                                }
                            }

                            val formattedBalance = formatBalanceCompact(totalBalance, showPositiveSign = true)
                            binding.tvBalance.text = formattedBalance

                            val balanceColor = if (totalBalance >= 0) R.color.success else R.color.danger
                            val chipBg = if (totalBalance >= 0) R.color.green_bg else R.color.red_bg
                            val chipText = if (totalBalance >= 0) "Credit" else "Debit"
                            val chipIcon = if (totalBalance >= 0) R.drawable.ic_trans_plus else R.drawable.ic_trans_minus

                            binding.tvBalance.setTextColor(ContextCompat.getColor(itemView.context, balanceColor))
                            binding.layoutBalanceChip.backgroundTintList = ContextCompat.getColorStateList(itemView.context, chipBg)
                            binding.tvBalanceChipText.text = chipText
                            binding.tvBalanceChipText.setTextColor(ContextCompat.getColor(itemView.context, balanceColor))
                            binding.ivBalanceChipIcon.setImageResource(chipIcon)
                            binding.ivBalanceChipIcon.imageTintList = ContextCompat.getColorStateList(itemView.context, balanceColor)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            binding.tvBalance.text = "Error"
                        }
                    })
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotebookViewHolder {
        val itemBinding = ItemNotebookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotebookViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
        val notebook = notebooks[position]
        holder.bind(notebook, position)
        holder.itemView.setOnClickListener { onClick(notebook) }
        holder.binding.btnNotebookOptions.setOnClickListener { onMenuClick(notebook, it) }
    }

    override fun getItemCount() = notebooks.size
}