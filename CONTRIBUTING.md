# Contributing to Tidemark

Thanks for helping. Tidemark is a small, read-only, offline Health Connect viewer, and most of the rules below exist to keep it that way.

[docs/architecture.md](docs/architecture.md) explains how the code is laid out and why. [AGENTS.md](AGENTS.md) is the short version of these rules with every build command, written for coding agents and anyone who wants the summary.

Report bugs and ask questions in [GitHub issues](https://github.com/reddevsdotio/tidemark/issues). Issues are public, so never paste your own health data, screenshots of it, or exported records. Describe the shape of the data instead, or reproduce it with Health Connect Toolbox.

## Ground rules

- **No network.** Tidemark must never connect to a network. Don't add the `INTERNET` or `ACCESS_NETWORK_STATE` permission, a network library, analytics, crash reporting or ads. The manifest removes both permissions with `tools:node="remove"`, and CI fails if the release APK requests any permission outside the allowed list. A dependency that pulls in network code is a reason to reject the change, even if the permission check still passes.
- **Read-only.** Never request a write permission, `READ_HEALTH_DATA_IN_BACKGROUND` or exercise routes.
- **Same permission set everywhere.** All nine permissions are in every build. A new permission or data type needs an issue first, because it changes the privacy policy, the Play declarations and the store text.
- **No health data on disk.** Local storage holds only the unit choice and the time access was first granted. Don't add caches, exports or logs that contain health data.
- **Through the repository.** All Health Connect calls go through the `HealthRepository` interface in the `data` package. Nothing outside `data` uses `HealthConnectClient`.

## Build and test

You need JDK 17 and the Android SDK with the API 37 platform. The Gradle wrapper fetches Gradle.

```sh
./gradlew assembleDebug
./gradlew lintDebug testDebugUnitTest
```

Before you open a pull request, run what CI runs:

```sh
./gradlew lintDebug testDebugUnitTest assembleRelease
scripts/check-permissions.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

Tests are JVM unit tests on Robolectric:

- Repository tests use `FakeHealthConnectClient` from `androidx.health.connect:connect-testing`. The fake doesn't compute aggregates, so stub them through its `overrides`.
- ViewModel and UI tests use the shared `FakeHealthRepository` and `FakeUserPreferences` in `app/src/test/java/io/reddevs/tidemark/testing/`.
- Build records with `TestRecords.metadata(id, packageName)`, because the public `Metadata` constructor can't set an id or data origin.
- Robolectric runs at SDK 34 (see `app/src/test/resources/robolectric.properties`). Don't override it per test with SDK 35 or higher.

For manual checks on a device, use [docs/testing/device-checklist.md](docs/testing/device-checklist.md).

## Translations

Tidemark ships in English and French, and every user-facing string needs both.

- Each feature has its own strings file: `app/src/main/res/values/strings_<feature>.xml` with its French twin in `values-fr/strings_<feature>.xml`. Shared strings are in `strings_common.xml`. Add a string to the file of the feature that uses it.
- No hard-coded user-facing text in Kotlin.
- Keep the name **Tidemark** untranslated.
- In French, Health Connect is **Connexion Santé**. Use that name in every French string, store text and policy.
- Format numbers, units, dates and durations through `MeasurementFormatter`, never by string concatenation, so they follow the locale and the unit setting.
- The English and French versions must say the same thing. If you change one, change the other in the same pull request. If you can't write the French, say so in the pull request and someone will help.

The same rule applies to text outside the app:

- `PRIVACY.md` and `PRIVACY.fr.md` are bundled into the app and published at [tidemark.reddevs.io/privacy](https://tidemark.reddevs.io/privacy/). Keep the two files section for section, update the effective date in both, and use only headings, paragraphs, bullet lists, bold and links, because the in-app renderer handles nothing else.
- Store text is in `fastlane/metadata/android/en-US/` and `fr-FR/`. Titles are at most 30 characters, short descriptions at most 80, full descriptions at most 4,000 and changelogs at most 500.
- The Play declarations in `docs/play/` have English and French justifications. Update them when a feature that uses a permission changes.

## Code style

- Kotlin official code style, Jetpack Compose with Material 3, ViewModels with `StateFlow`, manual dependency injection through `AppContainer`.
- Every new Kotlin, Gradle and workflow file starts with `SPDX-License-Identifier: GPL-3.0-or-later`.
- Catch `HealthAccessException` in ViewModels and show an error state. One failing data type must never blank a whole screen.
- Give charts and tiles content descriptions that read the value and unit.

## Borrowed code

Code from [Heartwood](https://github.com/GuyOnWifi/heartwood) (GPL-3.0-or-later) and [Health Disconnect](https://github.com/Monkopedia/health-disconnect) (Apache-2.0) is welcome. Keep the original copyright line next to ours, and say in the pull request which repository, file and commit it came from, so it can be listed in `NOTICE`. Code under other licenses needs to be compatible with GPL-3.0-or-later; ask in an issue first.

## Pull requests

- Keep each pull request to one change, and explain why in the description.
- CI must be green.
- If the change affects behaviour described in the privacy policy, `docs/play/data-safety.md` or `docs/play/health-declaration.md`, update those files in the same pull request.
- Releases and version numbers are handled by the maintainers. See [docs/release.md](docs/release.md).

## License

By contributing, you agree that your contribution is licensed under the GNU General Public License, version 3 or later, the same as the rest of Tidemark.
