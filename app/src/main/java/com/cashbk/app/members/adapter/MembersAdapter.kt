package com.cashbk.app.members.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemMemberBinding
import com.cashbk.app.members._bean.Member

class MembersAdapter(
    private val members: List<Member>,
    private val onDeleteClick: (Member) -> Unit
) : RecyclerView.Adapter<MembersAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.binding.tvMemberName.text = member.name.ifEmpty { "User" }
        holder.binding.tvMemberPhone.text = member.phone
        holder.binding.tvMemberRole.text = "Role: ${member.role.replaceFirstChar { it.uppercase() }}"

        holder.binding.btnDeleteMember.setOnClickListener { onDeleteClick(member) }
    }

    override fun getItemCount() = members.size
}