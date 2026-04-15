package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
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

    private val originalPartiesList = mutableListOf<Party>()

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

        binding.btnAddToolbar.setOnClickListener { navigateToAddParty() }
        binding.btnAddPlaceholder.setOnClickListener { navigateToAddParty() }
        binding.fabAdd.setOnClickListener { navigateToAddParty() }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterParties(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun navigateToAddParty() {
        val fragment = AddPartyFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        fragment.arguments = args

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
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
                originalPartiesList.clear()
                for (child in snapshot.children) {
                    val party = child.getValue(Party::class.java)
                    party?.id = child.key ?: ""
                    party?.let { 
                        partiesList.add(it)
                        originalPartiesList.add(it)
                    }
                }

                partyAdapter.updateData(partiesList)
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun filterParties(query: String) {
        val filtered = if (query.isEmpty()) {
            originalPartiesList
        } else {
            originalPartiesList.filter { 
                it.name.contains(query, ignoreCase = true) || it.role.contains(query, ignoreCase = true)
            }
        }
        partiesList.clear()
        partiesList.addAll(filtered)
        partyAdapter.updateData(partiesList)
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
