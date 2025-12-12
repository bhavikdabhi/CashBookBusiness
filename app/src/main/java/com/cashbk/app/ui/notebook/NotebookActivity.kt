package com.cashbk.app.ui.notebook

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.data.model.Transaction
import com.cashbk.app.databinding.ActivityNotebookBinding
import com.cashbk.app.databinding.ItemTransactionBinding
import com.cashbk.app.ui.transaction.AddTransactionDialogFragment // Import the DialogFragment
import com.google.firebase.database.*
import java.util.Locale

class NotebookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotebookBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()
    private var notebookId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotebookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notebookId = intent.getStringExtra("notebookId")
        Log.d("NotebookActivity", "Received notebookId: $notebookId")

        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(this, "Notebook ID is missing. Cannot load transactions.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId!!)

        setupRecyclerView()
        fetchTransactions()

        binding.addTransactionFab.setOnClickListener {
            // Show the AddTransactionDialogFragment as a pop-up
            val addTransactionDialog = AddTransactionDialogFragment()
            val args = Bundle()
            args.putString("notebookId", notebookId)
            addTransactionDialog.arguments = args
            addTransactionDialog.show(supportFragmentManager, "AddTransactionDialog")
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactionList) { transaction ->
            Toast.makeText(
                this@NotebookActivity,
                "Clicked: ${transaction.remark} - ${transaction.getAmountAsDouble()}",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotebookActivity)
            adapter = transactionAdapter
        }
    }

    private fun fetchTransactions() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                transactionList.clear()
                if (snapshot.exists()) {
                    for (transactionSnapshot in snapshot.children) {
                        try {
                            val transaction = transactionSnapshot.getValue(Transaction::class.java)
                            transaction?.id = transactionSnapshot.key.orEmpty()
                            transaction?.let { transactionList.add(it) }
                        } catch (e: DatabaseException) {
                            Log.e("NotebookActivity", "Error converting transaction: ${transactionSnapshot.key}", e)
                            Toast.makeText(this@NotebookActivity, "Error reading transaction data for key: ${transactionSnapshot.key}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this@NotebookActivity, "No transactions found.", Toast.LENGTH_SHORT).show()
                }
                transactionAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@NotebookActivity, "Failed to load transactions: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    inner class TransactionAdapter(private val transactions: List<Transaction>, private val onClick: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

        inner class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return TransactionViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
            val transaction = transactions[position]
            holder.binding.tvRemark.text = transaction.remark
            holder.binding.tvAmount.text = transaction.getAmountAsDouble().toString()
            holder.binding.tvDate.text = transaction.date
            holder.binding.tvTime.text = transaction.time
            holder.binding.tvType.text = "Type: ${transaction.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}"
           holder.binding.tvCreatedBy.text = "By: ${transaction.createdBy}"

            val colorRes = if (transaction.type == "in") R.color.success else R.color.danger
            holder.binding.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))

            holder.itemView.setOnClickListener { onClick(transaction) }
        }

        override fun getItemCount() = transactions.size
    }
}