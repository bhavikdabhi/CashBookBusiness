package com.cashbk.app.party

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cashbk.app.utils.CustomAlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.party.adapter.PartyAdapter
import com.cashbk.app.databinding.FragmentManagePartiesBinding
import com.cashbk.app.party._bean.Party
import com.google.firebase.database.*
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation

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

        binding.ivSearchIconToolbar.setOnClickListener {
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
        
        binding.btnAddPlaceholder.setOnClickListener { navigateToAddParty() }
        binding.fabAdd.setOnClickListener { navigateToAddParty() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterParties(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun navigateToAddParty() {
        val fragment = AddPartyFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        fragment.arguments = args

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.notebook_fragment_container, fragment)
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
        if (_binding != null) {
            binding.layoutShimmerParties.visibility = View.VISIBLE
            binding.layoutShimmerParties.startPulseAnimation()
            binding.rvParties.visibility = View.GONE
        }

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

                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerParties.stopPulseAnimation()
                    binding.layoutShimmerParties.visibility = View.GONE
                    binding.rvParties.visibility = View.VISIBLE
                    partyAdapter.updateData(partiesList)
                }, 2000)
            }

            override fun onCancelled(error: DatabaseError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerParties.stopPulseAnimation()
                    binding.layoutShimmerParties.visibility = View.GONE
                    binding.rvParties.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }, 2000)
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
        CustomAlertDialog(requireContext())
            .setTitle("Delete Party")
            .setMessage("Are you sure you want to delete ${party.name}?")
            .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(requireContext(), R.color.danger))
            .setPositiveButton("Delete") {
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
