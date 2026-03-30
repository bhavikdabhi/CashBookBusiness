package com.cashbk.app.ui.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.databinding.FragmentEventsBinding
import com.google.firebase.database.*

data class EventItem(var id: String = "", val title: String = "", val description: String = "", val date: Long = 0L)

class EventsFragment : Fragment() {

    private var _binding: FragmentEventsBinding? = null
    private val binding get() = _binding!!
    private var currentBusinessId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.fabAddEvent.setOnClickListener {
            Toast.makeText(requireContext(), "Event creation dialog pending...", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
        fetchEvents()
    }

    private fun fetchEvents() {
        if (currentBusinessId.isNullOrEmpty()) return
        
        FirebaseDatabase.getInstance().reference.child("events").child(currentBusinessId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) return
                    
                    if (snapshot.childrenCount == 0L) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvEvents.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvEvents.visibility = View.VISIBLE
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
