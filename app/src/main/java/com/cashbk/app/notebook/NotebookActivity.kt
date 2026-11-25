package com.cashbk.app.notebook

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.ActivityNotebookBinding
import com.cashbk.app.transaction._bean.Transaction
import com.cashbk.app.transaction.AddTransactionFragment
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
import com.cashbk.app.category.ManageCategoriesFragment
import com.cashbk.app.party.ManagePartiesFragment
import com.cashbk.app.utils.CustomOptionsMenu
import com.cashbk.app.utils.setupStatusAndNavBar

import com.cashbk.app.utils.showToast

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
            setupStatusAndNavBar()

            notebookId = intent.getStringExtra("notebookId")
            if (notebookId.isNullOrEmpty()) {
                showToast("Notebook ID is missing.", Toast.LENGTH_LONG)
                finish()
                return
            }

            database = FirebaseDatabase.getInstance().reference.child("transactions").child(notebookId!!)

            // Setup Navigation
            setupNavigation()
            setupKeyboardVisibilityListener()

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
            showToast("Error: ${e.message}", Toast.LENGTH_LONG)
        }
    }

    private var isBottomNavAllowed = true

    fun setBottomNavVisible(visible: Boolean) {
        isBottomNavAllowed = visible
        binding.layoutBottomNavContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setupKeyboardVisibilityListener() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            binding.layoutBottomNavContainer.visibility =
                if (imeVisible || !isBottomNavAllowed) View.GONE else View.VISIBLE
            androidx.core.view.ViewCompat.onApplyWindowInsets(view, insets)
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
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetBinding = com.cashbk.app.databinding.BottomSheetTransactionActionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        val titleText = when {
            transaction.partyName.isNotEmpty() -> transaction.partyName
            transaction.remark.isNotEmpty() && transaction.remark != "-" -> transaction.remark
            transaction.categoryName.isNotEmpty() -> transaction.categoryName
            else -> if (transaction.type == "in") "Cash In" else "Cash Out"
        }
        sheetBinding.tvTransactionTitle.text = titleText
        sheetBinding.tvTransactionSubtitle.text = "${transaction.date} • ${transaction.time}"

        val formattedAmount = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).format(transaction.amount)
        if (transaction.type == "in") {
            sheetBinding.tvTransactionAmount.text = "+ ₹$formattedAmount"
            sheetBinding.tvTransactionAmount.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.success))
        } else {
            sheetBinding.tvTransactionAmount.text = "- ₹$formattedAmount"
            sheetBinding.tvTransactionAmount.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.danger))
        }

        sheetBinding.btnClose.setOnClickListener { dialog.dismiss() }
        sheetBinding.cardEdit.setOnClickListener {
            dialog.dismiss()
            showEditTransactionDialog(transaction)
        }
        sheetBinding.cardDelete.setOnClickListener {
            dialog.dismiss()
            deleteTransaction(transaction)
        }

        dialog.show()
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
            .addOnSuccessListener { showToast("Deleted from ledger") }
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
        args.putString("receiptUrl", transaction.receiptUrl)
        args.putString("receiptName", transaction.receiptName)
        
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.notebook_fragment_container, fragment, EDIT_DIALOG_TAG)
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        setupStatusAndNavBar()
    }
}