# SocialSave for Android

Kotlin + Jetpack Compose version of the SocialSave iOS app (`shootX/SociaLIOS`). Same product: a private
library of links saved from Instagram, TikTok, YouTube, Facebook, X, Threads, Pinterest, Reddit and the web.
Signing in is optional: with Apple, Google or Facebook the library syncs through the SocialSave API
(`SociaLAPI`) between phones, iPhone included. Without an account everything stays on the device. The one
addition is AdMob native ads in the library grid.

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

## Configuration

Values are read from a Gradle property (`-P`, `~/.gradle/gradle.properties`) or an environment variable
(e.g. GitHub Actions secrets). The app builds without any of them.

| Name | Used for |
| --- | --- |
| `ADMOB_APP_ID` | AdMob app id (release only; debug and missing values use Google's test ids) |
| `ADMOB_NATIVE_ID` | AdMob native ad unit id (release only, same fallback) |
| `FACEBOOK_APP_ID` | Facebook Login app id; the Facebook button is hidden while this or the token is empty |
| `FACEBOOK_CLIENT_TOKEN` | Facebook client token (App settings → Advanced) |
| `API_BASE_URL` | Optional; the sync API, default `https://socialsave.cc/api/v1` |
| `GOOGLE_SERVER_CLIENT_ID` | Optional; the Google **web** OAuth client the API accepts as `aud` (a default is built in) |

Provider setup outside the code:

* **Google:** in the same Google Cloud project as the web client, create an **Android** OAuth client for
  package `com.shoto.socialsave` with the SHA-1 of every signing key in use: the debug keystore, the
  upload/release key and the Play App Signing key (Play Console → App integrity). Without a matching
  Android client, Credential Manager returns no Google account.
* **Facebook:** in the Facebook app, add the Android platform with package `com.shoto.socialsave`, class
  `com.shoto.socialsave.ui.MainActivity`, and the key hash of each signing key
  (`keytool -exportcert -alias <alias> -keystore <keystore> | openssl sha1 -binary | openssl base64`;
  for Play App Signing, convert the SHA-1 from Play Console to base64).
* **Apple:** nothing on the device; Sign in with Apple runs as a web flow through the API, which returns to
  `socialsave://auth/apple`.

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
