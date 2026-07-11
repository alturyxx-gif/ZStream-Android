-keep interface com.zstream.plugin.api.** { *; }
-keep class com.zstream.plugin.api.** { *; }

# Kotlin coroutines cross the plugin API boundary — must not be obfuscated
-keep interface kotlin.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# Plugin-compiled bytecode calls Kotlin data class synthetic constructors
# (e.g. StreamResult.Variant's default-args <init>) by referencing
# kotlin.jvm.internal.DefaultConstructorMarker by name — must not be renamed.
-keep class kotlin.jvm.internal.** { *; }

# Gson model classes — keep fields so Gson can deserialize by name
-keep class com.zstream.android.data.model.** { *; }
-keep class com.zstream.android.data.remote.** { *; }
-keep class com.zstream.android.data.local.entity.** { *; }
-keep class com.zstream.android.data.SavedProfile { *; }
-keep class com.zstream.android.data.PairedTv { *; }
-keep class com.zstream.android.data.PairedPhoneSession { *; }
-keep class com.zstream.android.data.TraktSessionExport { *; }
-keep class com.zstream.android.data.TraktRepository$DeviceAuthorization { *; }
-keep class com.zstream.android.data.TraktRepository$TokenResponse { *; }
-keep class com.zstream.android.data.adb.SavedTv { *; }
-keep class com.zstream.android.plugin.PluginMetadata { *; }
-keep class com.zstream.android.plugin.Caption { *; }
-keep class com.zstream.android.ui.screens.WyzieSubtitleEntry { *; }
-keepattributes Signature,*Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit — keep service interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson TypeToken subclasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# The TV ADB client registers Bouncy Castle at runtime to generate its RSA certificate.
-keep class org.bouncycastle.** { *; }
