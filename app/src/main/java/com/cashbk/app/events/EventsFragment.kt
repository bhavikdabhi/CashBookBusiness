package com.cashbk.app.events

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
import com.cashbk.app.R
import com.cashbk.app.utils.CustomAlertDialog
import com.cashbk.app.events.adapter.CalendarAdapter
import com.cashbk.app.events.adapter.CalendarDay
import com.cashbk.app.events.adapter.EventAdapter
import com.cashbk.app.events.adapter.EventItem
import com.cashbk.app.databinding.DialogAddEventBinding
import com.cashbk.app.databinding.FragmentEventsBinding
import com.cashbk.app.notebook._bean.Notebook
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EventsFragment : Fragment() {

    private var _binding: FragmentEventsBinding? = null
    private val binding get() = _binding!!

    private var currentBusinessId: String? = null
    private var currentUserRole: String = "reader" // Default to reader for safety

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var eventAdapter: EventAdapter

    private val calendarDaysList = mutableListOf<CalendarDay>()
    private val allEventsList = mutableListOf<EventItem>()
    private val filteredEventsList = mutableListOf<EventItem>()

    private var currentMonth = Calendar.getInstance()
    private var selectedDate = Calendar.getInstance()

    private val userMap = mutableMapOf<String, String>() // uid -> name
    private val notebooksList = mutableListOf<Notebook>()

    private var eventsListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null
    private var notebooksListener: ValueEventListener? = null
    private var notebooksQuery: Query? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setMidnight(selectedDate)
        setMidnight(currentMonth)

        setupCalendarRecyclerView()
        setupEventsRecyclerView()
        fetchUsers()

        binding.btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            rebuildCalendar()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            rebuildCalendar()
        }

        binding.fabAddEvent.setOnClickListener {
            showAddEventDialog()
        }

        // Hide FAB by default until role check passes
        binding.fabAddEvent.visibility = View.GONE

        rebuildCalendar()

        // Load data if current business ID is set
        loadDataForCurrentBusiness()
    }

    fun updateBusinessId(businessId: String?) {
        if (businessId == currentBusinessId) return
        currentBusinessId = businessId

        // Clear old listeners
        removeListeners()

        if (_binding != null) {
            loadDataForCurrentBusiness()
        }
    }

    private fun loadDataForCurrentBusiness() {
        val businessId = currentBusinessId
        if (!businessId.isNullOrEmpty()) {
            checkUserRole()
            fetchNotebooks()
            listenToEvents()
        } else {
            allEventsList.clear()
            filteredEventsList.clear()
            rebuildCalendar()
            filterEventsForSelectedDate()
            if (_binding != null) {
                binding.layoutShimmerEvents.stopPulseAnimation()
                binding.layoutShimmerEvents.visibility = View.GONE
            }
        }
    }

    private fun removeListeners() {
        if (currentBusinessId != null && eventsListener != null) {
            FirebaseDatabase.getInstance().reference.child("events").child(currentBusinessId!!)
                .removeEventListener(eventsListener!!)
        }
        usersListener?.let {
            FirebaseDatabase.getInstance().reference.child("users").removeEventListener(it)
        }
        notebooksListener?.let { listener ->
            notebooksQuery?.removeEventListener(listener)
        }
    }

    private fun checkUserRole() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val businessId = currentBusinessId ?: return

        FirebaseDatabase.getInstance().reference.child("businesses").child(businessId).get()
            .addOnSuccessListener { businessSnapshot ->
                if (_binding == null) return@addOnSuccessListener
                val ownerId = businessSnapshot.child("ownerId").value as? String
                if (ownerId == currentUserId) {
                    currentUserRole = "owner"
                    updateUIForRole()
                    return@addOnSuccessListener
                }

                // Check business members
                FirebaseDatabase.getInstance().reference.child("business_members").child(businessId).child(currentUserId).get()
                    .addOnSuccessListener { memberSnapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        if (memberSnapshot.exists()) {
                            val role = memberSnapshot.child("role").value as? String ?: "partner"
                            currentUserRole = role
                            updateUIForRole()
                        } else {
                            // Fetch all notebooks to check if user has admin/writer/reader roles
                            checkNotebookRoles(currentUserId, businessId)
                        }
                    }
            }
    }

    private fun checkNotebookRoles(currentUserId: String, businessId: String) {
        FirebaseDatabase.getInstance().reference.child("notebooks")
            .orderByChild("businessId").equalTo(businessId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    var highestRole = "reader"
                    val notebookIds = snapshot.children.mapNotNull { it.key }

                    if (notebookIds.isEmpty()) {
                        currentUserRole = highestRole
                        updateUIForRole()
                        return
                    }

                    var pendingChecks = notebookIds.size
                    for (notebookId in notebookIds) {
                        FirebaseDatabase.getInstance().reference.child("members").child(notebookId).child(currentUserId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(memberSnapshot: DataSnapshot) {
                                    if (memberSnapshot.exists()) {
                                        val role = memberSnapshot.child("role").value as? String ?: "reader"
                                        if (role == "admin") {
                                            highestRole = "admin"
                                        } else if (role == "writer" && highestRole != "admin") {
                                            highestRole = "writer"
                                        }
                                    }
                                    pendingChecks--
                                    if (pendingChecks == 0) {
                                        currentUserRole = highestRole
                                        updateUIForRole()
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    pendingChecks--
                                    if (pendingChecks == 0) {
                                        currentUserRole = highestRole
                                        updateUIForRole()
                                    }
                                }
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUIForRole() {
        if (_binding == null) return
        val canCreate = currentUserRole == "owner" || currentUserRole == "admin" || currentUserRole == "partner"
        binding.fabAddEvent.visibility = if (canCreate) View.VISIBLE else View.GONE

        // Refresh event list to adjust edit/delete buttons
        filterEventsForSelectedDate()
    }

    private fun fetchUsers() {
        usersListener?.let { FirebaseDatabase.getInstance().reference.child("users").removeEventListener(it) }

        usersListener = FirebaseDatabase.getInstance().reference.child("users")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    userMap.clear()
                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        val name = child.child("name").value as? String ?: "Unknown User"
                        userMap[uid] = name
                    }
                    // Refresh current creator names
                    allEventsList.forEach {
                        it.createdByName = userMap[it.createdBy] ?: "Unknown User"
                    }
                    filterEventsForSelectedDate()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchNotebooks() {
        val businessId = currentBusinessId ?: return
        notebooksListener?.let { notebooksQuery?.removeEventListener(it) }

        notebooksQuery = FirebaseDatabase.getInstance().reference.child("notebooks")
            .orderByChild("businessId").equalTo(businessId)
        notebooksListener = notebooksQuery?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                notebooksList.clear()
                for (child in snapshot.children) {
                    val notebook = child.getValue(Notebook::class.java)
                    notebook?.id = child.key.orEmpty()
                    notebook?.let { notebooksList.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToEvents() {
        if (currentBusinessId.isNullOrEmpty()) return

        if (_binding != null) {
            binding.layoutShimmerEvents.visibility = View.VISIBLE
            binding.layoutShimmerEvents.startPulseAnimation()
            binding.rvEvents.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
        }

        eventsListener = FirebaseDatabase.getInstance().reference.child("events").child(currentBusinessId!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return

                    allEventsList.clear()
                    for (child in snapshot.children) {
                        val event = child.getValue(EventItem::class.java)
                        if (event != null) {
                            event.id = child.key.orEmpty()
                            event.createdByName = userMap[event.createdBy] ?: "Unknown User"
                            allEventsList.add(event)
                        }
                    }

                    // Artificially delay hiding the shimmer by 2 seconds so the effect is visible
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (_binding == null) return@postDelayed
                        binding.layoutShimmerEvents.stopPulseAnimation()
                        binding.layoutShimmerEvents.visibility = View.GONE
                        rebuildCalendar()
                        filterEventsForSelectedDate()
                    }, 2000)
                }

                override fun onCancelled(error: DatabaseError) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (_binding == null) return@postDelayed
                        binding.layoutShimmerEvents.stopPulseAnimation()
                        binding.layoutShimmerEvents.visibility = View.GONE
                        if (isAdded && FirebaseAuth.getInstance().currentUser != null) {
                            Toast.makeText(context, "Failed to load events: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, 2000)
                }
            })
    }

    private fun setupCalendarRecyclerView() {
        val ctx = context ?: return
        calendarAdapter = CalendarAdapter(calendarDaysList) { day ->
            if (day.date != null) {
                selectedDate.time = day.date
                rebuildCalendar()
                filterEventsForSelectedDate()
            }
        }
        binding.rvCalendar.apply {
            layoutManager = GridLayoutManager(ctx, 7)
            adapter = calendarAdapter
        }
    }

    private fun setupEventsRecyclerView() {
        val ctx = context ?: return
        val notebooksMap = notebooksList.associate { it.id to it.name }
        eventAdapter = EventAdapter(
            filteredEventsList,
            currentUserRole,
            notebooksMap,
            onEditClick = { event -> showEditEventDialog(event) },
            onDeleteClick = { event -> deleteEvent(event) }
        )
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = eventAdapter
        }
    }

    private fun rebuildCalendar() {
        if (_binding == null) return
        calendarDaysList.clear()

        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        setMidnight(cal)

        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 2 = Mon, ...
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Previous Month Padding (Monday-start alignment)
        val prevMonthCal = cal.clone() as Calendar
        prevMonthCal.add(Calendar.MONTH, -1)
        val maxDayPrev = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val paddingCount = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

        val tempDaysList = mutableListOf<CalendarDay>()

        for (i in paddingCount - 1 downTo 0) {
            val d = Calendar.getInstance()
            d.set(Calendar.YEAR, prevMonthCal.get(Calendar.YEAR))
            d.set(Calendar.MONTH, prevMonthCal.get(Calendar.MONTH))
            d.set(Calendar.DAY_OF_MONTH, maxDayPrev - i)
            setMidnight(d)
            tempDaysList.add(
                CalendarDay(
                    d.time,
                    d.get(Calendar.DAY_OF_MONTH).toString(),
                    false,
                    isToday(d)
                )
            )
        }

        // Current Month Days
        for (day in 1..maxDay) {
            val d = Calendar.getInstance()
            d.set(Calendar.YEAR, year)
            d.set(Calendar.MONTH, month)
            d.set(Calendar.DAY_OF_MONTH, day)
            setMidnight(d)
            val isSel = isSameDay(d, selectedDate)
            tempDaysList.add(CalendarDay(d.time, day.toString(), true, isToday(d), isSel))
        }

        // Next Month Padding (populate up to 42 cells to check for overflow)
        val nextMonthCal = cal.clone() as Calendar
        nextMonthCal.add(Calendar.MONTH, 1)
        val remainingCells = 42 - tempDaysList.size
        for (day in 1..remainingCells) {
            val d = Calendar.getInstance()
            d.set(Calendar.YEAR, nextMonthCal.get(Calendar.YEAR))
            d.set(Calendar.MONTH, nextMonthCal.get(Calendar.MONTH))
            d.set(Calendar.DAY_OF_MONTH, day)
            setMidnight(d)
            tempDaysList.add(CalendarDay(d.time, day.toString(), false, isToday(d)))
        }

        // Move current month days from Row 6 (indices 35-41) to Row 1 (indices 0-6)
        for (i in 35..41) {
            val day = tempDaysList[i]
            if (day.isCurrentMonth) {
                val targetIndex = i - 35
                tempDaysList[targetIndex] = day
            }
        }

        // Keep exactly 5 rows (35 cells)
        calendarDaysList.addAll(tempDaysList.subList(0, 35))

        // Set event indicator dots
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        for (day in calendarDaysList) {
            if (day.date != null) {
                day.hasEvents = allEventsList.any { event ->
                    event.date == day.date.time && isEventVisibleToUser(event, currentUserId)
                }
            }
        }

        calendarAdapter.notifyDataSetChanged()

        // Update Labels
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvMonthYear.text = sdf.format(currentMonth.time)
    }

    private fun filterEventsForSelectedDate() {
        if (_binding == null) return
        filteredEventsList.clear()

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val targetDateMs = selectedDate.timeInMillis

        val matches = allEventsList.filter { event ->
            event.date == targetDateMs && isEventVisibleToUser(event, currentUserId)
        }.sortedByDescending { it.createdAt }

        filteredEventsList.addAll(matches)

        // Update active count badge
        binding.tvActiveCount.text = "${filteredEventsList.size} Active"

        // Notify adapter updates with current role context
        setupEventsRecyclerView()

        if (filteredEventsList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvEvents.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvEvents.visibility = View.VISIBLE

        }
    }

    private fun isEventVisibleToUser(event: EventItem, currentUserId: String): Boolean {
        if (currentUserRole == "owner" || currentUserRole == "partner" || currentUserRole == "admin") {
            return true
        }
        if (event.createdBy == currentUserId) {
            return true
        }
        return when (event.visibility) {
            "Everyone" -> true
            "Specific Members" -> event.visibleToMembers[currentUserId] == true
            else -> false
        }
    }

    private fun showAddEventDialog() {
        showEventFormDialog(null)
    }

    private fun showEditEventDialog(event: EventItem) {
        showEventFormDialog(event)
    }

    private fun showEventFormDialog(editingEvent: EventItem?) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val dialogBinding = DialogAddEventBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
            .setView(dialogBinding.root)
            .create()

        // Set title
        dialogBinding.tvTitle.text = if (editingEvent == null) "Create Business Event" else "Edit Business Event"
        dialogBinding.btnSaveEvent.text = if (editingEvent == null) "Create Event" else "Save Changes"

        // Auto-fill "Added By"
        val currentUserName = userMap[currentUserId] ?: "You"

        // Date management
        var selectedEventDateMs = editingEvent?.date ?: selectedDate.timeInMillis
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        dialogBinding.etEventDate.setText(sdf.format(Date(selectedEventDateMs)))

        dialogBinding.etEventDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Event Date")
                .setSelection(selectedEventDateMs)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = selection
                setMidnight(calendar)
                selectedEventDateMs = calendar.timeInMillis
                dialogBinding.etEventDate.setText(sdf.format(Date(selectedEventDateMs)))
            }
            datePicker.show(parentFragmentManager, "EventDatePicker")
        }

        // Fill current fields if editing
        if (editingEvent != null) {
            dialogBinding.etEventName.setText(editingEvent.name)
            dialogBinding.etEventDescription.setText(editingEvent.description)
        }

        // Specific Members selection state
        val selectedMemberUidsMap = mutableMapOf<String, Boolean>()
        if (editingEvent != null) {
            selectedMemberUidsMap.putAll(editingEvent.visibleToMembers)
        }

        fun updateSelectedMembersText() {
            val selectedNames = selectedMemberUidsMap.filter { it.value }.keys.mapNotNull { uid ->
                userMap[uid]
            }
            dialogBinding.tvSelectedMembers.text = if (selectedNames.isEmpty()) {
                "Selected: None"
            } else {
                "Selected: ${selectedNames.joinToString(", ")}"
            }
        }

        var selectedNotebookId = editingEvent?.notebookId ?: ""

        // Setup Notebook selection
        val notebookNames = notebooksList.map { it.name }
        val notebookAdapter = ArrayAdapter(requireContext(), R.layout.item_dropdown, notebookNames)
        dialogBinding.actvNotebook.setAdapter(notebookAdapter)

        if (selectedNotebookId.isNotEmpty()) {
            val currentNotebook = notebooksList.find { it.id == selectedNotebookId }
            if (currentNotebook != null) {
                dialogBinding.actvNotebook.setText(currentNotebook.name, false)
            }
        } else if (notebooksList.isNotEmpty()) {
            dialogBinding.actvNotebook.setText(notebooksList[0].name, false)
            selectedNotebookId = notebooksList[0].id
        }

        dialogBinding.actvNotebook.setOnItemClickListener { _, _, position, _ ->
            selectedNotebookId = notebooksList[position].id
            // Reset selected members if notebook changes
            selectedMemberUidsMap.clear()
            updateSelectedMembersText()
        }

        // Setup Visibility selection
        val visibilityOptions = listOf("Everyone", "Specific Members", "Admin & Partner Only", "Only Me")
        val visibilityAdapter =
            ArrayAdapter(requireContext(), R.layout.item_dropdown, visibilityOptions)
        dialogBinding.actvVisibility.setAdapter(visibilityAdapter)

        val initialVisibility = editingEvent?.visibility ?: "Everyone"
        dialogBinding.actvVisibility.setText(initialVisibility, false)

        dialogBinding.actvVisibility.setOnItemClickListener { _, _, position, _ ->
            val visibility = visibilityOptions[position]
            if (visibility == "Specific Members") {
                dialogBinding.layoutMemberSelection.visibility = View.VISIBLE
                updateSelectedMembersText()
            } else {
                dialogBinding.layoutMemberSelection.visibility = View.GONE
            }
        }

        if (initialVisibility == "Specific Members") {
            dialogBinding.layoutMemberSelection.visibility = View.VISIBLE
            updateSelectedMembersText()
        }

        // Specific member picker click handler
        dialogBinding.btnSelectMembers.setOnClickListener {
            if (selectedNotebookId.isEmpty()) {
                Toast.makeText(context, "Select a notebook first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Fetch notebook members
            FirebaseDatabase.getInstance().reference.child("members").child(selectedNotebookId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val memberUids = snapshot.children.mapNotNull { it.key }
                        if (memberUids.isEmpty()) {
                            Toast.makeText(context, "No members found in this notebook", Toast.LENGTH_SHORT).show()
                            return
                        }

                        // Resolve names
                        val memberNames = memberUids.map { uid -> userMap[uid] ?: "Unknown User" }.toTypedArray()
                        val checkedItems = memberUids.map { uid -> selectedMemberUidsMap[uid] == true }.toBooleanArray()

                        MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
                            .setTitle("Select Members")
                            .setMultiChoiceItems(memberNames, checkedItems) { _, index, isChecked ->
                                val uid = memberUids[index]
                                if (isChecked) {
                                    selectedMemberUidsMap[uid] = true
                                } else {
                                    selectedMemberUidsMap.remove(uid)
                                }
                            }
                            .setPositiveButton("Done") { _, _ ->
                                updateSelectedMembersText()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        dialogBinding.btnSaveEvent.setOnClickListener {
            val eventName = dialogBinding.etEventName.text.toString().trim()
            val eventDesc = dialogBinding.etEventDescription.text.toString().trim()
            val visibility = dialogBinding.actvVisibility.text.toString()

            if (eventName.isEmpty()) {
                dialogBinding.etEventName.error = "Event Name is required"
                return@setOnClickListener
            }

            if (eventName.length > 100) {
                dialogBinding.etEventName.error = "Event Name cannot exceed 100 characters"
                return@setOnClickListener
            }

            if (eventDesc.length > 1000) {
                dialogBinding.etEventDescription.error = "Description cannot exceed 1000 characters"
                return@setOnClickListener
            }

            if (selectedNotebookId.isEmpty()) {
                Toast.makeText(context, "Please select a scoped notebook", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (visibility == "Specific Members" && selectedMemberUidsMap.none { it.value }) {
                Toast.makeText(context, "Select at least one member", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val businessId = currentBusinessId ?: return@setOnClickListener
            val ref = FirebaseDatabase.getInstance().reference.child("events").child(businessId)

            val eventId = editingEvent?.id ?: ref.push().key ?: return@setOnClickListener
            val timestamp = editingEvent?.createdAt ?: System.currentTimeMillis()

            val newEvent = EventItem(
                id = eventId,
                name = eventName,
                description = eventDesc,
                date = selectedEventDateMs,
                createdBy = editingEvent?.createdBy ?: currentUserId,
                createdAt = timestamp,
                visibility = visibility,
                notebookId = selectedNotebookId,
                visibleToMembers = if (visibility == "Specific Members") selectedMemberUidsMap else emptyMap()
            )

            ref.child(eventId).setValue(newEvent)
                .addOnSuccessListener {
                    Toast.makeText(context, if (editingEvent == null) "Event created" else "Event updated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun deleteEvent(event: EventItem) {
        val businessId = currentBusinessId ?: return
        CustomAlertDialog(requireContext())
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete this event?")
            .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(requireContext(), R.color.danger))
            .setPositiveButton("Delete") {
                FirebaseDatabase.getInstance().reference.child("events").child(businessId).child(event.id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Event deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun isToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return isSameDay(cal, today)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    override fun onDestroyView() {
        removeListeners()
        super.onDestroyView()
        _binding = null
    }
}