package com.cashbk.app.ui.transaction

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.cashbk.app.data.model.Category
import com.cashbk.app.data.model.Party
import com.cashbk.app.data.model.Transaction
import com.cashbk.app.databinding.ActivityAddTransactionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var notebookId: String? = null
    private var transactionType = "in" // Default to Cash In

    private val categories = mutableListOf<Category>()
    private val parties = mutableListOf<Party>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        notebookId = intent.getStringExtra("notebookId")

        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(this, "Notebook ID is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        fetchCategories()
        fetchParties()
    }

    private fun setupUI() {
        // Set default selection
        binding.toggleButtonGroup.check(binding.cashInButton.id)

        binding.toggleButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                transactionType = if (checkedId == binding.cashInButton.id) "in" else "out"
            }
        }

        // Set current date and time
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.dateEditText.setText(sdfDate.format(Date()))
        binding.timeEditText.setText(sdfTime.format(Date()))

        binding.saveButton.setOnClickListener { saveTransaction() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun fetchCategories() {
        val categoryRef = database.reference.child("categories").child(notebookId!!)
        categoryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categories.clear()
                snapshot.children.forEach { catSnapshot ->
                    val category = catSnapshot.getValue(Category::class.java)
                    category?.id = catSnapshot.key.orEmpty()
                    category?.let { categories.add(it) }
                }
                val adapter = ArrayAdapter(this@AddTransactionActivity, android.R.layout.simple_spinner_item, categories.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.categorySpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AddTransactionActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchParties() {
        val partyRef = database.reference.child("parties").child(notebookId!!)
        partyRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                parties.clear()
                snapshot.children.forEach { partySnapshot ->
                    val party = partySnapshot.getValue(Party::class.java)
                    party?.id = partySnapshot.key.orEmpty()
                    party?.let { parties.add(it) }
                }
                val adapter = ArrayAdapter(this@AddTransactionActivity, android.R.layout.simple_spinner_item, parties.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.partySpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AddTransactionActivity, "Failed to load parties", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveTransaction() {

        val amount = binding.amountEditText.text.toString().toDoubleOrNull()
        if (amount == null) {
            Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
            return
        }

        if (categories.isEmpty() || parties.isEmpty()) {
            Toast.makeText(this, "Category or Party not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }


        val selectedCategory = categories[binding.categorySpinner.selectedItemPosition]
        val selectedParty = parties[binding.partySpinner.selectedItemPosition]

        val newTransaction = Transaction(
            type = transactionType,
            amount = amount,
            remark = binding.remarkEditText.text.toString(),
            date = binding.dateEditText.text.toString(),
            time = binding.timeEditText.text.toString(),
            createdBy = auth.currentUser?.uid.orEmpty(),

            // ✅ STORE ONLY IDS
            categoryId = selectedCategory.id,
            partyId = selectedParty.id
        )


        val transactionRef =
            database.reference.child("transactions").child(notebookId!!).push()

        newTransaction.id = transactionRef.key ?: ""

        transactionRef.setValue(newTransaction)
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

}