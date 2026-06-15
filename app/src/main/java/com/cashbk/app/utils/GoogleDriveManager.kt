package com.cashbk.app.utils

import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveManager(private val context: Context, private val googleAccount: GoogleSignInAccount) {

    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = googleAccount.account
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("CashBookBusiness")
            .build()
    }

    /**
     * Uploads a file to a specific folder in Google Drive.
     * @param fileUri The URI of the file to upload.
     * @param fileName The name to give the file in Drive.
     * @param folderName The name of the folder where the file should be saved.
     * @param mimeType The MIME type of the file.
     * @return The webViewLink of the uploaded file.
     */
    suspend fun uploadFile(
        fileUri: Uri,
        fileName: String,
        folderName: String = "CashBook_Assets",
        mimeType: String = "image/jpeg"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(fileUri) ?: return@withContext null
            val mediaContent = InputStreamContent(mimeType, inputStream)

            val fileMetadata = File().apply {
                name = fileName
            }

            // Get or create the folder
            val folderId = getOrCreateFolder(folderName)
            if (folderId != null) {
                fileMetadata.parents = listOf(folderId)
            }

            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            // Set permission to anyone with link so it can be viewed by Glide/Partners
            val permission = Permission().apply {
                type = "anyone"
                role = "reader"
            }
            driveService.permissions().create(uploadedFile.id, permission).execute()

            uploadedFile.webViewLink
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a file from Google Drive.
     * @param fileUrl The full WebView URL of the file.
     */
    suspend fun deleteFile(fileUrl: String) = withContext(Dispatchers.IO) {
        val fileId = extractFileId(fileUrl) ?: return@withContext
        try {
            driveService.files().delete(fileId).execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Gets an existing folder ID or creates a new one under the "Cashbook" root folder.
     */
    private suspend fun getOrCreateFolder(folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Get or create the root folder: "Cashbook"
            val rootFolderId = getOrCreateFolderAtRoot("Cashbook") ?: return@withContext null
            
            // 2. Get or create the child folder (e.g., "Receipt" or "Profile") inside the root folder
            getOrCreateFolderInParent(folderName, rootFolderId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getOrCreateFolderAtRoot(folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and 'root' in parents and trashed = false"
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                result.files[0].id
            } else {
                val folderMetadata = File().apply {
                    name = folderName
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf("root")
                }
                val folder = driveService.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                folder.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getOrCreateFolderInParent(folderName: String, parentId: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and '$parentId' in parents and trashed = false"
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                result.files[0].id
            } else {
                val folderMetadata = File().apply {
                    name = folderName
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(parentId)
                }
                val folder = driveService.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                folder.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        /**
         * Extracts the file ID from a Google Drive URL.
         */
        fun extractFileId(url: String): String? {
            return try {
                if (url.contains("/d/")) {
                    url.substringAfter("/d/").substringBefore("/")
                } else if (url.contains("id=")) {
                    Uri.parse(url).getQueryParameter("id")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Converts a Drive WebView link into a high-quality thumbnail URL.
         */
        fun getThumbnailUrl(driveUrl: String, size: Int = 600): String {
            val fileId = extractFileId(driveUrl) ?: return driveUrl
            return "https://drive.google.com/thumbnail?id=$fileId&sz=w$size"
        }
    }
}
