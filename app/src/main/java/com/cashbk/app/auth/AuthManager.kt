package com.cashbk.app.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.cashbk.app.CashBookApp
import com.cashbk.app.network.AuthData
import com.cashbk.app.network.ApiResponse
import com.cashbk.app.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import com.cashbk.app.utils.CustomToast

object AuthManager {

    private fun getContext(): Context? {
        return try {
            CashBookApp.instance
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sends an OTP to the given email address via the Laravel API.
     */
    fun sendOtp(email: String, callback: ((Boolean, String) -> Unit)? = null) {
        val payload = hashMapOf("email" to email)
        RetrofitClient.apiService.sendEmailOtp(payload).enqueue(object : Callback<ApiResponse<Any>> {
            override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {
                val responseBody = response.body()
                val isSuccess = response.isSuccessful && responseBody?.success == true
                val message = responseBody?.message ?: "Response failed with code: ${response.code()}"
                
                showToast(message)
                callback?.invoke(isSuccess, message)
            }

            override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                val errorMsg = t.message ?: "Network error occurred"
                showToast(errorMsg)
                callback?.invoke(false, errorMsg)
            }
        })
    }

    /**
     * Sends an OTP to the given phone number via the Laravel API.
     */
    fun sendPhoneOtp(phone: String, callback: ((Boolean, String) -> Unit)? = null) {
        val payload = hashMapOf("phone" to phone)
        RetrofitClient.apiService.sendPhoneOtp(payload).enqueue(object : Callback<ApiResponse<Any>> {
            override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {
                val responseBody = response.body()
                val isSuccess = response.isSuccessful && responseBody?.success == true
                val message = responseBody?.message ?: "Response failed with code: ${response.code()}"
                
                showToast(message)
                callback?.invoke(isSuccess, message)
            }

            override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                val errorMsg = t.message ?: "Network error occurred"
                showToast(errorMsg)
                callback?.invoke(false, errorMsg)
            }
        })
    }

    /**
     * Verifies OTP and registers or logs in the user, saving the token and authenticating with Firebase.
     */
    fun verifyAndAuthenticate(
        name: String,
        email: String,
        otpCode: String,
        isRegister: Boolean,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        val payload = hashMapOf(
            "name" to name,
            "email" to email,
            "otp" to otpCode
        )

        val apiCall = if (isRegister) {
            RetrofitClient.apiService.register(payload)
        } else {
            RetrofitClient.apiService.login(payload)
        }

        apiCall.enqueue(object : Callback<ApiResponse<AuthData>> {
            override fun onResponse(call: Call<ApiResponse<AuthData>>, response: Response<ApiResponse<AuthData>>) {
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse?.success == true && authResponse.data != null) {
                        val authData = authResponse.data
                        
                        // 1. Save Laravel access_token in SharedPreferences
                        saveAccessToken(authData.access_token)
                        
                        // 2. Sign in to Firebase Auth using custom token
                        signInWithFirebaseToken(authData.firebase_custom_token) { firebaseSuccess, firebaseError ->
                            if (firebaseSuccess) {
                                val successMsg = "Authenticated successfully with Laravel and Firebase"
                                showToast(successMsg)
                                callback?.invoke(true, successMsg)
                            } else {
                                val errorMsg = "Firebase authentication failed: $firebaseError"
                                showToast(errorMsg)
                                callback?.invoke(false, errorMsg)
                            }
                        }
                    } else {
                        val errorMsg = authResponse?.message ?: "Response success is false"
                        showToast(errorMsg)
                        callback?.invoke(false, errorMsg)
                    }
                } else {
                    val errorMsg = "HTTP error code: ${response.code()}"
                    showToast(errorMsg)
                    callback?.invoke(false, errorMsg)
                }
            }

            override fun onFailure(call: Call<ApiResponse<AuthData>>, t: Throwable) {
                val errorMsg = t.message ?: "Network error occurred"
                showToast(errorMsg)
                callback?.invoke(false, errorMsg)
            }
        })
    }

    /**
     * Verifies phone OTP and registers or logs in the user, saving the token and authenticating with Firebase.
     */
    fun verifyAndAuthenticatePhone(
        name: String,
        phone: String,
        otpCode: String,
        isRegister: Boolean,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        val payload = hashMapOf(
            "name" to name,
            "phone" to phone,
            "otp" to otpCode
        )

        val apiCall = if (isRegister) {
            RetrofitClient.apiService.register(payload)
        } else {
            RetrofitClient.apiService.login(payload)
        }

        apiCall.enqueue(object : Callback<ApiResponse<AuthData>> {
            override fun onResponse(call: Call<ApiResponse<AuthData>>, response: Response<ApiResponse<AuthData>>) {
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse?.success == true && authResponse.data != null) {
                        val authData = authResponse.data
                        
                        // 1. Save Laravel access_token in SharedPreferences
                        saveAccessToken(authData.access_token)
                        
                        // 2. Sign in to Firebase Auth using custom token
                        signInWithFirebaseToken(authData.firebase_custom_token) { firebaseSuccess, firebaseError ->
                            if (firebaseSuccess) {
                                val successMsg = "Authenticated successfully with Laravel and Firebase"
                                showToast(successMsg)
                                callback?.invoke(true, successMsg)
                            } else {
                                val errorMsg = "Firebase authentication failed: $firebaseError"
                                showToast(errorMsg)
                                callback?.invoke(false, errorMsg)
                            }
                        }
                    } else {
                        val errorMsg = authResponse?.message ?: "Response success is false"
                        showToast(errorMsg)
                        callback?.invoke(false, errorMsg)
                    }
                } else {
                    val errorMsg = "HTTP error code: ${response.code()}"
                    showToast(errorMsg)
                    callback?.invoke(false, errorMsg)
                }
            }

            override fun onFailure(call: Call<ApiResponse<AuthData>>, t: Throwable) {
                val errorMsg = t.message ?: "Network error occurred"
                showToast(errorMsg)
                callback?.invoke(false, errorMsg)
            }
        })
    }

    /**
     * Alias helper for verification registration step.
     */
    fun registerAndLogin(
        name: String,
        email: String,
        otpCode: String,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        verifyAndAuthenticate(name, email, otpCode, isRegister = true, callback = callback)
    }

    private fun saveAccessToken(token: String) {
        val context = getContext() ?: return
        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? {
        val context = getContext() ?: return null
        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("access_token", null)
    }

    private fun signInWithFirebaseToken(customToken: String, onComplete: (Boolean, String?) -> Unit) {
        FirebaseAuth.getInstance().signInWithCustomToken(customToken)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.message ?: "Unknown Firebase error")
                }
            }
    }

    private fun showToast(message: String) {
        val context = getContext() ?: return
        Handler(Looper.getMainLooper()).post {
            try {
                CustomToast.show(context, message, Toast.LENGTH_LONG)
            } catch (e: Exception) {
                // Ignore if running outside main application context
            }
        }
    }
}
