package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.dataclass.Business
import com.cashbk.app.databinding.ItemBusinessSelectionBinding
import com.google.firebase.auth.FirebaseAuth

class BusinessSelectionAdapter(
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

        val context = holder.itemView.context
        holder.binding.businessRole.setTextColor(
            ContextCompat.getColor(context, if (isOwner) R.color.text_entryBy else R.color.text_disabled)
        )

        holder.binding.selectedCheck.visibility =
            if (item.id == selectedId) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = list.size
}
