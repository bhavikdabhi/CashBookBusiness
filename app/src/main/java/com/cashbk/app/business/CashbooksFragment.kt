package com.cashbk.app.business

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.notebook.adapter.NotebookAdapter
import com.cashbk.app.databinding.FragmentCashbooksBinding
import com.cashbk.app.databinding.PopupDeleteNotebookBinding
import com.cashbk.app.notebook._bean.Notebook
import com.cashbk.app.notebook.AddNotebookDialogFragment
import com.cashbk.app.notebook.NotebookActivity
import com.cashbk.app.utils.CustomOptionsMenu
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class CashbooksFragment : Fragment() {

    private var _binding: FragmentCashbooksBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val notebookList = mutableListOf<Notebook>()
    private var currentBusinessId: String? = null
    private var notebooksQuery: Query? = null
    private var notebooksListener: ValueEventListener? = null

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
            if (_binding != null) {
                binding.layoutShimmerNotebooks.stopPulseAnimation()
                binding.layoutShimmerNotebooks.visibility = View.GONE
            }
        }
    }

    private fun fetchNotebooks() {
        val id = currentBusinessId ?: return
        notebooksListener?.let { notebooksQuery?.removeEventListener(it) }

        if (_binding != null) {
            binding.layoutShimmerNotebooks.visibility = View.VISIBLE
            binding.layoutShimmerNotebooks.startPulseAnimation()
            binding.notebooksRecyclerView.visibility = View.GONE
            binding.layoutEmpty.visibility = View.GONE
        }

        notebooksQuery = database.orderByChild("businessId").equalTo(id)
        notebooksListener = notebooksQuery?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notebookList.clear()
                if (snapshot.exists()) {
                    for (notebookSnapshot in snapshot.children) {
                        val notebook = notebookSnapshot.getValue(Notebook::class.java)
                        notebook?.id = notebookSnapshot.key.orEmpty()
                        notebook?.let { notebookList.add(it) }
                    }
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerNotebooks.stopPulseAnimation()
                    binding.layoutShimmerNotebooks.visibility = View.GONE

                    if (isAdded) {
                         notebookAdapter.notifyDataSetChanged()
                         if (notebookList.isEmpty()) {
                             binding.layoutEmpty.visibility = View.VISIBLE
                             binding.notebooksRecyclerView.visibility = View.GONE
                         } else {
                             binding.layoutEmpty.visibility = View.GONE
                             binding.notebooksRecyclerView.visibility = View.VISIBLE
                         }
                    }
                }, 2000)
            }

            override fun onCancelled(error: DatabaseError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerNotebooks.stopPulseAnimation()
                    binding.layoutShimmerNotebooks.visibility = View.GONE
                    if (isAdded) {
                        Toast.makeText(context, "Failed to load notebooks", Toast.LENGTH_SHORT).show()
                    }
                }, 2000)
            }
        })
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(
            notebookList,
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
        val popup = CustomOptionsMenu(requireContext(), view)

        popup.setOnRenameClickListener {
            showRenameNotebookDialog(notebook)
        }
        popup.setOnMemberClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Join my notebook \"${notebook.name}\" on CashBook: https://cashbk.app/join/${notebook.id}")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share Notebook via"))
        }
        popup.setOnDeleteClickListener {
            showDeletePopup(view, notebook)
        }

        popup.show()
    }

    override fun onDestroyView() {
        notebooksListener?.let { notebooksQuery?.removeEventListener(it) }
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




}