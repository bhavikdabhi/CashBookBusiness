package com.cashbk.app.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemPartyPremiumBinding
import com.cashbk.app.dataclass.Party

class PartyAdapter(
    private var parties: List<Party>,
    private val onDeleteClick: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    inner class PartyViewHolder(val binding: ItemPartyPremiumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyPremiumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = parties[position]
        val context = holder.itemView.context
        
        holder.binding.tvPartyName.text = party.name
        holder.binding.tvPartyRole.text = party.role

        // Apply dynamic color tint to the icon container
        try {
            val color = Color.parseColor(party.colorHex)
            holder.binding.ivPartyIcon.backgroundTintList = ColorStateList.valueOf(color)
            holder.binding.tvPartyName.setTextColor(color)
        } catch (e: Exception) {
            holder.binding.ivPartyIcon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#40FFFFFF"))
        }

        // Load custom icon
        val resId = context.resources.getIdentifier(party.iconResName, "drawable", context.packageName)
        if (resId != 0) {
            holder.binding.ivPartyIcon.setImageResource(resId)
        } else {
            // Default to person icon
            val defaultResId = context.resources.getIdentifier("ic_party_person", "drawable", context.packageName)
            if (defaultResId != 0) holder.binding.ivPartyIcon.setImageResource(defaultResId)
        }

        holder.binding.ivDelete.setOnClickListener {
            onDeleteClick(party)
        }
    }

    override fun getItemCount(): Int = parties.size

    fun updateData(newPartiesList: List<Party>) {
        this.parties = newPartiesList
        notifyDataSetChanged()
    }
}
