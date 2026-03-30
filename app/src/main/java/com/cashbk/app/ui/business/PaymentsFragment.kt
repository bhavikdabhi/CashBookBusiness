package com.cashbk.app.ui.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.databinding.FragmentPaymentsBinding
import com.google.firebase.database.*

data class PaymentItem(var id: String = "", val title: String = "", val amount: Double = 0.0)

class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!
    private var currentBusinessId: String? = null
    
    // We would use a real adapter here, but maintaining an empty state is priority.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.fabAddPayment.setOnClickListener {
            Toast.makeText(requireContext(), "Payment creation dialog pending...", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
        fetchPayments()
    }

    private fun fetchPayments() {
        if (currentBusinessId.isNullOrEmpty()) return
        
        FirebaseDatabase.getInstance().reference.child("payments").child(currentBusinessId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) return
                    
                    if (snapshot.childrenCount == 0L) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.rvPayments.visibility = View.GONE
                    } else {
                        binding.layoutEmpty.visibility = View.GONE
                        binding.rvPayments.visibility = View.VISIBLE
                        // Load into adapter here
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
