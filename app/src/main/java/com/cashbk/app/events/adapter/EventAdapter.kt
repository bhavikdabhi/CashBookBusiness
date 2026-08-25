package com.cashbk.app.events.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    val eventTime: String = "", // Optional time e.g. "09:30 AM" or "03:00 PM"
    val createdBy: String = "",
    var createdByName: String = "",
    var createdByPhotoUrl: String = "",
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

        // 1. Time Column (Left Side)
        if (event.eventTime.isNotEmpty()) {
            val parts = event.eventTime.trim().split(" ")
            if (parts.size >= 2) {
                holder.binding.tvTimeHourMin.text = parts[0]
                holder.binding.tvTimeAmPm.text = parts[1].uppercase(Locale.US)
            } else {
                holder.binding.tvTimeHourMin.text = event.eventTime
                holder.binding.tvTimeAmPm.text = ""
            }
        } else {
            // Optional time not specified: default to 12:01 AM
            holder.binding.tvTimeHourMin.text = "12:01"
            holder.binding.tvTimeAmPm.text = "AM"
        }

        // 2. Event Name & Description
        holder.binding.tvEventName.text = event.name
        if (event.description.isNotEmpty()) {
            holder.binding.tvEventDescription.text = event.description
            holder.binding.tvEventDescription.visibility = View.VISIBLE
        } else {
            holder.binding.tvEventDescription.visibility = View.GONE
        }

        // 3. Notebook Tag
        val notebookName = notebooksMap[event.notebookId]
        if (!notebookName.isNullOrEmpty()) {
            holder.binding.tvTagNotebook.text = notebookName
            holder.binding.tvTagNotebook.visibility = View.VISIBLE

            // Custom green / amber badge styling matching image
            if (notebookName.lowercase(Locale.ROOT).contains("sales")) {
                val greenBg = ContextCompat.getColor(context, R.color.icon_bg_green)
                val greenText = ContextCompat.getColor(context, R.color.success)
                holder.binding.tvTagNotebook.backgroundTintList = ColorStateList.valueOf(greenBg)
                holder.binding.tvTagNotebook.setTextColor(greenText)
                holder.binding.tvTagNotebook.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(context, R.drawable.ic_book)?.apply {
                        setTint(greenText)
                    }, null, null, null
                )
            } else {
                val amberBg = Color.parseColor("#FEF3C7")
                val amberText = Color.parseColor("#D97706")
                holder.binding.tvTagNotebook.backgroundTintList = ColorStateList.valueOf(amberBg)
                holder.binding.tvTagNotebook.setTextColor(amberText)
                holder.binding.tvTagNotebook.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(context, R.drawable.ic_book)?.apply {
                        setTint(amberText)
                    }, null, null, null
                )
            }
        } else {
            holder.binding.tvTagNotebook.visibility = View.GONE
        }

        // 4. Visibility Tag
        holder.binding.tvTagVisibility.visibility = View.VISIBLE
        val visText = event.visibility
        holder.binding.tvTagVisibility.text = visText

        when (visText) {
            "Everyone" -> {
                val blueBg = Color.parseColor("#EFF6FF")
                val blueText = Color.parseColor("#2563EB")
                holder.binding.tvTagVisibility.backgroundTintList = ColorStateList.valueOf(blueBg)
                holder.binding.tvTagVisibility.setTextColor(blueText)
                holder.binding.tvTagVisibility.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(context, R.drawable.ic_people_)?.apply {
                        setTint(blueText)
                    }, null, null, null
                )
            }
            "Admin & Partner Only" -> {
                val purpleBg = Color.parseColor("#F3E8FF")
                val purpleText = Color.parseColor("#7C3AED")
                holder.binding.tvTagVisibility.backgroundTintList = ColorStateList.valueOf(purpleBg)
                holder.binding.tvTagVisibility.setTextColor(purpleText)
                holder.binding.tvTagVisibility.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(context, R.drawable.ic_security_enc)?.apply {
                        setTint(purpleText)
                    }, null, null, null
                )
            }
            else -> {
                val grayBg = Color.parseColor("#F1F5F9")
                val grayText = Color.parseColor("#475569")
                holder.binding.tvTagVisibility.backgroundTintList = ColorStateList.valueOf(grayBg)
                holder.binding.tvTagVisibility.setTextColor(grayText)
                holder.binding.tvTagVisibility.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(context, R.drawable.ic_security_enc)?.apply {
                        setTint(grayText)
                    }, null, null, null
                )
            }
        }

        // 5. Creator Footer Info Row
        val creatorName = if (event.createdByName.isNotEmpty()) event.createdByName else "Unknown User"
        holder.binding.tvCreatedBy.text = "Created by $creatorName"

        val sdfCreated = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US)
        holder.binding.tvCreatedAtTime.text = sdfCreated.format(Date(event.createdAt))

        if (event.createdByPhotoUrl.isNotEmpty()) {
            Glide.with(context)
                .load(event.createdByPhotoUrl)
                .placeholder(R.drawable.ic_default_avtar)
                .error(R.drawable.ic_default_avtar)
                .into(holder.binding.ivCreatorAvatar)
        } else {
            holder.binding.ivCreatorAvatar.setImageResource(R.drawable.ic_default_avtar)
        }

        // Options Menu Permission check
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
