package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.databinding.ItemCalendarDayBinding
import java.util.Date

data class CalendarDay(
    val date: Date?,
    val dayNumber: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    var isSelected: Boolean = false,
    var hasEvents: Boolean = false
)

class CalendarAdapter(
    private val list: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvDayNumber.text = item.dayNumber

        // Reset backgrounds and colors
        holder.binding.tvDayNumber.background = null
        val context = holder.binding.tvDayNumber.context

        if (item.date == null) {
            holder.binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            holder.binding.tvDayNumber.alpha = 0.4f
            holder.binding.viewEventDot.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        } else {
            holder.binding.tvDayNumber.alpha = 1.0f

            if (item.isSelected) {
                holder.binding.tvDayNumber.setBackgroundResource(R.drawable.bg_calendar_day_selected)
                holder.binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else if (item.isToday) {
                holder.binding.tvDayNumber.setBackgroundResource(R.drawable.bg_calendar_day_today)
                holder.binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.primary_color))
            } else {
                if (item.isCurrentMonth) {
                    holder.binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_color))
                } else {
                    holder.binding.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.gray))
                    holder.binding.tvDayNumber.alpha = 0.6f
                }
            }

            holder.binding.viewEventDot.visibility = if (item.hasEvents) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onDayClick(item)
            }
        }
    }

    override fun getItemCount(): Int = list.size
}
