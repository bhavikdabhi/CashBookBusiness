package com.cashbk.app.ui.transaction

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.dataclass.Category
import com.cashbk.app.dataclass.Party
import com.cashbk.app.dataclass.Transaction
import com.cashbk.app.databinding.FragmentAddTransactionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cashbk.app.utils.GoogleDriveManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionFragment : Fragment() {

    private var _binding: FragmentAddTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth

    private var notebookId: String? = null
    private var transactionId: String? = null
    private var transactionType = "in"

    private val categories = mutableListOf<Category>()
    private val parties = mutableListOf<Party>()

    private var receiptUri: android.net.Uri? = null
    private var capturedReceiptName: String = ""

    private val pickReceiptLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            receiptUri = it
            capturedReceiptName = getFileNameFromUri(it) ?: "Receipt_${System.currentTimeMillis()}.jpg"
            binding.tvAttachLabel.text = capturedReceiptName
            binding.tvAttachLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.success))
            binding.ivAttachIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            binding.ivAttachIcon.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.success))
        }
    }

    private var pendingCloseAfterSave = false
    private val driveSignInLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                saveGoogleDriveEmail(account.email)
                uploadToGoogleDrive(account)
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Drive Authorization Failed", Toast.LENGTH_SHORT).show()
                binding.saveButton.isEnabled = true
                binding.saveButton.text = "Confirm Transaction"
            }
        } else {
            binding.saveButton.isEnabled = true
            binding.saveButton.text = "Confirm Transaction"
        }
    }

    private val calendar = Calendar.getInstance()



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        notebookId = arguments?.getString("notebookId")
        transactionId = arguments?.getString("transactionId")

        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            requireActivity().supportFragmentManager.popBackStack()
            return
        }

        setupUI()
        
        // Populate fields if in edit mode
        if (transactionId != null) {
            val amount = arguments?.getDouble("amount", 0.0) ?: 0.0
            val remark = arguments?.getString("remark") ?: ""
            val date = arguments?.getString("date") ?: ""
            val time = arguments?.getString("time") ?: ""
            transactionType = arguments?.getString("type") ?: "in"

            binding.etAmount.setText(if (amount > 0) amount.toString() else "")
            binding.etRemark.setText(remark)
            binding.etDate.setText(date)
            binding.etTime.setText(time)
            
            // Set type
            if (transactionType == "in") {
                updateToggleButtonUI(true)
            } else {
                updateToggleButtonUI(false)
            }
            
            // Parse date/time for calendar
            try {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                if (date.isNotEmpty()) calendar.time = sdfDate.parse(date)!!
                // Note: syncing time perfectly might be tricky with separate fields, 
                // but this sets the calendar date correctly at least.
            } catch (e: Exception) { e.printStackTrace() }
            
            binding.saveAddMoreButton.visibility = View.GONE 
            binding.saveButton.text = "Update"
        }

        fetchCategories()
        fetchParties()
    }

    /* ---------------- UI ---------------- */

    private fun setupUI() {

        binding.cashInButton.setOnClickListener {
            updateToggleButtonUI(true)
        }
        
        binding.cashOutButton.setOnClickListener {
            updateToggleButtonUI(false)
        }
        
        updateToggleButtonUI(true)

        updateDate()
        updateTime()

        binding.etDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    calendar.set(y, m, d)
                    updateDate()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, h, min ->
                    calendar.set(Calendar.HOUR_OF_DAY, h)
                    calendar.set(Calendar.MINUTE, min)
                    updateTime()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        }

        binding.saveButton.setOnClickListener { saveTransaction(true) }
        binding.cancelButton.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }
        binding.saveAddMoreButton.setOnClickListener { saveTransaction(false) }

        binding.btnAttachBill.setOnClickListener {
            pickReceiptLauncher.launch("image/*")
        }

        binding.addPartyButton.setOnClickListener {
            val bottomSheet = com.cashbk.app.fragment.AddPartyBottomSheet()
            val args = Bundle()
            args.putString("notebookId", notebookId)
            bottomSheet.arguments = args
            bottomSheet.show(childFragmentManager, "AddPartyBottomSheet")
        }

    }

    private fun updateDate() {
        binding.etDate.setText(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(calendar.time)
        )
    }

    private fun updateTime() {
        binding.etTime.setText(
            SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(calendar.time)
        )
    }

    /* ---------------- DATA ---------------- */

    private fun fetchCategories() {
        database.reference
            .child("categories")
            .child(notebookId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    categories.clear()
                    
                    // Add General category by default
                    val generalCat = Category(id = "general", name = "General")
                    categories.add(generalCat)
                    
                    snapshot.children.forEach {
                        val cat = it.getValue(Category::class.java)
                        cat?.id = it.key ?: ""
                        cat?.let { categories.add(it) }
                    }

                    binding.categoryChipGroup.removeAllViews()
                    for (cat in categories) {
                        val chip = com.google.android.material.chip.Chip(requireContext())
                        chip.text = cat.name
                        chip.isCheckable = true
                        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.auth_surface_high)
                        )
                        chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.white))
                        chip.checkedIconTint = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.white)
                        )
                        // Show border only when selected, transparent when unselected
                        val primaryColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.stitch_primary)
                        chip.chipStrokeColor = android.content.res.ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf()
                            ),
                            intArrayOf(primaryColor, android.graphics.Color.TRANSPARENT)
                        )
                        chip.chipStrokeWidth = 2f * resources.displayMetrics.density // 2dp stroke
                        chip.tag = cat.id
                        binding.categoryChipGroup.addView(chip)
                    }


                    // Add a mock + chip
                   /* val addChip = com.google.android.material.chip.Chip(requireContext())
                    addChip.text = "+"
                    addChip.isCheckable = false
                    addChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.auth_surface_high))
                    addChip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.white))
                    addChip.setOnClickListener {
                        Toast.makeText(requireContext(), "Add Category not implemented", Toast.LENGTH_SHORT).show()
                    }
                    binding.categoryChipGroup.addView(addChip)
                    */
                    
                    // Set selection if editing
                    val targetId = arguments?.getString("categoryId")
                    if (targetId != null) {
                        for (i in 0 until binding.categoryChipGroup.childCount) {
                            val chip = binding.categoryChipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
                            if (chip?.tag == targetId) {
                                binding.categoryChipGroup.check(chip.id)
                                break
                            }
                        }
                    } else if (binding.categoryChipGroup.childCount > 1) {
                         // Check the first true category chip by default if there is one
                         val firstChip = binding.categoryChipGroup.getChildAt(0) as? com.google.android.material.chip.Chip
                         binding.categoryChipGroup.check(firstChip?.id ?: View.NO_ID)
                    }
                }


                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchParties() {
        database.reference
            .child("parties")
            .child(notebookId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    parties.clear()
                    snapshot.children.forEach {
                        val party = it.getValue(Party::class.java)
                        party?.id = it.key ?: ""
                        party?.let { parties.add(it) }
                    }

                    // Prepend a placeholder "--Select--" entry
                    val spinnerItems = mutableListOf("-- Select Party --") + parties.map { it.name }
                    val partyAdapter = object : ArrayAdapter<String>(requireContext(), com.cashbk.app.R.layout.item_dropdown_text, spinnerItems) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getView(position, convertView, parent) as android.widget.TextView
                            v.setTextColor(android.graphics.Color.WHITE)
                            return v
                        }
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getDropDownView(position, convertView, parent) as android.widget.TextView
                            v.setTextColor(android.graphics.Color.WHITE)
                            v.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.cashbk.app.R.color.card_bg))
                            return v
                        }
                    }
                    binding.partySpinner.adapter = partyAdapter
                    // Default to the placeholder (index 0)
                    binding.partySpinner.setSelection(0)
                    
                    // Set selection if editing (offset by 1 because of placeholder)
                    val targetId = arguments?.getString("partyId")
                    if (targetId != null) {
                        val index = parties.indexOfFirst { it.id == targetId }
                        if (index >= 0) {
                            binding.partySpinner.setSelection(index + 1)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load parties", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /* ---------------- SAVE ---------------- */

    private fun saveTransaction(closeAfterSave: Boolean) {

        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()
        if (amount == null) {
            binding.tilAmount.error = "Enter valid amount"
            return
        } else if (amount <= 0.0) {
            binding.tilAmount.error = "Amount must be greater than zero"
            return
        } else {
            binding.tilAmount.error = null
        }
        
        val remarkText = binding.etRemark.text.toString().trim()
        if (remarkText.length > 500) {
            binding.tilRemark.error = "Remark cannot exceed 500 characters"
            return
        } else {
            binding.tilRemark.error = null
        }
        
        // Party is optional — skip validation if placeholder is selected
        val partyNameStr = binding.partySpinner.selectedItem?.toString()?.trim() ?: ""
        val isNoPartySelected = partyNameStr.isEmpty() || partyNameStr == "-- Select Party --"

        if (receiptUri != null) {
            // Trigger Google Drive Upload Flow
            pendingCloseAfterSave = closeAfterSave
            binding.saveButton.isEnabled = false
            binding.saveButton.text = "Connecting to Drive..."
            
            val uid = auth.currentUser?.uid ?: return
            FirebaseDatabase.getInstance().reference.child("users").child(uid).child("googleDriveEmail").get()
                .addOnSuccessListener { snapshot ->
                    val driveEmail = snapshot.getValue(String::class.java)
                    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                    if (!driveEmail.isNullOrEmpty()) {
                        builder.setAccountName(driveEmail)
                    }
                    val signInOptions = builder.build()
                    val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
                    driveSignInLauncher.launch(client.signInIntent)
                }
                .addOnFailureListener {
                    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                        .build()
                    val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
                    driveSignInLauncher.launch(client.signInIntent)
                }
        } else {
            // No receipt, save directly to Firebase
            executeFirebaseSave(closeAfterSave, "")
        }
    }

    private fun saveGoogleDriveEmail(email: String?) {
        val uid = auth.currentUser?.uid ?: return
        if (email != null) {
            FirebaseDatabase.getInstance().reference.child("users").child(uid).child("googleDriveEmail").setValue(email)
        }
    }
    private fun uploadToGoogleDrive(googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        binding.saveButton.text = "Uploading Receipt..."
        
        val driveManager = GoogleDriveManager(requireContext(), googleAccount)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = capturedReceiptName.ifEmpty { "Receipt_${System.currentTimeMillis()}.jpg" }

                val webViewLink = driveManager.uploadFile(
                    fileUri = receiptUri!!,
                    fileName = fileName,
                    folderName = "Receipt"
                )

                if (webViewLink != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Uploaded to Google Drive!", Toast.LENGTH_SHORT).show()
                        binding.saveButton.isEnabled = true
                        binding.saveButton.text = "Confirm Transaction"
                        executeFirebaseSave(pendingCloseAfterSave, webViewLink)
                    }
                } else {
                    throw Exception("Failed to get upload link")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Drive Upload Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.saveButton.text = "Confirm Transaction"
                    binding.saveButton.isEnabled = true
                }
            }
        }
    }

    private fun executeFirebaseSave(closeAfterSave: Boolean, finalReceiptUrl: String) {
        val amount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val selectedChipId = binding.categoryChipGroup.checkedChipId
        val selectedChip = binding.categoryChipGroup.findViewById<com.google.android.material.chip.Chip>(selectedChipId)
        val selectedCategoryId = selectedChip?.tag as? String ?: "-"
        // Resolve the category name from our loaded list so it's stored in the DB
        val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: ""

        val partyNameStr = binding.partySpinner.selectedItem?.toString()?.trim() ?: ""
        val isPlaceholder = partyNameStr.isEmpty() || partyNameStr == "-- Select Party --"
        val selectedParty = if (isPlaceholder) null else parties.find { it.name.equals(partyNameStr, ignoreCase = true) }
        val partyId = selectedParty?.id ?: ""
        val selectedPartyName = selectedParty?.name ?: ""
        
        val remarkText = binding.etRemark.text.toString().takeIf { it.isNotBlank() } ?: "-"

        val transactionRef = if (transactionId != null) {
             database.reference
            .child("transactions")
            .child(notebookId!!)
            .child(transactionId!!)
        } else {
             database.reference
            .child("transactions")
            .child(notebookId!!)
            .push()
        }

        val transaction = Transaction(
            id = transactionRef.key ?: "",
            type = transactionType,
            amount = amount,
            remark = remarkText,
            date = binding.etDate.text.toString(),
            time = binding.etTime.text.toString(),
            createdBy = auth.currentUser?.uid.orEmpty(),
            categoryId = selectedCategoryId,
            categoryName = selectedCategoryName,
            partyId = partyId,
            partyName = selectedPartyName,
            receiptUrl = finalReceiptUrl,
            receiptName = capturedReceiptName
        )

        transactionRef.setValue(transaction)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), if (closeAfterSave) "Transaction saved" else "Saved. Add next entry", Toast.LENGTH_SHORT).show()
                if (closeAfterSave) {
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    resetFormForNextEntry()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun resetFormForNextEntry() {

        binding.etAmount.text?.clear()
        binding.etRemark.text?.clear()

        // Reset inputs
        if (binding.categoryChipGroup.childCount > 1) {
             val firstChip = binding.categoryChipGroup.getChildAt(0) as? com.google.android.material.chip.Chip
             binding.categoryChipGroup.check(firstChip?.id ?: View.NO_ID)
        }
        if (parties.isNotEmpty()) {
            binding.partySpinner.setSelection(0)
        }
        
        // Reset Receipt
        receiptUri = null
        capturedReceiptName = ""
        binding.tvAttachLabel.text = "ATTACH BILL / RECEIPT"
        binding.tvAttachLabel.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.gray))
        binding.ivAttachIcon.setImageResource(android.R.drawable.ic_menu_camera)
        binding.ivAttachIcon.imageTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.gray))

        // Reset to Cash In
        updateToggleButtonUI(true)

        // Reset date & time to current
        calendar.timeInMillis = System.currentTimeMillis()
        updateTime()
        updateDate()

        // Focus amount for fast typing
        binding.etAmount.requestFocus()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun updateToggleButtonUI(isCashIn: Boolean) {

        if (isCashIn) {
            transactionType = "in"
            binding.cashInButton.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.success))
            binding.cashInButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.white))
            
            binding.cashOutButton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            binding.cashOutButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.gray))
        } else {
            transactionType = "out"
            binding.cashOutButton.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.danger))
            binding.cashOutButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.white))
            
            binding.cashInButton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            binding.cashInButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.cashbk.app.R.color.gray))
        }
    }

    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }
}
