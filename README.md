# Tidemark

Tidemark is a read-only, offline viewer for the health and fitness data you keep in [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) on Android. It shows your trends on a dashboard and lets you browse every raw record with its metadata. Nothing leaves your phone.

Tidemark is available in English and French. In French, Health Connect is called Connexion Santé.

## Screenshots

Screenshots will be added before the first release. They will live in `fastlane/metadata/android/en-US/images/phoneScreenshots/` and `fastlane/metadata/android/fr-FR/images/phoneScreenshots/`, so this README, the Play listing and stores that read fastlane metadata, such as F-Droid and IzzyOnDroid, use the same images.

- Dashboard: *screenshot to come*
- Metric detail and sleep hypnogram: *screenshot to come*
- Record explorer and record detail: *screenshot to come*

## What it does

- **Dashboard.** Today's steps, active calories and distance, last night's sleep with its stages, and your latest resting heart rate and weight. Each tile has a 7-day trend, or 30 days for weight.
- **Charts.** Day, week, month and year views for each metric, a hypnogram of each night's sleep stages, and the day's heart rate samples against your resting heart rate. Tap a day to see the records behind it.
- **Record explorer.** Raw records for all eight data types, filtered by date range and by the app that wrote them. Each record shows every field Health Connect stores, including samples, stages, segments, laps, the source app, the device and the recording method.
- **Units.** Metric or imperial follows your language and region, with an override in settings.

Tidemark reads eight data types: steps, distance, active calories burned, exercise sessions, sleep sessions, heart rate, resting heart rate and weight.

## Privacy

- **No network access.** The app has no `INTERNET` permission, and the build removes it if a library tries to add it. CI fails if the release APK asks for any permission outside the allowed list.
- **Nothing collected, shared or sold.** No accounts, ads, analytics or crash reporting.
- **Read-only.** Tidemark never writes, edits or deletes Health Connect data, and it never reads in the background.
- **Almost nothing stored.** Two settings on the device: your unit choice and the time you first granted access. Android backup is turned off.

Read the full policy in [PRIVACY.md](PRIVACY.md) ([français](PRIVACY.fr.md)). It is published at [tidemark.reddevs.io/privacy](https://tidemark.reddevs.io/privacy/) and bundled in the app, built from the same files.

### Permissions

The APK requests exactly these nine Health Connect permissions:

- `android.permission.health.READ_STEPS`
- `android.permission.health.READ_DISTANCE`
- `android.permission.health.READ_ACTIVE_CALORIES_BURNED`
- `android.permission.health.READ_EXERCISE`
- `android.permission.health.READ_SLEEP`
- `android.permission.health.READ_HEART_RATE`
- `android.permission.health.READ_RESTING_HEART_RATE`
- `android.permission.health.READ_WEIGHT`
- `android.permission.health.READ_HEALTH_DATA_HISTORY`, to view your past records beyond the 30 days before you first granted access. Tidemark asks for it only when your device supports it.

The APK also lists `io.reddevs.tidemark.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX Core adds this signature permission so that receivers registered at runtime stay private to the app on Android versions before 13. Only Tidemark holds it, and it gives access to no data.

## Requirements

- Android 9 (API 28) or later.
- On Android 9 to 13, the Health Connect app from Google Play. On Android 14 and later, Health Connect is part of the system.
- Reading data older than 30 days needs a Health Connect version with the past data feature. Without it, Tidemark shows a banner that says how far back your data goes.

## Install

### GitHub Releases

Download `tidemark-<version>.apk` from the [releases page](https://github.com/reddevsdotio/tidemark/releases) and open it on your phone. Each release also has a `SHA256SUMS` file for checking the download. From the first release on, [docs/release.md](docs/release.md) records the signing certificate's SHA-256 fingerprint, so you can check the APK with `apksigner verify --print-certs`.

### Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates apps straight from their GitHub releases. Add an app with this source URL:

```text
https://github.com/reddevsdotio/tidemark
```

Leave pre-releases off unless you want release candidates.

### Google Play

A Google Play listing will follow once the GitHub releases have settled. Play and GitHub builds use the same signing key, so you can switch between them without uninstalling.

## Build from source

You need JDK 17 and the Android SDK with the platform for API 37 (`compileSdk` is 37; `targetSdk` stays 36). The Gradle wrapper downloads the right Gradle version.

```sh
git clone https://github.com/reddevsdotio/tidemark
cd tidemark
./gradlew assembleDebug
```

The debug APK is in `app/build/outputs/apk/debug/`. To run the same checks as CI:

```sh
./gradlew lintDebug testDebugUnitTest assembleRelease
scripts/check-permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

A release build is unsigned unless all four signing variables are set: `TIDEMARK_KEYSTORE_PATH`, `TIDEMARK_KEYSTORE_PASSWORD`, `TIDEMARK_KEY_ALIAS` and `TIDEMARK_KEY_PASSWORD`. The release process is in [docs/release.md](docs/release.md).

To try the app on an emulator, use a Google Play system image on Android 9 to 13 (Health Connect comes from Play) or any image on Android 14 and later. Google's Health Connect Toolbox can write test data.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and questions go to [GitHub issues](https://github.com/reddevsdotio/tidemark/issues). Please don't post your health data in an issue.

## License

Tidemark is free software: you can redistribute it and modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

Every source file carries the identifier `SPDX-License-Identifier: GPL-3.0-or-later`.

## Credits

- [Heartwood](https://github.com/GuyOnWifi/heartwood) (GPL-3.0-or-later) is the reference for the history permission flow, paged reads and the Health Connect test setup.
- [Health Disconnect](https://github.com/Monkopedia/health-disconnect) (Apache-2.0) is the reference for what a raw record explorer shows.

Code taken from either project keeps its original copyright notice and is listed in `NOTICE`.

Health Connect is a trademark of Google LLC. Tidemark is not affiliated with or endorsed by Google.
