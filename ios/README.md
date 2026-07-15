# iOS app

Lives in this monorepo now, alongside the Android/KMP code, so shared logic (currently just
`:shared`'s plugin models, eventually the TMDB catalog layer too) can move between platforms
without syncing two separate repos.

## Building the Kotlin shared framework

Kotlin/Native's iOS targets can only be linked into an actual `.framework`/`.xcframework` on
macOS with Xcode installed -- that step can't run in CI on Linux, so you'll do it locally:

```sh
./gradlew :shared:assembleSharedDebugXCFramework
```

This produces `shared/build/XCFrameworks/debug/Shared.xcframework`. Add it to the Xcode project
(drag into the target, or add via `project.yml` dependencies) to call into Kotlin from Swift.
Use `assembleSharedReleaseXCFramework` for release builds.

Nothing in `commonMain` is iOS-specific yet -- today it's just the plugin/source data models
(`SourceInfo`, `MediaRequest`, `StreamResult`). The TMDB catalog code you already wrote
(`Movie`, `TVShow`, `Endpoints`, the cache layer, etc.) is still pure Swift under `ZStream/Sources`
and doesn't depend on `:shared` at all yet -- whether/when that moves into Kotlin is an open
question, not decided.

## Known gaps in the ported catalog code

- `Utilities/Endpoints.swift` never attaches a TMDB API key/token to requests -- every call will
  401 until that's wired up. Android's equivalent lives in `app/build.gradle.kts` via
  `buildConfigField("String", "TMDB_API_KEY", ...)`, sourced from `local.properties`/env var.
- `BookmarksRepository` and `RecentlyWatchedRepository` are stubs (`// TODO: fetch from Celeste
  backend`) -- no real backend calls yet.

## Project generation

Uses XcodeGen. From `ios/`: `xcodegen generate` to produce the `.xcodeproj`.
