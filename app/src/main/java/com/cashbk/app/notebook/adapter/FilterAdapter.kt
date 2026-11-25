package com.cashbk.app.notebook.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemFilterChipBinding

data class FilterItem(val type: String, var displayText: String, var isSelected: Boolean = false)

class FilterAdapter(
    private var filterItems: List<FilterItem>,
    private val onFilterClick: (FilterItem, Int) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    inner class FilterViewHolder(private val binding: ItemFilterChipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FilterItem, position: Int) {
            binding.chipItem.text = item.displayText
            binding.chipItem.isSelected = item.isSelected
            binding.chipItem.setOnClickListener {
                onFilterClick(item, position)
            }
            binding.chipItem.setOnCloseIconClickListener {
                onFilterClick(item, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(filterItems[position], position)
    }

    override fun getItemCount(): Int = filterItems.size

    fun updateItem(position: Int, newText: String, isActive: Boolean = false) {
        filterItems[position].displayText = newText
        filterItems[position].isSelected = isActive
        notifyItemChanged(position)
    }
}
