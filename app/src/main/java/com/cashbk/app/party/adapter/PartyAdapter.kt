package com.cashbk.app.party.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.databinding.ItemPartyPremiumBinding
import com.cashbk.app.party._bean.Party

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

        holder.binding.tvPartyName.text = party.name.ifBlank { "Unknown" }
        holder.binding.tvPartyRole.text = party.role.ifBlank { "VENDOR" }.uppercase()

        if (party.priorityTag.isNotBlank()) {
            holder.binding.tvPriorityTag.visibility = android.view.View.VISIBLE
            holder.binding.tvPriorityTag.text = party.priorityTag.uppercase()
        } else {
            holder.binding.tvPriorityTag.visibility = android.view.View.GONE
        }

        val contactDisplay = listOf(party.contact, party.email).filter { it.isNotBlank() }.joinToString(" • ")
        if (contactDisplay.isNotEmpty()) {
            holder.binding.layoutContact.visibility = android.view.View.VISIBLE
            holder.binding.tvContact.text = contactDisplay
        } else {
            holder.binding.layoutContact.visibility = android.view.View.GONE
        }

        try {
            val defaultColor = ContextCompat.getColor(context, R.color.primary)
            val accentColor = if (!party.colorHex.isNullOrBlank()) Color.parseColor(party.colorHex) else defaultColor
            val softBg = androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 36)
            holder.binding.cardPartyIcon.setCardBackgroundColor(softBg)
            holder.binding.cardPartyIcon.strokeColor = accentColor
            holder.binding.ivPartyIcon.imageTintList = ColorStateList.valueOf(accentColor)
        } catch (e: Exception) {
            val fallback = ContextCompat.getColor(context, R.color.primary)
            holder.binding.cardPartyIcon.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bottom_nav_active_bg))
            holder.binding.cardPartyIcon.strokeColor = fallback
            holder.binding.ivPartyIcon.imageTintList = ColorStateList.valueOf(fallback)
        }

        // Load icon from party data (with fallback mapping for legacy iconResName / role values)
        val iconName = party.iconResName.ifBlank { "" }
        val directResId = if (iconName.isNotEmpty()) {
            context.resources.getIdentifier(iconName, "drawable", context.packageName)
        } else 0

        val resolvedResId = if (directResId != 0) {
            directResId
        } else {
            when (iconName.lowercase()) {
                "ic_party_shop" -> R.drawable.ic_party_vendor
                "ic_party_tool" -> R.drawable.ic_party_contractor
                "ic_party_employee" -> R.drawable.ic_party_employee
                "ic_party_person" -> R.drawable.ic_party_group
                else -> when (party.role.uppercase()) {
                    "VENDOR" -> R.drawable.ic_party_vendor
                    "CONTRACTOR" -> R.drawable.ic_party_contractor
                    "EMPLOYEE" -> R.drawable.ic_party_employee
                    "PARTNER", "COMPANY", "ORGANIZATION" -> R.drawable.ic_party_building
                    else -> R.drawable.ic_party_group
                }
            }
        }
        holder.binding.ivPartyIcon.setImageResource(resolvedResId)

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