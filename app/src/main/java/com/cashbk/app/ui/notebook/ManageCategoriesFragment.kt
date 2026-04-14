package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.adapter.CategoryAdapter
import com.cashbk.app.databinding.DialogAddEntityBinding
import com.cashbk.app.databinding.FragmentManageCategoriesBinding
import com.cashbk.app.dataclass.Category
import com.google.firebase.database.*

class ManageCategoriesFragment : Fragment() {

    private var _binding: FragmentManageCategoriesBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: DatabaseReference
    private lateinit var categoryAdapter: CategoryAdapter
    private val categoriesList = mutableListOf<Category>()
    
    private var notebookId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook ID missing", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("categories").child(notebookId!!)

        setupRecyclerView()
        fetchCategories()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabAdd.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(categoriesList) { category ->
            showDeleteConfirmation(category)
        }
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }
    }

    private fun fetchCategories() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoriesList.clear()
                for (child in snapshot.children) {
                    val category = child.getValue(Category::class.java)
                    category?.id = child.key ?: ""
                    category?.let { categoriesList.add(it) }
                }

                if (categoriesList.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvCategories.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvCategories.visibility = View.VISIBLE
                }
                categoryAdapter.updateData(categoriesList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddCategoryDialog() {
        val dialogBinding = DialogAddEntityBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvTitle.text = "Add New Category"
        
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etName.text.toString().trim()
            if (name.isNotEmpty()) {
                val categoryId = database.push().key ?: ""
                val category = Category(id = categoryId, name = name)
                database.child(categoryId).setValue(category)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Category Added", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
            } else {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showDeleteConfirmation(category: Category) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete ${category.name}?")
            .setPositiveButton("Delete") { _, _ ->
                database.child(category.id).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
