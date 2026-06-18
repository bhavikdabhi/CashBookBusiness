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
            fun bind(notebook: Notebook) {
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

                            binding.tvBalance.text = "₹ $totalBalance"
                            val color = if (totalBalance >= 0) R.color.success else R.color.danger
                            binding.tvBalance.setTextColor(ContextCompat.getColor(itemView.context, color))
                        }

                        override fun onCancelled(error: DatabaseError) {
                            binding.tvBalance.text = "Error"
                        }
                    })
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotebookViewHolder {
        val itemBinding = ItemNotebookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotebookViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
        val notebook = notebooks[position]
        holder.bind(notebook)
        holder.itemView.setOnClickListener { onClick(notebook) }
        holder.binding.btnNotebookOptions.setOnClickListener { onMenuClick(notebook, it) }
    }

    override fun getItemCount() = notebooks.size
}