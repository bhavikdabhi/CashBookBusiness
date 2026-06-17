package com.cashbk.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.databinding.ItemEventBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class EventItem(
    var id: String = "",
    val name: String = "",
    val description: String = "",
    val date: Long = 0L,
    val createdBy: String = "",
    var createdByName: String = "",
    val createdAt: Long = 0L,
    val visibility: String = "Everyone",
    val notebookId: String = "",
    val visibleToMembers: Map<String, Boolean> = emptyMap()
)

class EventAdapter(
    private val events: List<EventItem>,
    private val currentUserRole: String, // "owner", "partner", "admin", "writer", "reader"
    private val notebooksMap: Map<String, String>, // notebookId -> name
    private val onEditClick: (EventItem) -> Unit,
    private val onDeleteClick: (EventItem) -> Unit
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        val context = holder.binding.root.context
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Event Name
        holder.binding.tvEventName.text = event.name

        // Description
        if (event.description.isNotEmpty()) {
            holder.binding.tvEventDescription.text = event.description
            holder.binding.tvEventDescription.visibility = View.VISIBLE
        } else {
            holder.binding.tvEventDescription.visibility = View.GONE
        }

        // Selected Date & Time (formatted like OCT 13, 09:00)
        val calendarDate = Calendar.getInstance().apply { timeInMillis = event.date }
        val calendarTime = Calendar.getInstance().apply { timeInMillis = event.createdAt }
        calendarDate.set(Calendar.HOUR_OF_DAY, calendarTime.get(Calendar.HOUR_OF_DAY))
        calendarDate.set(Calendar.MINUTE, calendarTime.get(Calendar.MINUTE))
        val formattedDateTime = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(calendarDate.time).uppercase(Locale.US)
        holder.binding.tvEventDate.text = formattedDateTime

        // Set Icon based on name keywords
        val nameLower = event.name.lowercase(Locale.ROOT)
        val iconRes = when {
            nameLower.contains("audit") || nameLower.contains("tax") || nameLower.contains("distribution") || 
            nameLower.contains("pay") || nameLower.contains("dividend") || nameLower.contains("finance") -> {
                R.drawable.ic_business
            }
            nameLower.contains("summit") || nameLower.contains("meeting") || nameLower.contains("partner") || 
            nameLower.contains("member") || nameLower.contains("team") || nameLower.contains("shareholder") || 
            nameLower.contains("board") || nameLower.contains("party") -> {
                R.drawable.ic_party_group
            }
            else -> R.drawable.ic_event
        }
        holder.binding.ivEventIcon.setImageResource(iconRes)

        // Tags logic
        // Tag 1: Notebook Name
        val notebookName = notebooksMap[event.notebookId]
        if (!notebookName.isNullOrEmpty()) {
            holder.binding.tvTagNotebook.text = notebookName
            holder.binding.tvTagNotebook.visibility = View.VISIBLE
        } else {
            holder.binding.tvTagNotebook.visibility = View.GONE
        }

        // Tag 2: Visibility Tag
        val visibilityText = when (event.visibility) {
            "Admin & Partner Only" -> "Admin Only"
            "Specific Members" -> "Members Only"
            "Only Me" -> "Private"
            else -> "" // Don't show tag if Everyone is visibility
        }
        if (visibilityText.isNotEmpty()) {
            holder.binding.tvTagVisibility.text = visibilityText
            holder.binding.tvTagVisibility.visibility = View.VISIBLE
            
            // Adjust margin based on first tag visibility
            val params = holder.binding.tvTagVisibility.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = if (holder.binding.tvTagNotebook.visibility == View.VISIBLE) {
                context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
            } else {
                0
            }
            holder.binding.tvTagVisibility.layoutParams = params
        } else {
            holder.binding.tvTagVisibility.visibility = View.GONE
        }

        // Options permissions check:
        val hasEditDeletePermission = currentUserRole == "owner" || 
                currentUserRole == "admin" || 
                (currentUserRole == "partner" && event.createdBy == currentUserId)

        if (hasEditDeletePermission) {
            holder.binding.btnEventOptions.visibility = View.VISIBLE
            holder.binding.btnEventOptions.setOnClickListener { view ->
                val popup = PopupMenu(context, view)
                popup.menu.add("Edit")
                popup.menu.add("Delete")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "Edit" -> {
                            onEditClick(event)
                            true
                        }
                        "Delete" -> {
                            onDeleteClick(event)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        } else {
            holder.binding.btnEventOptions.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = events.size
}
