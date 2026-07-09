package com.cashbk.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseService {

    private val database: DatabaseReference
        get() = FirebaseDatabase.getInstance().reference

    private val currentUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Checks if a user is currently authenticated in Firebase.
     */
    fun isUserAuthenticated(): Boolean {
        return currentUid != null
    }

    /**
     * Gets the current user's unique ID.
     */
    fun getCurrentUserUid(): String? {
        return currentUid
    }

    /**
     * Writes user-associated data to the Realtime Database at the specified path.
     * Validates that the user is authenticated.
     */
    fun writeData(path: String, data: Any, onComplete: (Boolean, Exception?) -> Unit) {
        val uid = currentUid
        if (uid == null) {
            onComplete(false, Exception("User is not authenticated with Firebase."))
            return
        }

        database.child(path).setValue(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception)
                }
            }
    }

    /**
     * Reads data from a path in the database.
     */
    fun readData(path: String, listener: ValueEventListener) {
        database.child(path).addListenerForSingleValueEvent(listener)
    }

    /**
     * Writes business details to the `/businesses` reference path,
     * ensuring the ownerId or uid matches the authenticated Firebase User ID.
     */
    fun writeBusiness(
        businessId: String,
        businessData: Map<String, Any>,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        val uid = currentUid
        if (uid == null) {
            onComplete(false, Exception("User is not authenticated with Firebase."))
            return
        }

        // Enforce the ownerId property to be the current user's UID
        val updatedData = businessData.toMutableMap()
        updatedData["ownerId"] = uid

        database.child("businesses").child(businessId).setValue(updatedData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception)
                }
            }
    }
}
