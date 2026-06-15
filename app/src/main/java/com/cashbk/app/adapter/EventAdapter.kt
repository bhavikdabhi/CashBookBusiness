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
        holder.binding.tvEventName.text = "📅 ${event.name}"

        // Description
        if (event.description.isNotEmpty()) {
            holder.binding.tvEventDescription.text = event.description
            holder.binding.tvEventDescription.visibility = View.VISIBLE
        } else {
            holder.binding.tvEventDescription.visibility = View.GONE
        }

        // Selected Date
        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateString = sdfDate.format(Date(event.date))
        holder.binding.tvEventDate.text = "🗓 Date: $dateString"

        // Added By
        val creatorName = if (event.createdBy == currentUserId) "You" else event.createdByName.ifEmpty { "Unknown" }
        holder.binding.tvAddedBy.text = "👤 Added By: $creatorName"

        // Created Date & Time
        val sdfDateTime = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val createdDateTimeString = sdfDateTime.format(Date(event.createdAt))
        holder.binding.tvCreatedTime.text = "🕒 Created: $createdDateTimeString"

        // Visibility
        holder.binding.tvVisibility.text = "👁 Visibility: ${event.visibility}"

        // Options permissions check:
        // - Owner or Admin: can edit/delete all events.
        // - Partner: can edit/delete their own events.
        // - Others: cannot edit/delete.
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
