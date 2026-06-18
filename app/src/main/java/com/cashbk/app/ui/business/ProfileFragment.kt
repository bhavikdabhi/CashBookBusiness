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
import com.cashbk.app.fragment.EditProfileBottomSheet
import com.cashbk.app.R
import com.cashbk.app.databinding.FragmentProfileBinding
import com.cashbk.app.dataclass.User
import com.cashbk.app.ui.auth.AuthActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import androidx.core.content.ContextCompat
import com.cashbk.app.utils.GoogleDriveManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.net.URL
import java.net.HttpURLConnection

class ProfileFragment : Fragment() {

    enum class DriveFlowMode {
        NONE,
        CONNECT,
        PROFILE_UPLOAD,
        SWITCH_NO_MIGRATE,
        SWITCH_WITH_MIGRATE
    }

    private data class MigrationItem(
        val dbPath: String,
        val oldUrl: String,
        val fileName: String,
        val folderName: String
    )

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    
    private var currentBusinessId: String? = null
    private var currentUser: User? = null
    private var pendingImageUri: Uri? = null
    private var profileListener: ValueEventListener? = null
    private var registeredUid: String? = null

    private var driveFlowMode = DriveFlowMode.NONE
    private val pendingMigrationFiles = mutableListOf<Pair<MigrationItem, File>>()

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
                saveGoogleDriveEmail(account.email)
                when (driveFlowMode) {
                    DriveFlowMode.CONNECT -> {
                        Toast.makeText(requireContext(), "Google Drive connected successfully!", Toast.LENGTH_SHORT).show()
                    }
                    DriveFlowMode.PROFILE_UPLOAD -> {
                        if (pendingImageUri != null) {
                            uploadToGoogleDrive(account)
                        }
                    }
                    DriveFlowMode.SWITCH_NO_MIGRATE -> {
                        Toast.makeText(requireContext(), "Google Account switched successfully!", Toast.LENGTH_SHORT).show()
                    }
                    DriveFlowMode.SWITCH_WITH_MIGRATE -> {
                        uploadMigratedFiles(account)
                    }
                    else -> {}
                }
                driveFlowMode = DriveFlowMode.NONE
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Drive Authorization Failed", Toast.LENGTH_SHORT).show()
                cleanupTempMigrationFiles()
                driveFlowMode = DriveFlowMode.NONE
            }
        } else {
            cleanupTempMigrationFiles()
            driveFlowMode = DriveFlowMode.NONE
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
            val bottomSheet = EditProfileBottomSheet(
                currentUser?.name,
                currentUser?.phone
            ) { newName, newPhone ->
                performProfileUpdate(newName, newPhone)
            }
            bottomSheet.show(parentFragmentManager, "EditProfileBottomSheet")
        }

        binding.btnSignOut.setOnClickListener {
            profileListener?.let { listener ->
                registeredUid?.let { uid ->
                    database.child("users").child(uid).removeEventListener(listener)
                }
            }
            profileListener = null

            auth.signOut()
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(requireActivity(), signInOptions).signOut().addOnCompleteListener {
                if (isAdded) {
                    val intent = Intent(context, AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
        }

        binding.btnResetPassword.setOnClickListener {
            val email = currentUser?.email
            if (!email.isNullOrEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(context, "Reset email sent to $email", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener {
                        if (isAdded) {
                            Toast.makeText(context, "Failed to send reset email: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
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

        // Google Drive Card Click Listeners
        binding.btnConnectDrive.setOnClickListener {
            connectGoogleDrive()
        }

        binding.btnDisconnectDrive.setOnClickListener {
            disconnectGoogleDrive()
        }

        binding.btnSwitchDrive.setOnClickListener {
            switchGoogleDriveAccount()
        }

        // Biometric App Lock Switch
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        binding.switchBiometric.isChecked = prefs.getBoolean("app_lock_enabled", false)

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            val currentVal = prefs.getBoolean("app_lock_enabled", false)
            if (isChecked == currentVal) return@setOnCheckedChangeListener

            if (isChecked) {
                // Onboarding: Fallback PIN setup
                val handler = com.ext.biometric_auth.PinAuthHandler(
                    requireActivity(),
                    com.ext.biometric_auth.BiometricConfig(
                        title = "App Lock Setup",
                        subtitle = "Create a security PIN fallback for App Lock"
                    ),
                    null,
                    onActionCompleted = { mode ->
                        if (mode == com.ext.biometric_auth.PinDialogMode.SETUP) {
                            prefs.edit().putBoolean("app_lock_enabled", true).apply()
                            Toast.makeText(context, "App Lock enabled successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            binding.switchBiometric.isChecked = false
                        }
                    }
                )
                handler.showPinDialog(com.ext.biometric_auth.PinDialogMode.SETUP)
            } else {
                // Verification: Authenticate before disabling
                val callback = object : com.ext.biometric_auth.BiometricCallback {
                    override fun onResult(result: com.ext.biometric_auth.BiometricResult) {
                        if (result is com.ext.biometric_auth.BiometricResult.AuthenticationSucceeded ||
                            result is com.ext.biometric_auth.BiometricResult.PinAuthenticationSucceeded) {
                            
                            val removeHandler = com.ext.biometric_auth.PinAuthHandler(
                                requireActivity(),
                                com.ext.biometric_auth.BiometricConfig(),
                                null,
                                onActionCompleted = { mode ->
                                    if (mode == com.ext.biometric_auth.PinDialogMode.REMOVE) {
                                        prefs.edit().putBoolean("app_lock_enabled", false).apply()
                                        Toast.makeText(context, "App Lock disabled.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        binding.switchBiometric.isChecked = true
                                    }
                                }
                            )
                            removeHandler.showPinDialog(com.ext.biometric_auth.PinDialogMode.REMOVE)
                        } else {
                            binding.switchBiometric.isChecked = true
                        }
                    }
                }
                val authManager = com.ext.biometric_auth.BiometricAuthManager(requireContext())
                authManager.authenticate(requireActivity(), callback)
            }
        }
    }

    private fun updateAppearanceUI(theme: String) {
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val gray = ContextCompat.getColor(requireContext(), R.color.gray)

        // Reset backgrounds of all options
        binding.btnAppearanceDark.setBackgroundResource(0)
        binding.btnAppearanceLight.setBackgroundResource(0)
        binding.btnAppearanceSystem.setBackgroundResource(0)

        // Reset text colors of all options
        binding.btnAppearanceDark.setTextColor(gray)
        binding.btnAppearanceLight.setTextColor(gray)
        binding.btnAppearanceSystem.setTextColor(gray)

        // Set styles for selected option
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

        Toast.makeText(requireContext(), "Theme set to: $theme", Toast.LENGTH_SHORT).show()
    }

    private fun startDriveAuthFlow() {
        driveFlowMode = DriveFlowMode.PROFILE_UPLOAD
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
        
        currentUser?.googleDriveEmail?.let {
            if (it.isNotEmpty()) {
                builder.setAccountName(it)
            }
        }

        val signInOptions = builder.build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
        driveSignInLauncher.launch(client.signInIntent)
    }

    private fun saveGoogleDriveEmail(email: String?) {
        val uid = auth.currentUser?.uid ?: return
        if (email != null) {
            database.child("users").child(uid).child("googleDriveEmail").setValue(email)
        }
    }

    private fun fetchUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        registeredUid = uid
        profileListener = database.child("users").child(uid).addValueEventListener(object : ValueEventListener {
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

                    // Update Google Drive Card UI
                    val driveEmail = user.googleDriveEmail
                    if (!driveEmail.isNullOrEmpty()) {
                        binding.tvDriveStatus.text = "Connected: $driveEmail"
                        binding.tvDriveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                        binding.btnConnectDrive.visibility = View.GONE
                        binding.layoutDriveActions.visibility = View.VISIBLE
                    } else {
                        binding.tvDriveStatus.text = "Not Connected"
                        binding.tvDriveStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                        binding.btnConnectDrive.visibility = View.VISIBLE
                        binding.layoutDriveActions.visibility = View.GONE
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && auth.currentUser != null) {
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
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
                    folderName = "Profile"
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
                    if (isAdded) {
                        Toast.makeText(context, "Upload Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateProfileImageUrl(url: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("users").child(uid).child("profileImageUrl").setValue(url)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun performProfileUpdate(newName: String, newPhone: String) {
        val uid = auth.currentUser?.uid ?: return
        val currentPhone = currentUser?.phone

        // 1. Check if the new phone is already registered to a DIFFERENT user
        database.child("users").orderByChild("phone").equalTo(newPhone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var otherUid: String? = null
                    for (child in snapshot.children) {
                        if (child.key != uid) {
                            otherUid = child.key
                            break
                        }
                    }

                    if (otherUid != null) {
                        // Conflict! Perform Reverse Merge (Move Other UID's data to Current UID)
                        migrateData(otherUid, uid, newName, newPhone)
                    } else {
                        // No conflict, safe update
                        val updates = mapOf(
                            "name" to newName,
                            "phone" to newPhone
                        )
                        database.child("users").child(uid).updateChildren(updates)
                            .addOnSuccessListener {
                                if (isAdded) {
                                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isAdded) {
                        Toast.makeText(context, "Update failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun migrateData(fromUid: String, toUid: String, newName: String, newPhone: String) {
        if (isAdded) {
            Toast.makeText(context, "Merging data from existing record...", Toast.LENGTH_LONG).show()
        }

        // 1. Update existing businesses ownership
        database.child("businesses").orderByChild("ownerId").equalTo(fromUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val batchUpdates = mutableMapOf<String, Any?>()
                    for (child in snapshot.children) {
                        batchUpdates["businesses/${child.key}/ownerId"] = toUid
                    }

                    // 2. Clear old phone record and Update current user in one go
                    batchUpdates["users/$fromUid/phone"] = ""
                    batchUpdates["users/$toUid/name"] = newName
                    batchUpdates["users/$toUid/phone"] = newPhone

                    database.updateChildren(batchUpdates).addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(context, "Account merged and updated successfully", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener {
                        if (isAdded) {
                            Toast.makeText(context, "Merge failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun updateBusinessId(businessId: String?) {
        currentBusinessId = businessId
    }

    private fun connectGoogleDrive() {
        driveFlowMode = DriveFlowMode.CONNECT
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), signInOptions)
        driveSignInLauncher.launch(client.signInIntent)
    }

    private fun disconnectGoogleDrive() {
        MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
            .setTitle("Disconnect Google Drive")
            .setMessage("Are you sure you want to disconnect Google Drive? Receipts will no longer be backed up to your Google account.")
            .setPositiveButton("Disconnect") { _: android.content.DialogInterface, _: Int ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                database.child("users").child(uid).child("googleDriveEmail").setValue("")
                    .addOnSuccessListener {
                        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                        GoogleSignIn.getClient(requireActivity(), signInOptions).signOut().addOnCompleteListener {
                            if (isAdded) {
                                Toast.makeText(context, "Google Drive disconnected.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchGoogleDriveAccount() {
        MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
            .setTitle("Switch Google Drive Account")
            .setMessage("Do you want to transfer your existing profile picture and receipts to the new account?")
            .setPositiveButton("Migrate & Switch") { _: android.content.DialogInterface, _: Int ->
                startMigrationAndSwitchFlow()
            }
            .setNegativeButton("Switch Only") { _: android.content.DialogInterface, _: Int ->
                driveFlowMode = DriveFlowMode.SWITCH_NO_MIGRATE
                performGoogleSignOutAndChooser()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun startMigrationAndSwitchFlow() {
        val progressDialog = MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
            .setTitle("Data Migration")
            .setMessage("Preparing data...")
            .setCancelable(false)
            .create()
            
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            val success = scanAndDownloadMigrationData(progressDialog)
            progressDialog.dismiss()
            if (success) {
                driveFlowMode = DriveFlowMode.SWITCH_WITH_MIGRATE
                performGoogleSignOutAndChooser()
            } else {
                Toast.makeText(requireContext(), "Migration failed to prepare. Please check your connection and try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performGoogleSignOutAndChooser() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(requireActivity(), signInOptions).signOut().addOnCompleteListener {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            val newClient = GoogleSignIn.getClient(requireActivity(), builder.build())
            driveSignInLauncher.launch(newClient.signInIntent)
        }
    }

    private suspend fun downloadFile(urlStr: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun scanAndDownloadMigrationData(progressDialog: androidx.appcompat.app.AlertDialog): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        val db = database
        
        withContext(Dispatchers.Main) {
            progressDialog.setMessage("Scanning database for files...")
        }
        
        val itemsToMigrate = mutableListOf<MigrationItem>()
        
        // 1. Profile image
        val profileUrl = currentUser?.profileImageUrl
        if (!profileUrl.isNullOrEmpty() && profileUrl.contains("drive.google.com")) {
            itemsToMigrate.add(MigrationItem(
                dbPath = "users/$uid/profileImageUrl",
                oldUrl = profileUrl,
                fileName = "Profile_$uid.jpg",
                folderName = "Profile"
            ))
        }
        
        try {
            // 2. Fetch owned businesses
            val ownedSnapshot = db.child("businesses").orderByChild("ownerId").equalTo(uid).awaitValue()
            val businessIds = mutableSetOf<String>()
            for (child in ownedSnapshot.children) {
                businessIds.add(child.key ?: "")
            }
            
            // 3. Fetch shared businesses
            val membersSnapshot = db.child("business_members").awaitValue()
            for (businessSnap in membersSnapshot.children) {
                if (businessSnap.child(uid).exists()) {
                    businessIds.add(businessSnap.key ?: "")
                }
            }
            
            // 4. Fetch notebooks for all these businesses
            val notebookIds = mutableSetOf<String>()
            for (businessId in businessIds) {
                if (businessId.isEmpty()) continue
                val notebooksSnap = db.child("notebooks").orderByChild("businessId").equalTo(businessId).awaitValue()
                for (child in notebooksSnap.children) {
                    notebookIds.add(child.key ?: "")
                }
            }
            
            // 5. Fetch transactions for these notebooks
            for (notebookId in notebookIds) {
                if (notebookId.isEmpty()) continue
                val transactionsSnap = db.child("transactions").child(notebookId).awaitValue()
                for (child in transactionsSnap.children) {
                    val createdBy = child.child("createdBy").getValue(String::class.java)
                    val receiptUrl = child.child("receiptUrl").getValue(String::class.java)
                    val receiptName = child.child("receiptName").getValue(String::class.java) ?: "Receipt_${child.key}.jpg"
                    if (createdBy == uid && !receiptUrl.isNullOrEmpty() && receiptUrl.contains("drive.google.com")) {
                        itemsToMigrate.add(MigrationItem(
                            dbPath = "transactions/$notebookId/${child.key}/receiptUrl",
                            oldUrl = receiptUrl,
                            fileName = receiptName,
                            folderName = "Receipt"
                        ))
                    }
                }
            }
            
            if (itemsToMigrate.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No files found to migrate.", Toast.LENGTH_SHORT).show()
                }
                return@withContext true
            }
            
            pendingMigrationFiles.clear()
            val total = itemsToMigrate.size
            for ((index, item) in itemsToMigrate.withIndex()) {
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Downloading file ${index + 1} of $total...")
                }
                
                val fileId = GoogleDriveManager.extractFileId(item.oldUrl)
                if (fileId.isNullOrEmpty()) continue
                
                val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                val tempFile = File(requireContext().cacheDir, "migration_${System.currentTimeMillis()}_$fileId.jpg")
                
                val success = downloadFile(downloadUrl, tempFile)
                if (success) {
                    pendingMigrationFiles.add(Pair(item, tempFile))
                } else {
                    tempFile.delete()
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun uploadMigratedFiles(googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        if (pendingMigrationFiles.isEmpty()) {
            Toast.makeText(requireContext(), "No files to upload.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progressDialog = MaterialAlertDialogBuilder(requireContext(), R.style.CashbkAlertDialog)
            .setTitle("Migrating Files")
            .setMessage("Uploading files to new account...")
            .setCancelable(false)
            .create()
            
        progressDialog.show()
        
        val driveManager = GoogleDriveManager(requireContext(), googleAccount)
        val total = pendingMigrationFiles.size
        
        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            val db = database
            
            for ((index, pair) in pendingMigrationFiles.withIndex()) {
                val (item, file) = pair
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Uploading file ${index + 1} of $total...")
                }
                
                val fileUri = Uri.fromFile(file)
                val newUrl = driveManager.uploadFile(
                    fileUri = fileUri,
                    fileName = item.fileName,
                    folderName = item.folderName
                )
                
                if (newUrl != null) {
                    try {
                        db.child(item.dbPath).setValue(newUrl).awaitTask()
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: write without await
                        db.child(item.dbPath).setValue(newUrl)
                        successCount++
                    }
                }
                file.delete()
            }
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                pendingMigrationFiles.clear()
                Toast.makeText(context, "Migration completed: $successCount of $total files migrated.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cleanupTempMigrationFiles() {
        for (pair in pendingMigrationFiles) {
            pair.second.delete()
        }
        pendingMigrationFiles.clear()
    }

    override fun onDestroyView() {
        profileListener?.let { listener ->
            registeredUid?.let { uid ->
                database.child("users").child(uid).removeEventListener(listener)
            }
        }
        super.onDestroyView()
        _binding = null
    }
}

// Extensions for suspending Firebase calls
private suspend fun Query.awaitValue(): DataSnapshot = suspendCancellableCoroutine { cont ->
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            cont.resume(snapshot)
        }
        override fun onCancelled(error: DatabaseError) {
            cont.resumeWithException(error.toException())
        }
    }
    addListenerForSingleValueEvent(listener)
    cont.invokeOnCancellation { removeEventListener(listener) }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(task.exception ?: Exception("Task failed"))
        }
    }
}
