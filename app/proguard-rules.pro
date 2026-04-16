# Project Specific Rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Google API Client (Drive)
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.drive.model.** { *; }
-dontwarn com.google.api.client.**

# Glide
-keep public class * extends com.github.bumptech.glide.module.AppGlideModule
-keep public class * extends com.github.bumptech.glide.module.LibraryGlideModule
-keep class com.github.bumptech.glide.** { *; }
-dontwarn com.github.bumptech.glide.**

# Data Classes (Important for Firebase Serialization)
-keep class com.cashbk.app.dataclass.** { *; }
-keepclassmembers class com.cashbk.app.dataclass.** {
    <fields>;
    <init>(...);
}

# Android Framework
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# SDP/SSP (Dimensions)
-keep class com.intuit.sdp.** { *; }
-keep class com.intuit.ssp.** { *; }

# Remove Logs for Scanner Cleanliness
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}