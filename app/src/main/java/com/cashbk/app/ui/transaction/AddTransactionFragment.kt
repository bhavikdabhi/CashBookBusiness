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
import com.cashbk.app.data.model.Category
import com.cashbk.app.data.model.Party
import com.cashbk.app.data.model.Transaction
import com.cashbk.app.databinding.FragmentAddTransactionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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

            binding.amountEditText.setText(if (amount > 0) amount.toString() else "")
            binding.remarkEditText.setText(remark)
            binding.dateEditText.setText(date)
            binding.timeEditText.setText(time)
            
            // Set type
            if (transactionType == "in") {
                binding.toggleButtonGroup.check(binding.cashInButton.id)
            } else {
                binding.toggleButtonGroup.check(binding.cashOutButton.id)
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

        binding.toggleButtonGroup.check(binding.cashInButton.id)

        binding.toggleButtonGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                transactionType =
                    if (checkedId == binding.cashInButton.id) "in" else "out"
            }
        }

        updateDate()
        updateTime()

        binding.dateEditText.setOnClickListener {
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

        binding.timeEditText.setOnClickListener {
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

        binding.addPartyButton.setOnClickListener {
            val bottomSheet = com.cashbk.app.fragment.AddPartyBottomSheet()
            val args = Bundle()
            args.putString("notebookId", notebookId)
            bottomSheet.arguments = args
            bottomSheet.show(childFragmentManager, "AddPartyBottomSheet")
        }

    }

    private fun updateDate() {
        binding.dateEditText.setText(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(calendar.time)
        )
    }

    private fun updateTime() {
        binding.timeEditText.setText(
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
                    snapshot.children.forEach {
                        val cat = it.getValue(Category::class.java)
                        cat?.id = it.key ?: ""
                        cat?.let { categories.add(it) }
                    }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        categories.map { it.name }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.categorySpinner.adapter = adapter
                    
                    // Set selection if editing
                    val targetId = arguments?.getString("categoryId")
                    if (targetId != null) {
                        val index = categories.indexOfFirst { it.id == targetId }
                        if (index != -1) binding.categorySpinner.setSelection(index)
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
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    parties.clear()
                    snapshot.children.forEach {
                        val party = it.getValue(Party::class.java)
                        party?.id = it.key ?: ""
                        party?.let { parties.add(it) }
                    }

                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        parties.map { it.name }
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.partySpinner.adapter = adapter
                    
                    // Set selection if editing
                    val targetId = arguments?.getString("partyId")
                    if (targetId != null) {
                        val index = parties.indexOfFirst { it.id == targetId }
                        if (index != -1) binding.partySpinner.setSelection(index)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load parties", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /* ---------------- SAVE ---------------- */

    private fun saveTransaction(closeAfterSave: Boolean) {

        val amount = binding.amountEditText.text.toString().toDoubleOrNull()
        if (amount == null) {
            Toast.makeText(requireContext(), "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (categories.isEmpty() || parties.isEmpty()) {
            Toast.makeText(requireContext(), "Category or Party not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategory = categories[binding.categorySpinner.selectedItemPosition]
        val selectedParty = parties[binding.partySpinner.selectedItemPosition]

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
            remark = binding.remarkEditText.text.toString(),
            date = binding.dateEditText.text.toString(),
            time = binding.timeEditText.text.toString(),
            createdBy = auth.currentUser?.uid.orEmpty(),
            categoryId = selectedCategory.id,
            partyId = selectedParty.id
        )

        transactionRef.setValue(transaction)
            .addOnSuccessListener {

                Toast.makeText(
                    requireContext(),
                    if (closeAfterSave) "Transaction saved" else "Saved. Add next entry",
                    Toast.LENGTH_SHORT
                ).show()

                if (closeAfterSave) {
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    resetFormForNextEntry()
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Failed: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    private fun resetFormForNextEntry() {

        binding.amountEditText.text?.clear()
        binding.remarkEditText.text?.clear()

        // Reset spinners
        binding.categorySpinner.setSelection(0)
        binding.partySpinner.setSelection(0)

        // Reset to Cash In
        binding.toggleButtonGroup.check(binding.cashInButton.id)
        transactionType = "in"

        // Reset date & time to current
        calendar.timeInMillis = System.currentTimeMillis()
        updateTime()
        updateDate()

        // Focus amount for fast typing
        binding.amountEditText.requestFocus()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
