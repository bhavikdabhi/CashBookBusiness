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
        holder.binding.tvDescription.text = category.description.ifEmpty { 
            if (category.type == "income") "Revenue stream" else "Expenditure"
        }
        holder.binding.progressUtilization.progress = category.utilization
        holder.binding.tvUtilizationPercent.text = "${category.utilization}%"
        
        // 1. Apply the user's selected icon glyph
        val context = holder.itemView.context
        val iconRes = context.resources.getIdentifier(category.iconResName, "drawable", context.packageName)
        if (iconRes != 0) {
            holder.binding.ivIcon.setImageResource(iconRes)
        } else {
            holder.binding.ivIcon.setImageResource(com.cashbk.app.R.drawable.ic_book) // Fallback
        }

        // 2. Apply the user's selected aesthetic tint
        val tintColor = try {
            android.graphics.Color.parseColor(category.colorHex)
        } catch (e: Exception) {
            android.graphics.Color.WHITE
        }
        
        holder.binding.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
        holder.binding.progressUtilization.setIndicatorColor(tintColor)
        holder.binding.tvName.setTextColor(tintColor)
        
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateData(newCategories: List<Category>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }
}
