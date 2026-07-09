package com.cashbk.app.network

import com.google.gson.annotations.SerializedName

/**
 * Wrapper for standard API responses from the Laravel backend.
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T
)

/**
 * Contains auth data returned on successful registration or login.
 */
data class AuthData(
    @SerializedName("user") val user: UserProfile,
    @SerializedName("access_token") val access_token: String,
    @SerializedName("token_type") val token_type: String,
    @SerializedName("firebase_custom_token") val firebase_custom_token: String
)

/**
 * Basic user profile object.
 */
data class UserProfile(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String?,
    @SerializedName("phone") val phone: String?
)
