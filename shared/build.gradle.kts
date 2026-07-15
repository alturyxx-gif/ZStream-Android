import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.zstream.shared"
        compileSdk = 36
        minSdk = 24
    }

    // Declared now so the module structure is iOS-ready; no iOS-specific code lives here yet.
    // See the KMP migration plan: Android goes first, iOS actuals come once Android is settled.
    // Building the actual .xcframework requires the Apple toolchain (macOS + Xcode), so that step
    // `./gradlew :shared:assembleSharedXCFramework` from the repo root produces 
    // shared/build/XCFrameworks/debug/Shared.xcframework for Xcode to link against.
    val xcf = XCFramework("Shared")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            xcf.add(this)
        }
    }

    sourceSets {
        androidMain.dependencies {
            // org.json is part of the Android SDK -- fine here, but NOT available on iOS/Kotlin-Native.
            // PluginJson.kt stays in androidMain until the wire format moves to kotlinx.serialization.
        }
    }
}
