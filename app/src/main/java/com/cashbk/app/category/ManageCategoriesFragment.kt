package com.cashbk.app.category

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cashbk.app.utils.CustomAlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.R
import com.cashbk.app.category.adapter.CategoryAdapter
import com.cashbk.app.databinding.FragmentManageCategoriesBinding
import com.cashbk.app.category._bean.Category
import com.google.firebase.database.*
import com.cashbk.app.utils.startPulseAnimation
import com.cashbk.app.utils.stopPulseAnimation

class ManageCategoriesFragment : Fragment() {

    private var _binding: FragmentManageCategoriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var categoryAdapter: CategoryAdapter

    private val categoriesList = mutableListOf<Category>()
    private val originalCategoriesList = mutableListOf<Category>()

    private var notebookId: String? = null

    // ✅ FIX: Keep reference to listener
    private var valueEventListener: ValueEventListener? = null

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

        database = FirebaseDatabase.getInstance()
            .reference.child("categories")
            .child(notebookId!!)

        setupRecyclerView()
        fetchCategories()
        setupClickListeners()
        setupSearch()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCreateCategory.setOnClickListener { navigateToAddCategory() }
        binding.btnAddPlaceholder.setOnClickListener { navigateToAddCategory() }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCategories(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun navigateToAddCategory() {
        val fragment = AddCategoryFragment().apply {
            arguments = Bundle().apply {
                putString("notebookId", notebookId)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.notebook_fragment_container, fragment)
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
        if (_binding != null) {
            binding.layoutShimmerCategories.visibility = View.VISIBLE
            binding.layoutShimmerCategories.startPulseAnimation()
            binding.rvCategories.visibility = View.GONE
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                categoriesList.clear()
                originalCategoriesList.clear()

                for (child in snapshot.children) {
                    val category = child.getValue(Category::class.java)
                    category?.let {
                        it.id = child.key ?: ""   // ⚠ Ensure id is VAR in data class
                        categoriesList.add(it)
                        originalCategoriesList.add(it)
                    }
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerCategories.stopPulseAnimation()
                    binding.layoutShimmerCategories.visibility = View.GONE
                    binding.rvCategories.visibility = View.VISIBLE
                    
                    categoryAdapter.updateData(categoriesList)
                    updateEmptyState()
                }, 2000)
            }

            override fun onCancelled(error: DatabaseError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (_binding == null) return@postDelayed
                    binding.layoutShimmerCategories.stopPulseAnimation()
                    binding.layoutShimmerCategories.visibility = View.GONE
                    binding.rvCategories.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }, 2000)
            }
        }

        database.addValueEventListener(valueEventListener!!)
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

        if (_binding != null) {
            categoryAdapter.updateData(categoriesList)
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        if (_binding == null) return

        if (categoriesList.isEmpty()) {
            binding.rvCategories.visibility = View.GONE
            binding.btnAddPlaceholder.visibility = View.VISIBLE
        } else {
            binding.rvCategories.visibility = View.VISIBLE
            binding.btnAddPlaceholder.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmation(category: Category) {
        CustomAlertDialog(requireContext())
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete ${category.name}?")
            .setIcon(R.drawable.ic_action_delete, ContextCompat.getColor(requireContext(), R.color.danger))
            .setPositiveButton("Delete") {
                database.child(category.id).removeValue()
                    .addOnSuccessListener {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // ✅ IMPORTANT: remove Firebase listener
        valueEventListener?.let {
            database.removeEventListener(it)
        }

        _binding = null
    }
}