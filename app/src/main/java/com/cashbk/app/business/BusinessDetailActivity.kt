package com.cashbk.app.business

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cashbk.app.R
import com.cashbk.app.business._bean.Business
import com.cashbk.app.databinding.ActivityBusinessDetailBinding
import com.cashbk.app.events.EventsFragment
import com.cashbk.app.profile.ProfileFragment
import com.cashbk.app.business.SettingsFragment
import com.cashbk.app.utils.GoogleDriveManager
import com.cashbk.app.utils.ThemeAnimationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.cashbk.app.utils.setupStatusAndNavBar

class BusinessDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessDetailBinding
    private var currentBusinessId: String? = null
    private var userProfileListener: ValueEventListener? = null
    private var userRef: DatabaseReference? = null
    
    // Fragments
    private val cashbooksFragment = CashbooksFragment()
    private val profileFragment = ProfileFragment()
    private val eventsFragment = EventsFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme locally before super.onCreate to initialize directly without recreate() blink
        val sharedPrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("app_theme", "system") ?: "system"
        val nightMode = when (savedTheme) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        delegate.localNightMode = nightMode

        super.onCreate(savedInstanceState)

        binding = ActivityBusinessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply smooth circular reveal theme animation if triggered
        val isThemeTransition = ThemeAnimationHelper.hasPendingAnimation
        ThemeAnimationHelper.applyPendingThemeAnimation(this, binding.root)

        currentBusinessId = intent.getStringExtra("businessId")

        if (!currentBusinessId.isNullOrEmpty()) {
            fetchBusinessDetails()
            updateFragmentsBusinessId()
        } else {
            fetchFirstBusiness()
        }

        val tabFromIntent = intent.getIntExtra("selected_nav_tab", 0)
        val initialTab = if (tabFromIntent != 0) tabFromIntent else savedInstanceState?.getInt("selected_nav_tab", R.id.nav_cashbooks) ?: R.id.nav_cashbooks
        setupClickListeners()
        setupBottomNavigation(initialTab)
        loadUserProfile()
        
        // Sync theme toggle button states
        updateThemeToggleIcon()
        setupStatusAndNavBar()

        if (isThemeTransition) {
            animateThemeIconEntry()
        }
    }

    private fun updateThemeToggleIcon() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDark) {
            binding.ivThemeIcon.setImageResource(R.drawable.ic_theme_sun)
            binding.tvThemeText.text = "Light"
        } else {
            binding.ivThemeIcon.setImageResource(R.drawable.ic_theme_moon)
            binding.tvThemeText.text = "Dark"
        }
    }

    private fun animateThemeIconEntry() {
        binding.ivThemeIcon.rotation = -180f
        binding.ivThemeIcon.scaleX = 0.5f
        binding.ivThemeIcon.scaleY = 0.5f
        binding.ivThemeIcon.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(750)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.tvThemeText.alpha = 0f
        binding.tvThemeText.animate()
            .alpha(1f)
            .setDuration(600)
            .start()
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
            val newNightMode = if (currentDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            val themePref = if (currentDark) "light" else "dark"

            ThemeAnimationHelper.switchThemeWithAnimation(
                activity = this,
                sourceView = binding.btnThemeToggle,
                newNightMode = newNightMode,
                themePrefValue = themePref,
                currentBusinessId = currentBusinessId,
                selectedTabId = binding.bottomNavigation.selectedItemId
            )
        }

        binding.btnProfile.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_profile
        }
        
//        binding.btnAddPartner.setOnClickListener {
//             if (!currentBusinessId.isNullOrEmpty()) {
//                 val intent = Intent(this, ManagePartnersActivity::class.java)
//                 intent.putExtra("businessId", currentBusinessId)
//                 startActivity(intent)
//             }
//        }
    }

    private fun setupBottomNavigation(selectedTabId: Int = R.id.nav_cashbooks) {
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
        binding.bottomNavigation.selectedItemId = selectedTabId
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

    private fun loadUserProfile() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid

        // Initial fallback from Google / Firebase Auth
        user.photoUrl?.let { photoUri ->
            Glide.with(this)
                .load(photoUri)
                .placeholder(R.drawable.ic_default_avtar)
                .error(R.drawable.ic_default_avtar)
                .into(binding.btnProfile)
        }

        // Live sync from Realtime Database users node
        userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)
        userProfileListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDestroyed || isFinishing) return
                val profileUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                if (!profileUrl.isNullOrEmpty()) {
                    val displayUrl = if (profileUrl.contains("drive.google.com")) {
                        GoogleDriveManager.getThumbnailUrl(profileUrl)
                    } else {
                        profileUrl
                    }
                    Glide.with(this@BusinessDetailActivity)
                        .load(displayUrl)
                        .placeholder(R.drawable.ic_default_avtar)
                        .error(R.drawable.ic_default_avtar)
                        .into(binding.btnProfile)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        userProfileListener?.let { userRef?.addValueEventListener(it) }
    }

    override fun onResume() {
        super.onResume()
        setupStatusAndNavBar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_nav_tab", binding.bottomNavigation.selectedItemId)
    }

    fun getCurrentBusinessId(): String? = currentBusinessId

    override fun onDestroy() {
        super.onDestroy()
        userProfileListener?.let { userRef?.removeEventListener(it) }
        if (isFinishing) {
            ThemeAnimationHelper.clear()
        }
    }
}
