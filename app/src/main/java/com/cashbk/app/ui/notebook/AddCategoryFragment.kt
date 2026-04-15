package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cashbk.app.databinding.FragmentAddCategoryBinding
import com.cashbk.app.dataclass.Category
import com.google.firebase.database.FirebaseDatabase

class AddCategoryFragment : Fragment() {

    private var _binding: FragmentAddCategoryBinding? = null
    private val binding get() = _binding!!
    private var notebookId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        notebookId = arguments?.getString("notebookId")
        if (notebookId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Notebook configuration error", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSave.setOnClickListener {
            saveCategory()
        }
    }

    private fun saveCategory() {
        val name = binding.etCategoryName.text.toString().trim()
        val description = binding.etCategoryDescription.text.toString().trim()
        
        if (name.isEmpty()) {
            binding.tilCategoryName.error = "Please enter a category name"
            return
        }

        binding.btnSave.isEnabled = false
        val database = FirebaseDatabase.getInstance().reference.child("categories").child(notebookId!!)
        val categoryId = database.push().key ?: ""
        
        // Initial utilization mocked for demonstration
        val mockUtilization = (10..100).random()
        val category = Category(
            id = categoryId, 
            name = name, 
            description = description, 
            utilization = mockUtilization
        )

        database.child(categoryId).setValue(category)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Category created successfully", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
