package com.cashbk.app.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object AppModule {
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val db = FirebaseDatabase.getInstance()
        db.setPersistenceEnabled(false) // Disable local unencrypted caching for security compliance
        return db
    }
}