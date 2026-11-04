# SocialSave for Android

Kotlin + Jetpack Compose version of the SocialSave iOS app (`shootX/SociaLIOS`). Same product: a private,
on-device library of links saved from Instagram, TikTok, YouTube, Facebook, X, Threads, Pinterest, Reddit
and the web. No account, no server. The one addition is AdMob native ads in the library grid.

## Features (parity with iOS)

| iOS | Android |
| --- | --- |
| SwiftData library | Room database (`app/.../data`) |
| Share extension | `ShareReceiverActivity` (Share → SocialSave) |
| Paste a link sheet, clipboard prefill | `PasteLinkSheet` |
| Library: search, platform tabs, favorites, category filter, sort, multi-select | `LibraryScreen` |
| Categories tab, collection screens, edit/reorder/delete categories | `ui/collections` |
| Post details: category, favorite, copy, share, notes, delete | `PostDetailScreen` |
| Titles and thumbnails fetched on device (oEmbed, Open Graph, Instagram embed) | `core/LinkMetadataFetcher` + `MetadataWorker` |
| Weekly reminder notification | `ReminderScheduler` (WorkManager) |
| Rediscover widget (small + medium) | `RediscoverWidget` (Glance) |
| One-Tap Save (Back Tap / Action Button / Siri) | Quick Settings tile + "Save Copied Link" launcher shortcut |
| Onboarding, appearance (system/light/dark), EN / KA / RU | same |

Strings in `app/src/main/res/values*/strings.xml` were generated from the iOS `Localizable.xcstrings`.

## Ads

* Native ads appear in the library and collection grids after every 10th post, drawn like a post card
  with an "Ad" badge (`ads/NativeAdCard.kt`, `ads/NativeAdsController.kt`).
* Consent: Google UMP asks users in the EEA/UK before any ad is requested; Settings shows
  "Privacy Choices" when the user is required to be able to change it.
* **Debug builds always use Google's test ids.** Release builds read the real ids from
  `ADMOB_APP_ID` / `ADMOB_NATIVE_ID` (Gradle property or environment variable, e.g. GitHub Actions secrets),
  and fall back to the test ids if they are missing.

## Project layout

* `core/` – pure Kotlin/JVM: URL normalization, platform detection, title cleanup, preview fetching.
  Unit tested (`./gradlew :core:test`), no Android needed.
* `app/` – the Android app.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```
./gradlew :core:test :app:assembleDebug
```

CI (`.github/workflows/android.yml`) runs the tests and builds debug + release APKs on every push;
the debug APK is attached to each run as an artifact.

Release signing: put `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) in the
project root; it is git-ignored.
