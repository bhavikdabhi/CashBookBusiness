package com.cashbk.app.ui.notebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentAddPartyBinding
import com.cashbk.app.dataclass.Party
import com.google.firebase.database.FirebaseDatabase

class AddPartyFragment : Fragment() {

    private var _binding: FragmentAddPartyBinding? = null
    private val binding get() = _binding!!

    private var notebookId: String? = null
    private var selectedRole: String = "CUSTOMER"   // default — matches reference

    // Convenience holder for each role tile's views
    private data class RoleTile(
        val container: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val roleKey: String
    )

    private val tiles by lazy {
        listOf(
            RoleTile(binding.roleVendor,     binding.ivVendorIcon,     binding.tvVendor,     "VENDOR"),
            RoleTile(binding.roleCustomer,   binding.ivCustomerIcon,   binding.tvCustomer,   "CUSTOMER"),
            RoleTile(binding.roleContractor, binding.ivContractorIcon, binding.tvContractor, "CONTRACTOR"),
            RoleTile(binding.roleEmployee,   binding.ivEmployeeIcon,   binding.tvEmployee,   "EMPLOYEE")
        )
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPartyBinding.inflate(inflater, container, false)
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

        applyRoleHighlight(selectedRole)   // paint default selection
        setupClickListeners()
    }

    // ─── Listeners ─────────────────────────────────────────────────────────────

    private fun setupClickListeners() {

        // Navigation
        binding.btnBack.setOnClickListener   { parentFragmentManager.popBackStack() }
        binding.btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }

        // Contacts chip
        binding.chipSelectContact.setOnClickListener {
            Toast.makeText(requireContext(), "Contact picker coming soon", Toast.LENGTH_SHORT).show()
        }

        // Role tiles
        tiles.forEach { tile ->
            tile.container.setOnClickListener {
                selectedRole = tile.roleKey
                applyRoleHighlight(selectedRole)
            }
        }

        // Save
        binding.btnSave.setOnClickListener { saveParty() }
    }

    // ─── Role highlighting ──────────────────────────────────────────────────────

    private fun applyRoleHighlight(activeRole: String) {
        tiles.forEach { tile ->
            val selected = tile.roleKey == activeRole

            tile.container.background = ContextCompat.getDrawable(
                requireContext(),
                if (selected) R.drawable.bg_role_button_selected
                else          R.drawable.bg_role_button_default
            )

            val iconColor = ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.auth_bg              // dark colour on glowing bg
                else          R.color.stitch_on_surface_variant
            )
            tile.icon.setColorFilter(iconColor)

            val textColor = ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.auth_bg
                else          R.color.stitch_on_surface_variant
            )
            tile.label.setTextColor(textColor)
        }
    }

    // ─── Save ──────────────────────────────────────────────────────────────────

    private fun saveParty() {
        val name    = binding.etPartyName.text.toString().trim()
        val contact = binding.etContact.text.toString().trim()

        if (name.isEmpty()) {
            binding.etPartyName.error = "Full name is required"
            return
        }
        binding.etPartyName.error = null

        binding.btnSave.isEnabled = false

        val db = FirebaseDatabase.getInstance().reference
            .child("parties")
            .child(notebookId!!)
        val partyId = db.push().key ?: ""

        val party = Party(
            id          = partyId,
            name        = name,
            role        = selectedRole,
            contact     = contact,
            email       = "",
            priorityTag = "GENERAL"
        )

        db.child(partyId).setValue(party)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Partner added successfully", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                binding.btnSave.isEnabled = true
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
