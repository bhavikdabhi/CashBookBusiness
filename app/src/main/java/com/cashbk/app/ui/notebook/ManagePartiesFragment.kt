package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.adapter.PartyAdapter
import com.cashbk.app.databinding.DialogAddEntityBinding
import com.cashbk.app.databinding.FragmentManagePartiesBinding
import com.cashbk.app.dataclass.Party
import com.google.firebase.database.*

class ManagePartiesFragment : Fragment() {

    private var _binding: FragmentManagePartiesBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: DatabaseReference
    private lateinit var partyAdapter: PartyAdapter
    private val partiesList = mutableListOf<Party>()
    
    private var notebookId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagePartiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("parties").child(notebookId!!)

        setupRecyclerView()
        fetchParties()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabAdd.setOnClickListener {
            showAddPartyDialog()
        }
    }

    private fun setupRecyclerView() {
        partyAdapter = PartyAdapter(partiesList) { party ->
            showDeleteConfirmation(party)
        }
        binding.rvParties.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = partyAdapter
        }
    }

    private fun fetchParties() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                partiesList.clear()
                for (child in snapshot.children) {
                    val party = child.getValue(Party::class.java)
                    party?.id = child.key ?: ""
                    party?.let { partiesList.add(it) }
                }

                if (partiesList.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvParties.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvParties.visibility = View.VISIBLE
                }
                partyAdapter.updateData(partiesList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddPartyDialog() {
        val dialogBinding = DialogAddEntityBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvTitle.text = "Add New Party"
        
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etName.text.toString().trim()
            if (name.isNotEmpty()) {
                val partyId = database.push().key ?: ""
                val party = Party(id = partyId, name = name)
                database.child(partyId).setValue(party)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Party Added", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
            } else {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmation(party: Party) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Party")
            .setMessage("Are you sure you want to delete ${party.name}?")
            .setPositiveButton("Delete") { _, _ ->
                database.child(party.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
