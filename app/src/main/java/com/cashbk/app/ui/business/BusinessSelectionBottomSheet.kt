package com.cashbk.app.ui.business

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.data.model.Business
import com.cashbk.app.databinding.DialogSelectBusinessBinding
import com.cashbk.app.databinding.ItemBusinessSelectionBinding
import com.cashbk.app.fragment.AddBusinessFragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.core.content.ContextCompat
import com.cashbk.app.R


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
            val dialog = AddBusinessFragment()
            dialog.show(parentFragmentManager, "AddBusinessDialog")
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

        businessList.clear()

        // 1️⃣ Fetch OWNED businesses
        db.child("businesses")
            .orderByChild("ownerId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    for (child in snapshot.children) {
                        val business = child.getValue(Business::class.java)
                        business?.id = child.key.orEmpty()
                        business?.let { businessList.add(it) }
                    }

                    // 2️⃣ Fetch SHARED businesses after owned loaded
                    fetchSharedBusinesses(userId)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchSharedBusinesses(userId: String) {

        val db = FirebaseDatabase.getInstance().reference

        db.child("business_members")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val sharedBusinessIds = mutableListOf<String>()

                    // Find businesses where current user is member
                    for (businessSnap in snapshot.children) {
                        if (businessSnap.child(userId).exists()) {
                            sharedBusinessIds.add(businessSnap.key ?: "")
                        }
                    }

                    if (sharedBusinessIds.isEmpty()) {
                        adapter.notifyDataSetChanged()
                        return
                    }

                    // Fetch each shared business
                    sharedBusinessIds.forEach { businessId ->
                        db.child("businesses").child(businessId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(businessSnap: DataSnapshot) {
                                    val business = businessSnap.getValue(Business::class.java)
                                    business?.id = businessSnap.key.orEmpty()

                                    // Avoid duplicates (owner already added)
                                    if (business != null && businessList.none { it.id == business.id }) {
                                        businessList.add(business)
                                    }

                                    adapter.notifyDataSetChanged()
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })
                    }
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

            val isOwner = item.ownerId == FirebaseAuth.getInstance().currentUser?.uid

            holder.binding.businessRole.text =
                if (isOwner) "Owner" else "Partner"

            holder.binding.businessRole.setTextColor(
                        ContextCompat.getColor(requireContext(), if (isOwner) R.color.text_entryBy else R.color.text_disabled)
            )


            holder.binding.selectedCheck.visibility =
                if (item.id == selectedId) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
