# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers and source file names for deobfuscation and crash reporting in Google Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve type annotations, signatures, and reflection attributes
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,Deprecated

# Room Database & SQLite Persistence
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }
-keep class com.example.data.local.** { *; }

# Application Data Models
-keep class com.example.data.model.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Jsoup HTML Parser
-keep public class org.jsoup.** { public *; }
-dontwarn org.jsoup.**

# Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Hardware-backed Security & Cryptography
-keep class com.example.data.security.CryptoManager { *; }

# WebView JavascriptInterface (xterm.js terminal bridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

