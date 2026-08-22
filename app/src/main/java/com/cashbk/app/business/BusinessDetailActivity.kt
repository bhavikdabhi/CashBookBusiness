package com.cashbk.app.business

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.content.res.Configuration
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cashbk.app.R
import com.cashbk.app.business._bean.Business
import com.cashbk.app.databinding.ActivityBusinessDetailBinding
import com.cashbk.app.events.EventsFragment
import com.cashbk.app.profile.ProfileFragment
import com.cashbk.app.business.SettingsFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.cashbk.app.utils.setupStatusAndNavBar

class BusinessDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessDetailBinding
    private var currentBusinessId: String? = null
    
    // Fragments
    private val cashbooksFragment = CashbooksFragment()
    private val profileFragment = ProfileFragment()
    private val eventsFragment = EventsFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBusinessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentBusinessId = intent.getStringExtra("businessId")

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchBusinessDetails()
            updateFragmentsBusinessId()
        } else {
            fetchFirstBusiness()
        }

        setupClickListeners()
        setupBottomNavigation()
        
        // Sync theme toggle button states
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDark) {
            binding.ivThemeIcon.setImageResource(R.drawable.ic_sun)
            binding.tvThemeText.text = "Light"
        } else {
            binding.ivThemeIcon.setImageResource(R.drawable.ic_moon)
            binding.tvThemeText.text = "Dark"
        }
        
        setupStatusAndNavBar()

        // Initial Fragment
        loadFragment(cashbooksFragment)
    }

    private fun setupClickListeners() {
        binding.layoutBusinessSelector.setOnClickListener {
            val bottomSheet = BusinessSelectionBottomSheet(currentBusinessId) { selectedBusiness ->
                if (selectedBusiness.id != currentBusinessId) {
                    currentBusinessId = selectedBusiness.id
                    fetchBusinessDetails()
                    updateFragmentsBusinessId()
                }
            }
            bottomSheet.show(supportFragmentManager, "BusinessSelectionBottomSheet")
        }

        binding.btnThemeToggle.setOnClickListener {
            val currentDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (currentDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
        
//        binding.btnAddPartner.setOnClickListener {
//             if (!currentBusinessId.isNullOrEmpty()) {
//                 val intent = Intent(this, ManagePartnersActivity::class.java)
//                 intent.putExtra("businessId", currentBusinessId)
//                 startActivity(intent)
//             }
//        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_cashbooks -> {
                    loadFragment(cashbooksFragment)
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(profileFragment)
                    true
                }
                R.id.nav_events -> {
                    loadFragment(eventsFragment)
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigation.selectedItemId = R.id.nav_cashbooks
    }

    private fun loadFragment(fragment: Fragment) {
        if (fragment is ProfileFragment || fragment is EventsFragment) {
            binding.topBar.visibility = View.GONE
        } else {
            binding.topBar.visibility = View.VISIBLE
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun updateFragmentsBusinessId() {
        cashbooksFragment.updateBusinessId(currentBusinessId)
        profileFragment.updateBusinessId(currentBusinessId)
        eventsFragment.updateBusinessId(currentBusinessId)
        settingsFragment.updateBusinessId(currentBusinessId)
    }

    private fun fetchFirstBusiness() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val businessRef = FirebaseDatabase.getInstance().reference.child("businesses")
        
        businessRef.orderByChild("ownerId").equalTo(userId).limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.childrenCount > 0) {
                        val firstChild = snapshot.children.first()
                        val business = firstChild.getValue(Business::class.java)
                        business?.id = firstChild.key.orEmpty()
                        
                        if (business != null) {
                            currentBusinessId = business.id
                            fetchBusinessDetails()
                            updateFragmentsBusinessId()
                        }
                    } else {
                        binding.tvBusinessName.text = "No Business Found"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchBusinessDetails() {
        val id = currentBusinessId ?: return
        FirebaseDatabase.getInstance().reference.child("businesses").child(id).get()
            .addOnSuccessListener { snapshot ->
                val name = snapshot.child("name").value as? String ?: "Unknown"
                binding.tvBusinessName.text = name
            }
    }

    override fun onResume() {
        super.onResume()
        setupStatusAndNavBar()
    }
}
