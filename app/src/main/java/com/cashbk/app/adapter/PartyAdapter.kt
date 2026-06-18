package com.cashbk.app.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
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
        
        holder.binding.tvPartyName.text = party.name ?: "Unknown"
        holder.binding.tvPartyRole.text = party.role ?: "VENDOR"

        try {
            val defaultColorHex = String.format("#%06X", 0xFFFFFF and ContextCompat.getColor(context, R.color.color_80deea))
            val colorStr = party.colorHex ?: defaultColorHex
            val color = Color.parseColor(colorStr)
            holder.binding.ivPartyIcon.backgroundTintList = ColorStateList.valueOf(color)
            holder.binding.tvPartyName.setTextColor(color)
        } catch (e: Exception) {
            holder.binding.ivPartyIcon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_80deea))
        }

        // Load custom icon
        val iconName = party.iconResName ?: "ic_party_person"
        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
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
