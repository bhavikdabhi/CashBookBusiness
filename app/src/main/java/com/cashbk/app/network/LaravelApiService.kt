package com.cashbk.app.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header

interface LaravelApiService {
    @POST("api/send-otp")
    fun sendPhoneOtp(@Body body: HashMap<String, String>): Call<ApiResponse<Any>>

    @POST("api/verify-otp")
    fun verifyPhoneOtp(@Body body: HashMap<String, String>): Call<ApiResponse<Any>>

    @POST("api/send-email-otp")
    fun sendEmailOtp(@Body body: HashMap<String, String>): Call<ApiResponse<Any>>

    @POST("api/verify-email-otp")
    fun verifyEmailOtp(@Body body: HashMap<String, String>): Call<ApiResponse<Any>>

    @POST("api/register")
    fun register(@Body body: HashMap<String, String>): Call<ApiResponse<AuthData>>

    @POST("api/login")
    fun login(@Body body: HashMap<String, String>): Call<ApiResponse<AuthData>>

    @GET("api/profile")
    fun getProfile(@Header("Authorization") token: String): Call<ApiResponse<UserProfile>>
}
