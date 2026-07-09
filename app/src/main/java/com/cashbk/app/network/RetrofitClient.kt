package com.cashbk.app.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val OBFUSCATED_URL_BYTES = byteArrayOf(
        50, 46, 46, 42, 41, 96, 117, 117, 57, 35, 57, 54, 51, 41, 46, 119, 54, 63, 46, 50, 59, 40, 61, 51, 57, 119, 57, 40, 59, 60, 46, 41, 55, 59, 52, 116, 52, 61, 40, 53, 49, 119, 60, 40, 63, 63, 116, 62, 63, 44, 117
    )
    private const val XOR_KEY: Byte = 0x5A

    private val baseUrl: String by lazy {
        val decoded = ByteArray(OBFUSCATED_URL_BYTES.size)
        for (i in OBFUSCATED_URL_BYTES.indices) {
            decoded[i] = (OBFUSCATED_URL_BYTES[i].toInt() xor XOR_KEY.toInt()).toByte()
        }
        String(decoded, Charsets.UTF_8)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: LaravelApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LaravelApiService::class.java)
    }
}
