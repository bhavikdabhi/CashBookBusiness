package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemPartyCategoryBinding
import com.cashbk.app.dataclass.Party

class PartyAdapter(
    private var parties: List<Party>,
    private val onDeleteClick: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    inner class PartyViewHolder(val binding: com.cashbk.app.databinding.ItemPartyPremiumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = com.cashbk.app.databinding.ItemPartyPremiumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = parties[position]
        holder.binding.tvName.text = party.name
        holder.binding.tvRoleLabel.text = "ROLE: ${party.role}"
        
        // Priority Tag
        if (party.priorityTag.isNotEmpty()) {
            holder.binding.tvPriorityTag.visibility = android.view.View.VISIBLE
            holder.binding.tvPriorityTag.text = party.priorityTag
        } else {
            holder.binding.tvPriorityTag.text = "PARTNER"
        }

        // Contact Info
        val contactText = when {
            party.contact.isNotEmpty() -> party.contact
            party.email.isNotEmpty() -> party.email
            else -> "No contact info"
        }
        holder.binding.tvContact.text = contactText
        
        holder.binding.ivAvatar.setImageResource(com.cashbk.app.R.drawable.ic_action_group)
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(party) }
    }

    override fun getItemCount(): Int = parties.size

    fun updateData(newParties: List<Party>) {
        this.parties = newParties
        notifyDataSetChanged()
    }
}
