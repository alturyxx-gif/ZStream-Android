import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zstream.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zstream.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "v1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties()
        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            localProps.inputStream().use { props.load(it) }
        }
        buildConfigField("String", "TMDB_API_KEY", "\"${props.getProperty("tmdb.api_key", "84259f99204eeb7d45c7e3d8e36c6123")}\"")
        // No real site id is ever committed here -- this is a public repo, and a hardcoded
        // default would mean every fork/CI/random build silently reports into our own Rybbit
        // dashboard. Resolution order: RYBBIT_SITE_ID env var (set as a GitHub Actions secret on
        // the real release pipeline) -> `rybbit.site_id` in local.properties (gitignored, for
        // local dev) -> empty string, which RybbitAnalytics treats as "tracking disabled".
        val rybbitSiteId = System.getenv("RYBBIT_SITE_ID") ?: props.getProperty("rybbit.site_id", "")
        buildConfigField("String", "RYBBIT_SITE_ID", "\"$rybbitSiteId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../zstream.jks")
            storePassword = "legacy-appeasing-bagpipe-swimwear-stretch"
            keyAlias = "zstream"
            keyPassword = "legacy-appeasing-bagpipe-swimwear-stretch"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.effect)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.androidx.media)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.bouncycastle)
    implementation(libs.datastore)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.work)
    implementation(libs.haze)
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation(project(":libadb"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
