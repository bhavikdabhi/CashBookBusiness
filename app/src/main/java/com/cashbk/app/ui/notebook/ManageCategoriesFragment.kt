package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
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

    private val originalCategoriesList = mutableListOf<Category>()

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

        binding.btnToolbarAdd.setOnClickListener { navigateToAddCategory() }
        binding.btnCreateCategory.setOnClickListener { navigateToAddCategory() }
        binding.btnAddPlaceholder.setOnClickListener { navigateToAddCategory() }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCategories(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun navigateToAddCategory() {
        val fragment = AddCategoryFragment()
        val args = Bundle()
        args.putString("notebookId", notebookId)
        fragment.arguments = args

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
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
                originalCategoriesList.clear()
                for (child in snapshot.children) {
                    val category = child.getValue(Category::class.java)
                    category?.id = child.key ?: ""
                    category?.let { 
                        categoriesList.add(it)
                        originalCategoriesList.add(it)
                    }
                }

                updateEmptyState()
                categoryAdapter.updateData(categoriesList)
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun filterCategories(query: String) {
        val filtered = if (query.isEmpty()) {
            originalCategoriesList
        } else {
            originalCategoriesList.filter { 
                it.name.contains(query, ignoreCase = true)
            }
        }
        categoriesList.clear()
        categoriesList.addAll(filtered)
        categoryAdapter.updateData(categoriesList)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (categoriesList.isEmpty()) {
            binding.btnAddPlaceholder.visibility = View.VISIBLE
            binding.rvCategories.visibility = View.GONE
        } else {
            binding.btnAddPlaceholder.visibility = View.VISIBLE // Always show as per design, or adjust if needed
            binding.rvCategories.visibility = View.VISIBLE
        }
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
