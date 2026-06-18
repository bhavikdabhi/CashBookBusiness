package com.cashbk.app.partner.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemPartnerBinding
import com.cashbk.app.partner.Partner
import java.util.Locale

class PartnerAdapter(
    private var partners: List<Partner>,
    private val onRemoveClick: (Partner) -> Unit
) : RecyclerView.Adapter<PartnerAdapter.PartnerViewHolder>() {

    inner class PartnerViewHolder(val binding: ItemPartnerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartnerViewHolder {
        val binding = ItemPartnerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartnerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartnerViewHolder, position: Int) {
        val partner = partners[position]
        holder.binding.tvPartnerName.text = partner.name.ifEmpty { "Unknown Partner" }

        val displayRole = partner.role.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        holder.binding.tvPartnerRole.text = "$displayRole • ${partner.phone}"

        holder.binding.btnRemovePartner.setOnClickListener {
            onRemoveClick(partner)
        }
    }

    override fun getItemCount() = partners.size

    fun updateData(newPartners: List<Partner>) {
        partners = newPartners
        notifyDataSetChanged()
    }
}