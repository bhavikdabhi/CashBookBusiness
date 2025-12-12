package com.cashbk.app.ui.notebook

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.data.model.Transaction
import com.cashbk.app.databinding.ActivityNotebookBinding
import com.cashbk.app.databinding.ItemTransactionBinding
import com.cashbk.app.ui.transaction.AddTransactionDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale
import android.content.Intent

class NotebookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotebookBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()
    private var notebookId: String? = null
    private var currentUserRole: String = "" // "admin", "partner", "writer", "reader"

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
        checkUserRole()

        binding.addTransactionFab.setOnClickListener {
            val addTransactionDialog = AddTransactionDialogFragment()
            val args = Bundle()
            args.putString("notebookId", notebookId)
            addTransactionDialog.arguments = args
            addTransactionDialog.show(supportFragmentManager, "AddTransactionDialog")
        }

        binding.btnMenu.setOnClickListener {
            showMenu(it)
        }
    }

    private fun showMenu(view: android.view.View) {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_notebook_options, popup.menu)

        // Role check
        if (currentUserRole != "admin" && currentUserRole != "partner") {
             // Hide all or show limited? User said "when user clikc not three dots that time open a menu that menu data not visible"
             // Assuming we just want to fix visibility. Logic remains: only owner/partner can act.
             // If neither, maybe we shouldn't even show the button (already handled in adapter).
             // But if we are here, we show options.
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    Toast.makeText(this, "Rename clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_delete -> {
                    Toast.makeText(this, "Delete clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_share -> {
                    val intent = Intent(this, com.cashbk.app.ui.members.MembersActivity::class.java)
                    intent.putExtra("entityId", notebookId)
                    intent.putExtra("entityType", "notebook")
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get().addOnSuccessListener { notebookSnapshot ->
            val businessId = notebookSnapshot.child("businessId").value as? String ?: return@addOnSuccessListener
            
            // 2. Check if Owner (Admin)
            FirebaseDatabase.getInstance().reference.child("businesses").child(businessId).get().addOnSuccessListener { businessSnapshot ->
                val ownerId = businessSnapshot.child("ownerId").value as? String
                
                if (ownerId == currentUserId) {
                    currentUserRole = "admin"
                    updateUI()
                } else {
                    // 3. Check if Partner in business_members
                    FirebaseDatabase.getInstance().reference.child("business_members").child(businessId).child(currentUserId).get().addOnSuccessListener { partnerSnapshot ->
                        if (partnerSnapshot.exists() && partnerSnapshot.child("role").value == "partner") {
                            currentUserRole = "partner"
                            updateUI()
                        } else {
                            // 4. Check if Notebook Member in members/{notebookId}/{uid}
                            checkNotebookMemberRole(currentUserId)
                        }
                    }
                }
            }
        }
    }

    private fun checkNotebookMemberRole(uid: String) {
        FirebaseDatabase.getInstance().reference.child("members").child(notebookId!!).child(uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val role = snapshot.child("role").value as? String
                if (role != null) {
                    currentUserRole = role
                    updateUI()
                }
            }
        }
    }

    private fun updateUI() {
        Log.d("NotebookActivity", "User Role: $currentUserRole")
        when (currentUserRole) {
            "admin", "partner" -> {
                binding.addTransactionFab.visibility = android.view.View.VISIBLE
                binding.btnMenu.visibility = android.view.View.VISIBLE
            }
            "writer" -> {
                binding.addTransactionFab.visibility = android.view.View.VISIBLE
                binding.btnMenu.visibility = android.view.View.GONE // Can't manage notebook
            }
            "reader" -> {
                binding.addTransactionFab.visibility = android.view.View.GONE
                binding.btnMenu.visibility = android.view.View.GONE // Can't manage notebook
            }
        }
        transactionAdapter.notifyDataSetChanged()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactionList) { transaction ->
            if (currentUserRole == "reader") {
                Toast.makeText(this, "View Only: ${transaction.remark}", Toast.LENGTH_SHORT).show()
            } else {
                showTransactionOptions(transaction)
            }
        }
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotebookActivity)
            adapter = transactionAdapter
        }
    }

    private fun showTransactionOptions(transaction: Transaction) {
        val options = arrayOf("Delete Transaction")
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                if (which == 0) {
                    deleteTransaction(transaction)
                }
            }
            .show()
    }

    private fun deleteTransaction(transaction: Transaction) {
        database.child(transaction.id).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
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