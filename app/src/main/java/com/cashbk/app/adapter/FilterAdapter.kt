package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemFilterChipBinding

data class FilterItem(val type: String, var displayText: String)

class FilterAdapter(
    private var filterItems: List<FilterItem>,
    private val onFilterClick: (FilterItem, Int) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    inner class FilterViewHolder(private val binding: ItemFilterChipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FilterItem, position: Int) {
            binding.chipItem.text = item.displayText
            binding.chipItem.setOnClickListener {
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
    
    fun updateItem(position: Int, newText: String) {
        filterItems[position].displayText = newText
        notifyItemChanged(position)
    }
}
