package com.cashbk.app.di
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object AppModule {
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val db = FirebaseDatabase.getInstance("https://cashbook-5e6a1-default-rtdb.firebaseio.com/") // Update this!
        db.setPersistenceEnabled(true)
        return db
    }
}