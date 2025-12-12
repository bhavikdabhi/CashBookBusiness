package com.cashbk.app.fragment

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
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

class AddTransactionDialogFragment : DialogFragment() {

    private var _binding: ActivityAddTransactionBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var notebookId: String? = null
    private var transactionType = "in" // Default to Cash In

    private val categories = mutableListOf<Category>()
    private val parties = mutableListOf<Party>()

    private val calendar = Calendar.getInstance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // You can customize dialog properties here if needed
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ActivityAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        notebookId = arguments?.getString("notebookId")

        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(context, "Notebook ID is missing.", Toast.LENGTH_SHORT).show()
            dismiss()
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
        updateDateInView()
        updateTimeInView()

        // Date Picker
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, monthOfYear)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateInView()
        }

        binding.dateEditText.setOnClickListener {
            DatePickerDialog(requireContext(),
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time Picker
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            updateTimeInView()
        }

        binding.timeEditText.setOnClickListener {
            TimePickerDialog(requireContext(),
                timeSetListener,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false // set to true for 24-hour time
            ).show()
        }

        binding.saveButton.setOnClickListener { saveTransaction() }
        binding.cancelButton.setOnClickListener { dismiss() }
    }

    private fun updateDateInView() {
        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        binding.dateEditText.setText(sdfDate.format(calendar.time))
    }

    private fun updateTimeInView() {
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.timeEditText.setText(sdfTime.format(calendar.time))
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
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.categorySpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load categories", Toast.LENGTH_SHORT).show()
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
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, parties.map { it.name })
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.partySpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load parties", Toast.LENGTH_SHORT).show()
            }

        })

    }

    private fun saveTransaction() {
        val amount = binding.amountEditText.text.toString().toDoubleOrNull()
        if (amount == null) {
            Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
            return
        }

        val newTransaction = Transaction(
            type = transactionType,
            amount = amount,
            remark = binding.remarkEditText.text.toString(),
            date = binding.dateEditText.text.toString(),
            time = binding.timeEditText.text.toString(),
            createdBy = auth.currentUser?.uid.orEmpty(),
            createdAt = System.currentTimeMillis()
        )

        val transactionRef = database.reference.child("transactions").child(notebookId!!).push()
        transactionRef.setValue(newTransaction).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Transaction saved.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Failed to save transaction: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}