package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.databinding.ItemPartyCategoryBinding
import com.cashbk.app.dataclass.Category

class CategoryAdapter(
    private var categories: List<Category>,
    private val onDeleteClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(val binding: com.cashbk.app.databinding.ItemCategoryPremiumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = com.cashbk.app.databinding.ItemCategoryPremiumBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.binding.tvName.text = category.name
        holder.binding.tvDescription.text = category.description.ifEmpty { "Transaction processing" }
        holder.binding.progressUtilization.progress = category.utilization
        holder.binding.tvUtilizationPercent.text = "${category.utilization}%"
        
        // Mock icons for common categories
        val iconRes = when (category.name.lowercase()) {
            "sales" -> com.cashbk.app.R.drawable.ic_cash_volate
            "rent" -> com.cashbk.app.R.drawable.ic_business
            "salary" -> com.cashbk.app.R.drawable.ic_action_group
            else -> com.cashbk.app.R.drawable.ic_book
        }
        holder.binding.ivIcon.setImageResource(iconRes)
        
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateData(newCategories: List<Category>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }
}
