# ZStream Android ProGuard Rules
# Aggressive obfuscation for release builds

# ==============================================================================
# General Settings
# ==============================================================================
-verbose
-optimizationpasses 2
-dontpreverify
-repackageclasses 'com.zstream.a'
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==============================================================================
# Kotlin/Coroutines
# ==============================================================================
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class **$WhenMappings { *; }

# ==============================================================================
# AndroidX & Jetpack
# ==============================================================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Compose (aggressive obfuscation of internals)
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** {
    public <methods>;
}

# Navigation
-keep class androidx.navigation.** { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** {
    public <methods>;
}

# Room Database
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keepclassmembers @androidx.room.Entity class * {
    public <init>(...);
    public <fields>;
}
-keepclassmembers @androidx.room.Dao interface * {
    <methods>;
}

# WorkManager
-keep class androidx.work.** { *; }
-keepclassmembers class androidx.work.** {
    public <methods>;
}

# ==============================================================================
# Hilt Dependency Injection
# ==============================================================================
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep class com.google.dagger.hilt.** { *; }
-keep interface com.google.dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
    @dagger.hilt.android.lifecycle.HiltViewModelFactory <init>(...);
}
-keep class **_HiltModules { *; }
-keep class **_Hilt* { *; }
-dontwarn dagger.hilt.**

# ==============================================================================
# Media3/ExoPlayer
# ==============================================================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** {
    public <methods>;
}
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# ==============================================================================
# Retrofit & OkHttp
# ==============================================================================
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.** { *; }
-keepclassmembers class okhttp3.** {
    private <fields>;
}
-dontwarn okhttp3.**

# ==============================================================================
# Gson/JSON Serialization
# ==============================================================================
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.JsonSerializable
-keep class * implements com.google.gson.JsonDeserializer

# ==============================================================================
# Coil Image Loading
# ==============================================================================
-keep class coil.** { *; }
-keep class coil3.** { *; }
-keepclassmembers class coil.** {
    public <methods>;
}

# ==============================================================================
# Datastore
# ==============================================================================
-keep class androidx.datastore.** { *; }
-keep class com.google.protobuf.** { *; }

# ==============================================================================
# Credentials
# ==============================================================================
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.** { *; }

# ==============================================================================
# BouncyCastle Cryptography
# ==============================================================================
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** {
    public <methods>;
}

# ==============================================================================
# TV Framework (if targeting TV)
# ==============================================================================
-keep class androidx.tv.** { *; }
-keep interface androidx.tv.** { *; }

# ==============================================================================
# Custom ZStream Code (Keep minimal, obfuscate aggressively)
# ==============================================================================
-keep class com.zstream.android.MainActivity { *; }
-keep class com.zstream.android.MyApplication { *; }

# Keep all Activities (Android requires them)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# Keep View constructors (for XML inflation)
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep Parcelable (for IPC)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==============================================================================
# String Obfuscation (aggressive)
# ==============================================================================
-obfuscationdictionary obfuscation_dict.txt
-classobfuscationdictionary obfuscation_dict.txt
-packageobfuscationdictionary obfuscation_dict.txt

# ==============================================================================
# Additional Obfuscation Directives
# ==============================================================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-keepattributes *Annotation*
-dontwarn **$$Lambda$*

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ==============================================================================
# Native Methods (JNI - Keep signatures)
# ==============================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==============================================================================
# Reflection Protection
# ==============================================================================
-keepclassmembers,allowobfuscation interface * {
    <methods>;
}

# ==============================================================================
# Suppress Warnings
# ==============================================================================
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**

