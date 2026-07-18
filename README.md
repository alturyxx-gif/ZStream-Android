# ZStream Android

Official Android client for ZStream.

## Installation

## Important Note: Plugin Required

This app plays streams from various sources. To work properly, it requires a **plugin APK** to access streaming sources.

For local testing / your own forks you NEED to make your OWN plugin with how the app accepts it.

For official usage, Download the latest release, it will auto-install.

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

## Command-line development

If you'd rather not drive everything through Android Studio, a `Makefile` wraps the common Gradle/adb commands. Requires GNU Make (preinstalled on macOS; on Windows use Git Bash/MSYS2 with `choco install make`, or WSL — plain cmd.exe/PowerShell can't run Makefiles) and `ANDROID_HOME` exported to your SDK path.

Run `make` or `make help` to see the full list. Summary:

**Setup**
| Command | What it does |
| --- | --- |
| `make setup` | Initializes git submodules and creates `local.properties` if missing |
| `make submodules` | Initializes/updates the `libadb-android` and `zstream-plugin` submodules |
| `make doctor` | Prints `ANDROID_HOME`, adb version, connected devices, and available AVDs — useful when something isn't detected |

**Build**
| Command | What it does |
| --- | --- |
| `make build` / `make debug` | Assembles the debug APK |
| `make release` | Assembles the signed release APK |
| `make test` | Runs unit tests |

**Run**
| Command | What it does |
| --- | --- |
| `make run` | Builds, installs, and launches the debug build on the only connected device |
| `make run-id ID=<serial>` | Same, but targets a specific device (see `make devices` for serials) |
| `make run-release` | Builds, installs, and launches the release build |
| `make install` / `make install-release` | Builds and installs only, without launching |
| `make uninstall [ID=<serial>]` | Uninstalls the app |
| `make stop [ID=<serial>]` | Force-stops the app |

**Devices**
| Command | What it does |
| --- | --- |
| `make devices` | Lists connected devices/emulators |
| `make avds` | Lists available emulator AVDs |
| `make emulator [AVD=<name>]` | Boots an emulator (defaults to `Android_Device`) |
| `make logs [ID=<serial>]` / `make logcat` | Streams this app's logcat |
| `make screenshot [ID=<serial>]` | Saves a screenshot to `./screenshot.png` |

**Cleanup**
| Command | What it does |
| --- | --- |
| `make clean` | Runs `gradle clean`, removing `build/` output directories |
| `make clean-cache` | `clean` plus wipes the Gradle cache (`~/.gradle/caches`) and stops the Gradle daemon — fixes stale/corrupt build weirdness, but the next build will be slow while dependencies re-download |
| `make clear-app-data [ID=<serial>]` | Wipes the app's on-device storage/cache (`pm clear`) — different from the Gradle cache, useful for testing a fresh install |

**Plugin sideload** (see [Build from Source](#build-from-source) above for context)
| Command | What it does |
| --- | --- |
| `make plugin-dev` | Builds the debug plugin APK and pushes it to all connected devices |
| `make plugin-release` | Builds the release plugin APK and pushes it to all connected devices |

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
