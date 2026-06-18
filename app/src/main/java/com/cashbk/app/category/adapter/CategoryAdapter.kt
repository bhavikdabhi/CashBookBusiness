package com.cashbk.app.category.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.category._bean.Category
import com.cashbk.app.databinding.ItemCategoryPremiumBinding

class CategoryAdapter(
    private var categories: List<Category>,
    private val onDeleteClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(val binding: ItemCategoryPremiumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryPremiumBinding.inflate(
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
            holder.binding.ivIcon.setImageResource(R.drawable.ic_book) // Fallback
        }

        // 2. Apply the user's selected aesthetic tint
        val tintColor = try {
            Color.parseColor(category.colorHex)
        } catch (e: Exception) {
            Color.WHITE
        }

        holder.binding.ivIcon.imageTintList = ColorStateList.valueOf(tintColor)
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