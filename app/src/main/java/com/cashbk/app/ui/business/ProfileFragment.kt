package com.cashbk.app.ui.business

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentProfileBinding
import com.cashbk.app.dataclass.User
import com.cashbk.app.ui.auth.AuthActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import androidx.core.content.ContextCompat
import com.cashbk.app.utils.GoogleDriveManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    
    private var currentBusinessId: String? = null
    private var currentUser: User? = null
    private var pendingImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            pendingImageUri = it
            startDriveAuthFlow()
        }
    }

    private val driveSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                if (pendingImageUri != null) {
                    uploadToGoogleDrive(account)
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Drive Authorization Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        setupClickListeners()
        fetchUserProfile()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnEditProfile.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.ivProfile.setOnClickListener {
            binding.btnEditProfile.performClick()
        }

        binding.icProfileEdit.setOnClickListener {
            binding.btnEditProfile.performClick()
        }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.btnResetPassword.setOnClickListener {
            val email = currentUser?.email
            if (!email.isNullOrEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Reset email sent to $email", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to send reset email: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(requireContext(), "Email address not found.", Toast.LENGTH_SHORT).show()
            }
        }

        // Appearance Selection Logic
        binding.btnAppearanceDark.setOnClickListener { updateAppearanceUI("dark") }
        binding.btnAppearanceLight.setOnClickListener { updateAppearanceUI("light") }
        binding.btnAppearanceSystem.setOnClickListener { updateAppearanceUI("system") }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "Enabled" else "Disabled"
            Toast.makeText(requireContext(), "Notifications $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAppearanceUI(theme: String) {
        // Set selected
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val gray = ContextCompat.getColor(requireContext(), R.color.gray)

        when (theme) {
            "dark" -> {
                binding.btnAppearanceDark.setBackgroundResource(R.drawable.bg_segmented_selected)
                binding.btnAppearanceDark.setTextColor(white)
            }
            "light" -> {
                binding.btnAppearanceLight.setBackgroundResource(R.drawable.bg_segmented_selected)
                binding.btnAppearanceLight.setTextColor(white)
            }
            "system" -> {
                binding.btnAppearanceSystem.setBackgroundResource(R.drawable.bg_segmented_selected)
                binding.btnAppearanceSystem.setTextColor(white)
            }
        }
        
        if (theme != "dark") binding.btnAppearanceDark.setTextColor(gray)
        if (theme != "light") binding.btnAppearanceLight.setTextColor(gray)
        if (theme != "system") binding.btnAppearanceSystem.setTextColor(gray)

        Toast.makeText(requireContext(), "Theme set to: $theme", Toast.LENGTH_SHORT).show()
    }

    private fun startDriveAuthFlow() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
        driveSignInLauncher.launch(client.signInIntent)
    }

    private fun fetchUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        
        database.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                
                currentUser = snapshot.getValue(User::class.java)
                currentUser?.let { user ->
                    binding.tvProfileName.text = user.name ?: "Unknown User"
                    binding.tvFullName.text = user.name ?: "N/A"
                    binding.tvEmail.text = user.email ?: "N/A"
                    binding.tvMobile.text = user.phone ?: "N/A"
//                    binding.tvBadgeTier.text = user.membershipTier ?: "GOLD MEMBER"

                    if (!user.profileImageUrl.isNullOrEmpty()) {
                        val displayUrl = if (user.profileImageUrl.contains("drive.google.com")) {
                            GoogleDriveManager.getThumbnailUrl(user.profileImageUrl)
                        } else {
                            user.profileImageUrl
                        }

                        Glide.with(this@ProfileFragment)
                            .load(displayUrl)
                            .placeholder(R.drawable.ic_default_avtar)
                            .override(600, 600)
                            .into(binding.ivProfile)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun uploadToGoogleDrive(googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val uid = auth.currentUser?.uid ?: return
        val currentImageUrl = currentUser?.profileImageUrl
        
        Toast.makeText(requireContext(), "Uploading Profile Image...", Toast.LENGTH_SHORT).show()
        
        val driveManager = GoogleDriveManager(requireContext(), googleAccount)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Delete previous file if exists on Drive
                if (!currentImageUrl.isNullOrEmpty() && currentImageUrl.contains("drive.google.com")) {
                    driveManager.deleteFile(currentImageUrl)
                }

                // 2. Upload new file
                val fileName = "Profile_$uid.jpg"
                val webViewLink = driveManager.uploadFile(
                    fileUri = pendingImageUri!!,
                    fileName = fileName,
                    folderName = "CashBook_Profile"
                )

                if (webViewLink != null) {
                    withContext(Dispatchers.Main) {
                        updateProfileImageUrl(webViewLink)
                        pendingImageUri = null
                    }
                } else {
                    throw Exception("Failed to get upload link")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Upload Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateProfileImageUrl(url: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("users").child(uid).child("profileImageUrl").setValue(url)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
