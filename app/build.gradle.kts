import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// google-services.json isn't committed (it's per-Firebase-project config), so only apply the
// plugin when a developer has actually dropped one in -- keeps the build working for everyone
// else without Firebase set up locally.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.zstream.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zstream.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "v1.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val props = Properties()
        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            localProps.inputStream().use { props.load(it) }
        }
        // No hardcoded defaults for any of these -- public repo. Env vars are CI secrets;
        // local.properties (gitignored) covers local dev. Empty values disable the feature.
        val tmdbApiKey = System.getenv("TMDB_API_KEY") ?: props.getProperty("tmdb.api_key", "")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        val traktClientId = System.getenv("TRAKT_CLIENT_ID") ?: props.getProperty("trakt.client_id", "")
        val traktClientSecret = System.getenv("TRAKT_CLIENT_SECRET") ?: props.getProperty("trakt.client_secret", "")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"$traktClientId\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"$traktClientSecret\"")
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
            // Keystore password never committed (public repo): ZSTREAM_SIGNING_PASSWORD env var
            // (CI secret) -> `zstream.signing_password` in local.properties (gitignored).
            val signingProps = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }
                ?.inputStream()?.use { signingProps.load(it) }
            val signingPassword = System.getenv("ZSTREAM_SIGNING_PASSWORD")
                ?: signingProps.getProperty("zstream.signing_password", "")
            storeFile = file("../zstream.jks")
            storePassword = signingPassword
            keyAlias = "zstream"
            keyPassword = signingPassword
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

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "hi",
            "es",
            "ar",
            "fr",
            "bn",
            "pt-rBR",
            "id",
            "ur-rPK",
            "ru",
        )
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
    implementation("androidx.appcompat:appcompat:1.7.1")
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
    implementation(libs.media3.exoplayer.ima)

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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation(project(":libadb"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
