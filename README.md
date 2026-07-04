# ZStream Android

Official Android client for ZStream.

## Installation

## Important Note: Plugin Required

This app plays streams from various sources. To work properly, it may require a **plugin APK** to access certain streaming services.

- If you built from source, the plugin is included
- If you downloaded the APK, check the [Releases](https://github.com/alturyxx-gif/ZStream-Android/releases) page for the plugin
- Place the plugin APK in the device's app directory or follow instructions in your release notes

**Without the plugin, the app can still access some streams but with limited sources.**

### Then download the latest release

1. Go to [Releases](https://github.com/alturyxx-gif/ZStream-Android/releases)
2. Download the latest `app-release.apk`
3. Enable installation from unknown sources in Settings (Android 8+)
4. Tap the APK to install

---

## Build from Source

If you want to compile it yourself:

1. Install **Android Studio** (Ladybug or newer)
2. Clone the repository:
   ```bash
   git clone --recurse-submodules https://github.com/alturyxx-gif/ZStream-Android.git
   ```
3. Open in Android Studio and click **Run**

The app will build and install automatically.

## First Time Setup

The app works out of the box. No account needed.

### Optional: Add Your Own TMDB API Key

The app comes with a built-in TMDB API key. If it stops working or you prefer to use your own:

1. Go to [The Movie Database (TMDB)](https://www.themoviedb.org/settings/api)
2. Create a free account and request an API key
3. In the app, go to **Settings → Connections → TMDB API Key**
4. Paste your key and tap **Save**

Searches will now use your personal key.

## FAQ

**Is this an official app?**  
Yes, this is an official, open-source project.

**Is it safe?**  
Yes, the source code is publicly available on GitHub. You can audit it yourself or build from source.

**Does it require an account?**  
No account needed. The app works anonymously out of the box.

**Does it store my data?**  
Watch history and preferences are stored locally on your device. The app doesn't send your data to external servers unless you enable optional features like syncing to the cloud.

**Can I export my watch history?**  
Yes. Go to **Settings → Watch History → Export** to save your library as JSON.

**Why do I get "unsupported environment" errors?**  
The app disables some features on emulators or rooted devices. This is a security measure. Build a debug version from source to bypass this but you wont be able to watch anything.

## Support & Feedback

- **Found a bug?** Report it on our [Discord server](https://discord.com/invite/wmbWfk4SGy)
- **Have a suggestion?** Share it on our [Discord server](https://discord.com/invite/wmbWfk4SGy)
