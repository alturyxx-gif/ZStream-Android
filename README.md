# ZStream Android

Unofficial Android client for ZStream. Built with Jetpack Compose, Hilt, Media3, and Coil.

## Prerequisites

- **Android Studio** (Ladybug or newer)
- **JDK 17** (bundled with Android Studio or via SDK Manager)
- An active internet connection (streaming sources require network access)

## Setup 

* Download the release APK from the releases.
* If you want to use your own TMDB API Key, you can either build with it hardcoded or set it in runtime in the settings under connections.

## Building

### 1. Clone the repo

```bash
git clone https://github.com/alturyxx-gif/ZStream-Android.git
cd ZStream-Android
```

### 2. Get a TMDB API Read Access Token (optional)

NOTE: The app comes with a TMDB API key hardcoded. If that key doesnt work for you or if you want to use your own, follow these steps.

1. Go to [TMDB Settings -> API](https://www.themoviedb.org/settings/api)
2. Create an account or sign in
3. Request an API key
4. Copy the **API Read Access Token** (a long JWT starting with `eyJ...`)

### 3. Set the TMDB API Key

The app ships with a built-in TMDB API key — **it works out of the box**, no setup needed. Just build and run.

If you want to use your own key, create `local.properties`:

```properties
tmdb.api_key=your-v3-api-key-here
```

You can also override the key at runtime from Settings -> **TMDB API Key**.

### 4. Build & Run

```bash
# Debug build (unsigned)
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Open in Android Studio -> select a device/emulator -> press Run.

## Optional In-App Services

Configured from the Settings screen inside the app. None are required for basic streaming.

| Service | Setting | Purpose |
|---|---|---|
| Febbox | `febboxKey` | Alternative cloud storage provider |
| TiDB | `tidbKey` | Alternative subtitle provider |

## Hardcoded Endpoints (no setup needed)

- **TMDB API Key** - used for fetching movies/shows. Can hardcode it during build or give a API key in runtime in settings under connections
- **Backend API** - used for account sync, settings push (not required for local-only usage)
- **Vidlink provider** - embedded stream source, fetched automatically

## Release Builds

**⚠️ Warning:** The release signing config in `app/build.gradle.kts` contains hardcoded passwords pointing to `zstream.jks`. **You must generate your own keystore and update these values before publishing.**

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../my-keystore.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "your-password"
        keyAlias = "your-alias"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "your-password"
    }
}
```

```bash
# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installRelease
```

Read passwords from environment variables or a separate secure file rather than hardcoding them.

## License

MIT
