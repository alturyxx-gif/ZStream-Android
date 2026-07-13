# ZStream Android

Official Android client for ZStream.

## Installation

## Important Note: Plugin Required

This app plays streams from various sources. To work properly, it requires a **plugin APK** to access streaming sources.

### Then download the latest release

1. Go to [Releases](https://github.com/alturyxx-gif/ZStream-Android/releases)
2. Download the latest `app-release.apk`
3. Enable installation from unknown sources in Settings (Android 8+)
4. Tap the APK to install
5. Then open the app, give the app the link to the plugin apk and it'll install it for you.

---

## Build from Source

If you want to compile it yourself:

1. Install **Android Studio**
2. Clone the repository:
   ```bash
   git clone --recurse-submodules https://github.com/alturyxx-gif/ZStream-Android.git
   ```
3. Open in Android Studio and click **Run**

The app will build and install automatically.

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
Watch history and preferences are stored locally on your device. The app doesn't send your data to external servers unless you enable optional features like syncing to the cloud, or integrations like Trakt.

**Can I export my watch history?**  
Yes. Go to **Settings → Watch History → Export** to save your library as JSON.

## Support & Feedback

- **Found a bug?** Report it on our [Discord server](https://discord.com/invite/wmbWfk4SGy) or open an issue here on this GitHub.
- **Have a suggestion?** Share it on our [Discord server](https://discord.com/invite/wmbWfk4SGy)
