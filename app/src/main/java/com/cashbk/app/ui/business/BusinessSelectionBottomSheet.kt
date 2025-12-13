package com.cashbk.app.ui.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.data.model.Business
import com.cashbk.app.databinding.DialogSelectBusinessBinding
import com.cashbk.app.databinding.ItemBusinessSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BusinessSelectionBottomSheet(
    private val currentBusinessId: String?,
    private val onBusinessSelected: (Business) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: DialogSelectBusinessBinding
    private val businessList = mutableListOf<Business>()
    private lateinit var adapter: BusinessSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectBusinessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchBusinesses()

        binding.btnAddNewBusiness.setOnClickListener {
            Toast.makeText(requireContext(), "Add Business Clicked", Toast.LENGTH_SHORT).show()
            dismiss()
            // Logic to open Add Business Dialog can be added here or via interface
        }
    }

    private fun setupRecyclerView() {
        adapter = BusinessSelectionAdapter(businessList, currentBusinessId) { business ->
            onBusinessSelected(business)
            dismiss()
        }
        binding.recyclerBusinessList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBusinessList.adapter = adapter
    }

    private fun fetchBusinesses() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference

        // Fetch owned businesses
        db.child("businesses").orderByChild("ownerId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    businessList.clear()
                    for (child in snapshot.children) {
                        val bus = child.getValue(Business::class.java)
                        bus?.id = child.key.orEmpty()
                        bus?.let { businessList.add(it) }
                    }
                    // Fetch shared businesses checking logic omitted for brevity, can be added
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    inner class BusinessSelectionAdapter(
        private val list: List<Business>,
        private val selectedId: String?,
        private val onClick: (Business) -> Unit
    ) : RecyclerView.Adapter<BusinessSelectionAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemBusinessSelectionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBusinessSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.binding.businessName.text = item.name
            holder.binding.businessRole.text = "Owner" // Simplified for now

            if (item.id == selectedId) {
                holder.binding.selectedCheck.visibility = View.VISIBLE
            } else {
                holder.binding.selectedCheck.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
