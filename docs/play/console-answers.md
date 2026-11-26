# Play Console answers

Answers for the Play Console pages that the other files in this folder don't cover, with the reason for each. They were worked out for Tidemark's first Play release, published on 26 November 2026. The Data safety form is in [data-safety.md](data-safety.md), and the Health Connect permission justifications are in [health-declaration.md](health-declaration.md).

Check the current form wording when you submit. Google changes field names and questions from time to time, and this file follows the app, not the form.

## Store settings

Grow users › Store presence › Store settings.

- **App or game:** App.
- **Category:** Health & Fitness.
- **Tags:** pick from Play's list the ones closest to health, activity, sleep and weight tracking. Play allows up to five.
- **Email address:** a RedDevs role address, never a personal inbox. Play shows it on the store page.
- **Phone number:** empty. It's optional.
- **Website:** `https://tidemark.reddevs.io`, the landing page of the privacy site, which links to both policies.
- **External marketing:** on. Google may advertise the app outside Play, at no cost.

## Store listing

Grow users › Store presence › Store listings. The text and images for each language are in `fastlane/metadata/android/<locale>/`: `title.txt`, `short_description.txt`, `full_description.txt` and `images/`. Fill in English (United States) as the default language, then add French (France) as a translation. `scripts/build-store-graphics.py` regenerates the images; see [docs/release.md](../release.md#store-graphics).

## App content

Monitor and improve › Policy and programs › App content.

- **Privacy policy:** `https://tidemark.reddevs.io/privacy/`.
- **Ads:** no. Tidemark has no ads SDK and no network access.
- **App access:** all functionality is available without special access. There is no account or login. The reviewer needs Health Connect data on the test device, which Health Connect Toolbox can write.
- **Content rating:** answer no to every content question. Tidemark has no user-generated content, no communication between users, no location sharing, no purchases and no web browsing.
- **Target audience:** 18 and over, or 13 and over, but no children's age groups. The app shows health data and is not designed for children.
- **Data safety:** see [data-safety.md](data-safety.md).
- **Government apps, financial features:** no.
- **Advertising ID:** no. No library uses it, and `scripts/check-permissions.sh` fails the build if a library merges in `com.google.android.gms.permission.AD_ID`.

### Health apps

Tick these three features, all under Health and fitness:

- **Activity and fitness:** steps, distance, active calories, exercise sessions, heart rate and resting heart rate.
- **Nutrition and weight management:** the weight tile and charts. Tidemark tracks no nutrition, but weight belongs to this feature, and the app requests `READ_WEIGHT`.
- **Sleep management:** the sleep tile, charts and hypnogram.

Leave Medical, Human subjects research and Other unticked. Tidemark only displays the user's own data: it makes no diagnosis, gives no clinical advice and controls no device. The Health Connect permission justifications that follow are in [health-declaration.md](health-declaration.md).

### Trader status

The RedDevs developer account is declared as a trader, and the app inherits that status.

- **Does your trader status apply to this app?** Yes. RedDevs publishes the app as an organisation. Answering no makes Play tell users in the EEA and the UK that consumer protection laws don't apply to the app.
- **Terms of Service URL:** empty. It's optional, and Tidemark has no terms of service beyond the GPL licence and the privacy policy. Don't put the licence here, because Play labels the link as terms of service.

## Releases

Play App Signing holds the same key as GitHub Releases; [docs/release.md](../release.md#google-play-play-app-signing-with-our-own-key) has the setup. Upload the AAB from the GitHub Release to an internal testing release first, install it from Play on a device, then promote it to production. The account is an organisation account, so Play doesn't require a closed test before production.
