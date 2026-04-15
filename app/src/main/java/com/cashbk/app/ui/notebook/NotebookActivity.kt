package com.cashbk.app.ui.notebook

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.ActivityNotebookBinding
import com.cashbk.app.dataclass.Notebook
import com.cashbk.app.dataclass.Transaction
import com.cashbk.app.ui.notebook.SettingsFragment
import com.cashbk.app.ui.transaction.AddTransactionFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class NotebookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotebookBinding
    private lateinit var database: DatabaseReference
    
    private var notebookId: String? = null
    private var currentUserRole: String = ""
    private val EDIT_DIALOG_TAG = "EditTransactionDialog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityNotebookBinding.inflate(layoutInflater)
            setContentView(binding.root)

            notebookId = intent.getStringExtra("notebookId")
            if (notebookId.isNullOrEmpty()) {
                Toast.makeText(this, "Notebook ID is missing.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            database = FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId!!)

            // Setup Navigation
            setupNavigation()

            // Fetch Details & Role (Async)
            checkUserRole()
            // Listeners
            // Moved to fragments

            // Load Default Fragment once on start
            if (savedInstanceState == null) {
                replaceFragment(NotebookHomeFragment(), "Transactions")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_transactions -> {
                    replaceFragment(NotebookHomeFragment(), "Transactions")
                    true
                }
                R.id.nav_parties -> {
                    replaceFragment(ManagePartiesFragment(), "Parties")
                    true
                }
                R.id.nav_categories -> {
                    replaceFragment(ManageCategoriesFragment(), "Categories")
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment(), "Settings")
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("currentUserRole", currentUserRole)
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("notebooks").child(notebookId!!).get()
            .addOnSuccessListener { notebookSnapshot ->
                val businessId = notebookSnapshot.child("businessId").value as? String ?: return@addOnSuccessListener
                
                FirebaseDatabase.getInstance().reference.child("businesses").child(businessId).get()
                    .addOnSuccessListener { businessSnapshot ->
                        val ownerId = businessSnapshot.child("ownerId").value as? String
                        if (ownerId == currentUserId) {
                            currentUserRole = "owner"
                        } else {
                            currentUserRole = "partner" // Defaulting to partner if not owner for now
                        }
                    }
            }
    }

    fun getUserRole(): String = currentUserRole

    fun showTransactionPopupMenu(anchor: View, transaction: Transaction) {
        val popup = com.cashbk.app.utils.CustomOptionsMenu(this, anchor)
        popup.setOnRenameClickListener { showEditTransactionDialog(transaction) }
        popup.setOnDeleteClickListener { deleteTransaction(transaction) }
        popup.show()
    }

    private fun deleteTransaction(transaction: Transaction) {
        database.child(transaction.id).removeValue()
            .addOnSuccessListener { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
    }

    private fun showEditTransactionDialog(transaction: Transaction) {
        val fragment = AddTransactionFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("transactionId", transaction.id)
        args.putDouble("amount", transaction.amount)
        args.putString("remark", transaction.remark)
        args.putString("date", transaction.date)
        args.putString("time", transaction.time)
        args.putString("type", transaction.type)
        args.putString("categoryId", transaction.categoryId)
        args.putString("partyId", transaction.partyId)
        
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, EDIT_DIALOG_TAG)
            .addToBackStack(null)
            .commit()
    }
}