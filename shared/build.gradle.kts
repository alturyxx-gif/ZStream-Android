import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
}

// SKIE 0.9.4 doesn't yet support the Kotlin version resolved by this project's plugin
// classpath (2.2.10) -- pinning it further back would fight AGP's own Kotlin requirement.
// Disabled until a SKIE release adds support; Flow bridging to Swift falls back to plain
// suspend functions in the meantime (see shared/src/iosMain repositories).
skie {
    isEnabled = false
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
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.atomicfu)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
