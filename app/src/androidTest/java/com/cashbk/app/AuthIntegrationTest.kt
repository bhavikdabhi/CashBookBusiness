package com.cashbk.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cashbk.app.auth.AuthManager
import com.cashbk.app.data.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest {

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        println("Target package name: ${appContext.packageName}")
    }

    @Test
    fun testAuthenticationFlow() {
        val testEmail = "bhavikdabhi1101@gmail.com"
        val testName = "Bhavik Dabhi"
        val mockOtp = "123456"

        // 1. Request OTP
        val latchSend = CountDownLatch(1)
        var sendSuccess = false
        var sendMsg = ""

        AuthManager.sendOtp(testEmail) { success, message ->
            sendSuccess = success
            sendMsg = message
            latchSend.countDown()
        }

        latchSend.await(10, TimeUnit.SECONDS)
        println("Send OTP result - Success: $sendSuccess, message: $sendMsg")

        // 2. Login & Firebase Authentication
        val latchAuth = CountDownLatch(1)
        var authSuccess = false
        var authMsg = ""

        AuthManager.verifyAndAuthenticate(testName, testEmail, mockOtp, isRegister = false) { success, message ->
            authSuccess = success
            authMsg = message
            latchAuth.countDown()
        }

        latchAuth.await(10, TimeUnit.SECONDS)
        println("Authentication result - Success: $authSuccess, message: $authMsg")

        if (authSuccess) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            assertNotNull("FirebaseAuth currentUser should not be null on successful authentication", currentUser)

            // 3. Write test business to Firebase Realtime Database
            val latchDb = CountDownLatch(1)
            var dbSuccess = false
            var dbError: Exception? = null

            val mockBusiness = mapOf(
                "name" to "Bhavik Integration Test Business",
                "currency" to "INR",
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseService.writeBusiness("test_business_id", mockBusiness) { success, exception ->
                dbSuccess = success
                dbError = exception
                latchDb.countDown()
            }

            latchDb.await(5, TimeUnit.SECONDS)
            assertTrue("Should write business successfully to database", dbSuccess)
            assertNull("Exception should be null on successful db write", dbError)
        }
    }
}
