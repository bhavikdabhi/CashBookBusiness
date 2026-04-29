package com.cashbk.app.ui.notebook

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentAddCategoryBinding
import com.cashbk.app.dataclass.Category
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.firebase.database.FirebaseDatabase

class AddCategoryFragment : Fragment() {

    private var _binding: FragmentAddCategoryBinding? = null
    private val binding get() = _binding!!
    private var notebookId: String? = null

    private var selectedType = "expense"
    private var selectedColorHex = "#EF5350" // Default red
    private var selectedIconName = "ic_cat_money"

    private val colorOptions = listOf(
        "#B5BDFF", "#80DEEA", "#1DE9B6", "#FFA726", "#EF5350",
        "#EC407A", "#AB47BC", "#5C6BC0", "#90A4AE", "#FFFFFF"
    )

    private val iconOptions = listOf(
        "ic_cat_money", "ic_cat_shopping", "ic_cat_dining", "ic_cat_transport",
        "ic_cat_home", "ic_cat_travel", "ic_book", "ic_payment"
    )

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

        setupTypeSelection()
        setupColorGrid()
        setupIconGrid()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSave.setOnClickListener {
            saveCategory()
        }
    }

    private fun setupTypeSelection() {
        // Initial state
        updateTypeUI()

        binding.btnTypeIncome.setOnClickListener {
            selectedType = "income"
            updateTypeUI()
        }

        binding.btnTypeExpense.setOnClickListener {
            selectedType = "expense"
            updateTypeUI()
        }
    }

    private fun updateTypeUI() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_disabled)
        val glassBorder = ContextCompat.getColor(requireContext(), R.color.glass_border)
        val dangerColor = ContextCompat.getColor(requireContext(), R.color.danger)
        val successColor = Color.parseColor("#00E676")

        if (selectedType == "income") {
            binding.btnTypeIncome.strokeColor = ColorStateList.valueOf(successColor)
            binding.btnTypeIncome.strokeWidth = 4
            binding.btnTypeIncome.setTextColor(successColor)
            
            binding.btnTypeExpense.strokeColor = ColorStateList.valueOf(glassBorder)
            binding.btnTypeExpense.strokeWidth = 2
            binding.btnTypeExpense.setTextColor(inactiveColor)
        } else {
            binding.btnTypeIncome.strokeColor = ColorStateList.valueOf(glassBorder)
            binding.btnTypeIncome.strokeWidth = 2
            binding.btnTypeIncome.setTextColor(inactiveColor)

            binding.btnTypeExpense.strokeColor = ColorStateList.valueOf(dangerColor)
            binding.btnTypeExpense.strokeWidth = 4
            binding.btnTypeExpense.setTextColor(dangerColor)
        }
    }

    private fun setupColorGrid() {
        val context = requireContext()
        val size = (context.resources.displayMetrics.density * 40).toInt()
        val margin = (context.resources.displayMetrics.density * 8).toInt()

        colorOptions.forEach { hex ->
            val frame = FrameLayout(context)
            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(margin, margin, margin, margin)
            frame.layoutParams = params

            // Selection ring
            val ring = View(context)
            val ringDrawable = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(size / 2f).build())
            ringDrawable.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
            ringDrawable.strokeWidth = 4f
            ringDrawable.strokeColor = ColorStateList.valueOf(Color.parseColor(hex))
            ring.background = ringDrawable
            ring.visibility = if (selectedColorHex == hex) View.VISIBLE else View.GONE
            ring.tag = "ring_$hex"
            frame.addView(ring)

            // Color circle
            val circle = View(context)
            val circleSize = (size * 0.7).toInt()
            val circleParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
            circle.layoutParams = circleParams
            val circleDrawable = MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(circleSize / 2f).build())
            circleDrawable.fillColor = ColorStateList.valueOf(Color.parseColor(hex))
            circle.background = circleDrawable
            frame.addView(circle)

            frame.setOnClickListener {
                selectedColorHex = hex
                refreshColorGridSelection()
            }
            binding.colorGrid.addView(frame)
        }
    }

    private fun refreshColorGridSelection() {
        for (i in 0 until binding.colorGrid.childCount) {
            val frame = binding.colorGrid.getChildAt(i) as FrameLayout
            val ring = frame.getChildAt(0)
            val hex = colorOptions[i]
            ring.visibility = if (selectedColorHex == hex) View.VISIBLE else View.GONE
        }
    }

    private fun setupIconGrid() {
        val context = requireContext()
        val size = (context.resources.displayMetrics.density * 56).toInt()
        val margin = (context.resources.displayMetrics.density * 8).toInt()

        iconOptions.forEach { iconName ->
            val frame = FrameLayout(context)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            params.width = 0
            params.height = size
            params.setMargins(margin, margin, margin, margin)
            frame.layoutParams = params
            frame.background = ContextCompat.getDrawable(context, R.drawable.bg_glass_field)
            frame.backgroundTintList = ColorStateList.valueOf(
                if (selectedIconName == iconName) Color.parseColor("#40FFFFFF") else Color.parseColor("#10FFFFFF")
            )

            val icon = ImageView(context)
            val iconSize = (context.resources.displayMetrics.density * 24).toInt()
            val iconParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            icon.layoutParams = iconParams
            
            val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
            if (resId != 0) {
                icon.setImageResource(resId)
            }
            icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            frame.addView(icon)

            frame.setOnClickListener {
                selectedIconName = iconName
                refreshIconGridSelection()
            }
            binding.iconGrid.addView(frame)
        }
    }

    private fun refreshIconGridSelection() {
        for (i in 0 until binding.iconGrid.childCount) {
            val frame = binding.iconGrid.getChildAt(i) as FrameLayout
            val iconName = iconOptions[i]
            frame.backgroundTintList = ColorStateList.valueOf(
                if (selectedIconName == iconName) Color.parseColor("#40FFFFFF") else Color.parseColor("#10FFFFFF")
            )
            // Add a border if selected
            if (frame.background is MaterialShapeDrawable) {
                (frame.background as MaterialShapeDrawable).strokeWidth = if (selectedIconName == iconName) 2f else 0f
                (frame.background as MaterialShapeDrawable).strokeColor = ColorStateList.valueOf(Color.WHITE)
            }
        }
    }

    private fun saveCategory() {
        val name = binding.etCategoryName.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        val database = FirebaseDatabase.getInstance().reference.child("categories").child(notebookId!!)
        val categoryId = database.push().key ?: ""
        
        val category = Category(
            id = categoryId, 
            name = name, 
            description = if (selectedType == "income") "Revenue stream" else "Expenditure", 
            utilization = (0..100).random(),
            type = selectedType,
            colorHex = selectedColorHex,
            iconResName = selectedIconName
        )

        database.child(categoryId).setValue(category)
            .addOnSuccessListener {

                val context = context ?: return@addOnSuccessListener

               // if (!isAdded) return@addOnSuccessListener

                Toast.makeText(context, "Category ready!", Toast.LENGTH_SHORT).show()

                if (isAdded && !parentFragmentManager.isStateSaved) {
                    parentFragmentManager.popBackStack()
                }
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
