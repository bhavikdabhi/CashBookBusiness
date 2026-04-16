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
import com.cashbk.app.ui.transaction.AddTransactionFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.net.Uri

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

    fun navigateToTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val args = Bundle()
        args.putString("notebookId", notebookId)
        args.putString("currentUserRole", currentUserRole)
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.notebook_fragment_container, fragment, tag)
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
        val url = transaction.receiptUrl
        if (url.isNotEmpty() && url.contains("drive.google.com")) {
            val fileId = extractDriveFileId(url)
            if (fileId != null) {
                deleteFromDrive(fileId)
            }
        }

        database.child(transaction.id).removeValue()
            .addOnSuccessListener { Toast.makeText(this, "Deleted from ledger", Toast.LENGTH_SHORT).show() }
    }

    private fun deleteFromDrive(fileId: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Log.w("NotebookActivity", "Cannot delete from Drive: No account signed in.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@NotebookActivity,
                    listOf(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = account.account

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("CashBookBusiness").build()

                driveService.files().delete(fileId).execute()
                
                withContext(Dispatchers.Main) {
                    Log.d("NotebookActivity", "Successfully deleted receipt from Drive.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Log.e("NotebookActivity", "Failed to delete Drive file: ${e.message}")
                }
            }
        }
    }

    private fun extractDriveFileId(url: String): String? {
        return try {
            if (url.contains("/d/")) {
                url.substringAfter("/d/").substringBefore("/")
            } else {
                Uri.parse(url).getQueryParameter("id")
            }
        } catch (e: Exception) {
            null
        }
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
            .replace(R.id.notebook_fragment_container, fragment, EDIT_DIALOG_TAG)
            .addToBackStack(null)
            .commit()
    }
}