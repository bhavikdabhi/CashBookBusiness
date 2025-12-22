package com.cashbk.app.ui.business

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cashbk.app.R
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.databinding.FragmentCashbooksBinding
import com.cashbk.app.databinding.ItemNotebookBinding
import com.cashbk.app.ui.notebook.NotebookActivity
import com.google.firebase.database.*
import java.util.Calendar
import java.util.Locale
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.content.ContextCompat
import com.cashbk.app.databinding.PopupDeleteNotebookBinding
import com.cashbk.app.fragment.AddBusinessFragment
import com.cashbk.app.fragment.AddNotebookDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CashbooksFragment : Fragment() {

    private var _binding: FragmentCashbooksBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val notebookList = mutableListOf<Notebook>()
    private var currentBusinessId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCashbooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        database = FirebaseDatabase.getInstance().reference.child("notebooks")
        setupRecyclerView()


    }

    fun updateBusinessId(businessId: String?) {
        if (businessId == currentBusinessId) return
        currentBusinessId = businessId
        if (!currentBusinessId.isNullOrEmpty()) {
            fetchNotebooks()
        } else {
            notebookList.clear()
            notebookAdapter.notifyDataSetChanged()
        }
    }

    private fun fetchNotebooks() {
        val id = currentBusinessId ?: return
        database.orderByChild("businessId").equalTo(id)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notebookList.clear()
                    if (snapshot.exists()) {
                        for (notebookSnapshot in snapshot.children) {
                            val notebook = notebookSnapshot.getValue(Notebook::class.java)
                            notebook?.id = notebookSnapshot.key.orEmpty()
                            notebook?.let { notebookList.add(it) }
                        }
                    }
                    if (isAdded) {
                         notebookAdapter.notifyDataSetChanged()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isAdded) {
                        Toast.makeText(context, "Failed to load notebooks", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(notebookList, 
            onClick = { notebook ->
                val intent = Intent(context, NotebookActivity::class.java)
                intent.putExtra("notebookId", notebook.id)
                startActivity(intent)
            },
            onMenuClick = { notebook, view ->
                showNotebookMenu(notebook, view)
            }
        )

        binding.notebooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notebookAdapter
        }

        binding.addNotebookFab.setOnClickListener {

            val sheet = AddNotebookDialogFragment()
            sheet.arguments = Bundle().apply {
                val businessId = currentBusinessId
                putString("businessId", businessId)
            }
            sheet.show(parentFragmentManager, "AddNotebook")

        }
    }

    private fun showNotebookMenu(notebook: Notebook, view: View) {

        val wrapper = ContextThemeWrapper(requireContext(), R.style.PopupMenuTheme)
        val popup = PopupMenu(wrapper, view)

        popup.menuInflater.inflate(R.menu.menu_notebook_options, popup.menu)

        // ---- Force show icons ----
        try {
            val fields = popup.javaClass.getDeclaredField("mPopup")
            fields.isAccessible = true
            val menuPopupHelper = fields.get(popup)
            val helperClass = Class.forName(menuPopupHelper.javaClass.name)
            val setForceIcons = helperClass.getMethod(
                "setForceShowIcon",
                Boolean::class.javaPrimitiveType
            )
            setForceIcons.invoke(menuPopupHelper, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ---- Tint icons (API 21+ safe) ----
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            item.icon?.let { icon ->
                val wrapped = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(
                    wrapped,
                    ContextCompat.getColor(requireContext(), R.color.text_color)
                )
                item.icon = wrapped
            }
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> {
                    showRenameNotebookDialog(notebook)
                    true
                }
                R.id.action_delete -> {
                    showDeletePopup(view, notebook)
                    true
                }
                R.id.action_share -> {
                    Toast.makeText(requireContext(), "Share book", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



    private fun showDeleteNotebookDialog(notebook: Notebook) {

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.CashbkAlertDialog
        )
            .setTitle("Delete Notebook")
            .setMessage(
                "Are you sure you want to delete \"${notebook.name}\"?\nThis action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteNotebook(notebook)
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Red delete button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
    }


    private fun deleteNotebook(notebook: Notebook) {
        FirebaseDatabase.getInstance()
            .reference
            .child("notebooks")
            .child(notebook.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Notebook deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeletePopup(anchor: View, notebook: Notebook) {

        val popupBinding = PopupDeleteNotebookBinding.inflate(
            LayoutInflater.from(requireContext())
        )

        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Set data
        popupBinding.tvNotebookName.text = notebook.name

        // Close button (top-right ❌)
        popupBinding.btnClose.setOnClickListener {
            popupWindow.dismiss()
        }

        // Delete action
        popupBinding.btnDelete.setOnClickListener {
            popupWindow.dismiss()
            deleteNotebook(notebook)
        }

        popupWindow.apply {
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
        }

        // Show near menu button
        popupWindow.showAsDropDown(anchor, -200, 0)
    }

    private fun showRenameNotebookDialog(notebook: Notebook) {

        val sheet = AddNotebookDialogFragment().apply {
            arguments = Bundle().apply {
                putBoolean("isRename", true)
                putString("notebookId", notebook.id)
                putString("notebookName", notebook.name)
                putString("businessId", currentBusinessId) // optional but safe
            }
        }

        sheet.show(parentFragmentManager, "RenameNotebook")
    }



    inner class NotebookAdapter(
        private val notebooks: List<Notebook>,
        private val onClick: (Notebook) -> Unit,
        private val onMenuClick: (Notebook, View) -> Unit
    ) : RecyclerView.Adapter<NotebookAdapter.NotebookViewHolder>() {

        inner class NotebookViewHolder(val binding: ItemNotebookBinding) :
            RecyclerView.ViewHolder(binding.root) {
                fun bind(notebook: Notebook) {
                    binding.notebookName.text = notebook.name
                    
                    val calendar = Calendar.getInstance(Locale.getDefault())
                    calendar.timeInMillis = notebook.createdAt
                    val dateStr = DateFormat.format("MMM dd yyyy", calendar).toString()

                    // Fetch Member Count
                    FirebaseDatabase.getInstance().reference.child("members").child(notebook.id)
                        .addListenerForSingleValueEvent(object: ValueEventListener {
                             override fun onDataChange(snapshot: DataSnapshot) {
                                 val count = snapshot.childrenCount
                                 // Add 1 for the owner/creator if not in list? stored separately? 
                                 // Usually members list contains added members. Owner might be implicit.
                                 // Let's assume count is accurate enough for now. 
                                 binding.notebookDetails.text = "$count Members . Updated on $dateStr"
                             }
                             override fun onCancelled(error: DatabaseError) {
                                 binding.notebookDetails.text = "- Members . Updated on $dateStr"
                             }
                        })

                    // Fetch Balance
                    FirebaseDatabase.getInstance().reference.child("transactions").child(notebook.id)
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                var totalBalance = 0.0
                                for (child in snapshot.children) {
                                    val type = child.child("type").value as? String ?: ""
                                    // Handle amount which can be Double, Long, or String
                                    val amountObj = child.child("amount").value
                                    val amount = when (amountObj) {
                                        is Long -> amountObj.toDouble()
                                        is Double -> amountObj
                                        is String -> amountObj.toDoubleOrNull() ?: 0.0
                                        else -> 0.0
                                    }
                                    
                                    if (type == "in") {
                                        totalBalance += amount
                                    } else if (type == "out") {
                                        totalBalance -= amount
                                    }
                                }
                                
                                binding.tvBalance.text = "₹ $totalBalance"
                                val color = if (totalBalance >= 0) R.color.success else R.color.danger
                                binding.tvBalance.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, color))
                            }

                            override fun onCancelled(error: DatabaseError) {
                                binding.tvBalance.text = "Error"
                            }
                        })
                }
            }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotebookViewHolder {
            val itemBinding = ItemNotebookBinding.inflate(layoutInflater, parent, false)
            return NotebookViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
            val notebook = notebooks[position]
            holder.bind(notebook)
            holder.itemView.setOnClickListener { onClick(notebook) }
            holder.binding.btnNotebookOptions.setOnClickListener { onMenuClick(notebook, it) }
        }

        override fun getItemCount() = notebooks.size
    }
}
