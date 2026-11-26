# Release runbook

This runbook covers how CI builds, signs and publishes Tidemark, and every step a person does by hand. GitHub Releases is the first channel. Google Play comes later and uses the same key.

## What runs where

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `.github/workflows/ci.yml` | Pull requests, pushes to `main` | Validates the Gradle wrapper, runs `./gradlew lintDebug testDebugUnitTest assembleRelease`, then `scripts/check-permissions.sh` on the unsigned release APK. Uploads lint and test reports as artifacts. |
| `.github/workflows/release.yml` | Tags `v*` | Checks the tag and versions, decodes the keystore, builds a signed APK and AAB, checks permissions and signatures, and creates a GitHub Release with `tidemark-<version>.apk`, `tidemark-<version>.aab`, `SHA256SUMS` and `versionCode`. |
| `.github/workflows/pages.yml` | Changes to `PRIVACY.md` or `PRIVACY.fr.md` on `main`, or a manual run | Renders the policy with `scripts/build-pages.py` and deploys it to <https://tidemark.reddevs.io/privacy/> and <https://tidemark.reddevs.io/fr/privacy/>. Pull requests only build the site. |

| Script | Use |
| --- | --- |
| `scripts/check-permissions.sh <apk>` | Fails unless the APK requests exactly the nine Health Connect read permissions plus `io.reddevs.tidemark.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (added by androidx.core, app-private). Fails on `INTERNET`, `ACCESS_NETWORK_STATE` or anything else. If the permission set changes on purpose, update the allow-list and `docs/play/health-declaration.md` in the same pull request. |
| `scripts/check-release-version.sh <tag>` | The release gate: tag equals `v` + `tidemark.versionName`, `tidemark.versionCode` is greater than every published code, and the changelog file exists. Offline: `PREVIOUS_VERSION_CODE=<n> scripts/check-release-version.sh v1.0.0`. |
| `scripts/verify-signature.sh <apk> [sha256]` | Runs `apksigner verify --print-certs` and, given a fingerprint, fails unless the APK is signed with that certificate. |
| `scripts/build-pages.py <dir>` | Builds the policy site locally. Open `<dir>/privacy/index.html` to preview it. |

The scripts find `aapt2` and `apksigner` in the newest `build-tools` under `$ANDROID_HOME`. Set `AAPT2` or `APKSIGNER` to override.

## Secrets and variables

| Name | Kind | Value |
| --- | --- | --- |
| `TIDEMARK_KEYSTORE_B64` | Secret | The keystore file, base64-encoded on one line. |
| `TIDEMARK_KEYSTORE_PASSWORD` | Secret | Keystore password. |
| `TIDEMARK_KEY_ALIAS` | Secret | Alias of the key inside the keystore. |
| `TIDEMARK_KEY_PASSWORD` | Secret | Key password. With a PKCS12 keystore (the `keytool` default) it is the same as the keystore password. |
| `TIDEMARK_CERT_SHA256` | Variable (not secret) | SHA-256 fingerprint of the signing certificate. When set, a release signed with any other key fails. |

`TIDEMARK_KEYSTORE_PATH` is not a secret. The release workflow points it at the decoded file in the runner's temp directory, and a final step deletes that file. `GITHUB_TOKEN` is provided automatically.

The release job runs in a GitHub environment named `release`. Store the four secrets as environment secrets there (recommended) or as repository secrets. GitHub masks secret values in logs. It masks the alias too, so pick an alias that doesn't otherwise appear in build output: `tidemark-release` is fine, `tidemark` alone would mask every file name in the log.

## One-time setup

### 1. Create the signing key

One key signs every channel: GitHub Releases now, Google Play later. If it is lost, existing GitHub installs can never be updated. Do this on a trusted machine, outside the repository.

```sh
keytool -genkeypair -v \
  -keystore tidemark-release.jks -storetype PKCS12 \
  -alias tidemark-release \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tidemark, O=RedDevs, C=<country code>"
```

`keytool` prompts for the password, so it stays out of shell history. 10,000 days is about 27 years. Play requires a key valid past 22 October 2033. Generate a long random password in the password manager first and paste it at the prompt.

Record the certificate fingerprint:

```sh
keytool -list -v -keystore tidemark-release.jks -alias tidemark-release | grep 'SHA256:'
```

Write it in [Signing certificate](#signing-certificate) below and save it as the `TIDEMARK_CERT_SHA256` repository variable. Colons and case don't matter:

```sh
gh variable set TIDEMARK_CERT_SHA256 --repo reddevsdotio/tidemark --body '<fingerprint>'
```

### 2. Back up the key

- Store the keystore, base64-encoded, with its password and alias in the RedDevs secret store. Tidemark's key has been kept there since 25 November 2026, and that is its only copy outside the GitHub secrets and Play.
- Test the backup once: read it back, decode it and run the `keytool -list` command above against it.
- Never commit the keystore. `.gitignore` already excludes `*.jks` and `*.keystore`.
- Once the secrets are set, delete any other working copies of the keystore and the base64 text.

The GitHub secrets can't be read back, so they don't count as a copy. Losing the key would stop GitHub and Obtainium installs from updating: users would have to uninstall and reinstall. Play could recover through an upload key reset, because Google holds its own copy as the app signing key.

### 3. Add the secrets

Create the `release` environment first: **Settings → Environments → New environment → `release`**. Under **Deployment branches and tags**, choose **Selected branches and tags** and add a tag rule `v*`. Adding yourself as a required reviewer is optional; it makes every release wait for a click.

Then set the secrets. `gh secret set` reads values from stdin or a prompt, so nothing lands in shell history or the clipboard:

```sh
base64 -w0 tidemark-release.jks | gh secret set TIDEMARK_KEYSTORE_B64 --repo reddevsdotio/tidemark --env release
gh secret set TIDEMARK_KEYSTORE_PASSWORD --repo reddevsdotio/tidemark --env release   # prompts
gh secret set TIDEMARK_KEY_ALIAS --repo reddevsdotio/tidemark --env release           # prompts
gh secret set TIDEMARK_KEY_PASSWORD --repo reddevsdotio/tidemark --env release        # prompts
```

On macOS use `base64 -i tidemark-release.jks` (it doesn't wrap lines). To use repository secrets instead, drop `--env release`.

### 4. Protect tags and `main`

- **Settings → Rules → Rulesets → New tag ruleset**: target `v*`, restrict creations, updates and deletions to maintainers. Anyone who can push a `v*` tag can publish a signed release.
- **Branch ruleset for `main`**: require pull requests and the status check **Lint, test, release build, permissions** from CI.

### 5. Privacy policy site on tidemark.reddevs.io

Do these steps in order. Verifying the domain first means nobody else can claim `tidemark.reddevs.io` on GitHub Pages.

1. **Verify the domain for the organization.** In the **organization settings → Pages → Add a domain**, enter `reddevs.io`. GitHub shows a TXT record (`_github-pages-challenge-reddevsdotio.reddevs.io`). Add it in the reddevs.io DNS, then click **Verify**. Leave the TXT record in place.
2. **Add the DNS record.** `tidemark.reddevs.io  CNAME  reddevsdotio.github.io.` Don't add wildcard records.
3. **Set the Pages source.** In the repo: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
4. **Set the custom domain.** On the same page, enter `tidemark.reddevs.io` under **Custom domain** and save. Wait for the DNS check and certificate (minutes to an hour), then tick **Enforce HTTPS**. With an Actions-based deployment the custom domain lives in repo settings; no `CNAME` file is needed.
5. **Publish.** Run the Pages workflow from the Actions tab (**Run workflow**), or push a change to `PRIVACY.md`. Check <https://tidemark.reddevs.io/privacy/> and <https://tidemark.reddevs.io/fr/privacy/> over HTTPS. The workflow warns if the deployed URL is not the custom domain.

## Making a release

1. **Bump the versions** in `gradle.properties`:
   - `tidemark.versionName`: the user-facing version, for example `1.0.0`. The tag will be `v` + this value.
   - `tidemark.versionCode`: one more than the last released code. GitHub and Play share this sequence, and it only goes up.
2. **Write the release notes** in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, plus the French one in `fr-FR/changelogs/` for the store. The GitHub release body is the en-US file. Play limits notes to 500 characters.
3. **Merge through a pull request** and wait for CI to pass on `main`.
4. **Tag the merge commit on `main` and push the tag:**

   ```sh
   git switch main && git pull --ff-only
   git tag -a v1.0.0 -m "Tidemark 1.0.0"
   git push origin v1.0.0
   ```

5. **Watch the Release workflow** in the Actions tab. It fails before signing if the tag, version code or release notes are wrong.

**Pre-releases.** A tag containing `-rc` (for example `v1.0.0-rc1`, with `tidemark.versionName=1.0.0-rc1`) becomes a pre-release. It still uses up a versionCode, so the final `v1.0.0` needs the next code.

**If the workflow fails.** Nothing is published until the **Create GitHub Release** step. Fix the cause on `main`, delete the tag (`git push --delete origin v1.0.0` and `git tag -d v1.0.0`), and tag again. Once a release exists, never reuse its tag or versionCode; release a new version instead.

### Verifying a release

Each run writes the certificate fingerprint and checksums to the job summary. To check the published files:

```sh
gh release download v1.0.0 --repo reddevsdotio/tidemark --dir /tmp/tidemark-v1.0.0
cd /tmp/tidemark-v1.0.0 && sha256sum -c SHA256SUMS
scripts/verify-signature.sh /tmp/tidemark-v1.0.0/tidemark-1.0.0.apk '<fingerprint>'
scripts/check-permissions.sh /tmp/tidemark-v1.0.0/tidemark-1.0.0.apk
```

Then install through Obtainium by adding `https://github.com/reddevsdotio/tidemark`. It picks the `.apk` asset. After a later release, check that Obtainium offers the update and installs it over the old version.

## Google Play: Play App Signing with our own key

Google Play must sign with the same key as GitHub Releases, so users can move between channels. Tidemark's Play app was set up this way on 25 November 2026, before its first upload. These are the steps, for reference or for another app.

1. **Check which key Play holds.** The Play Console generates an app signing key when the app is created. Open **Test and release → App integrity → Play app signing** (the App signing page) and compare the classical key's SHA-256 certificate fingerprint with the one in [Signing certificate](#signing-certificate). If they differ, replace the key before the first upload. After a release, only a key upgrade is possible, and older Android versions keep the old key.
2. **Choose to provide the key.** Click **Change key**, then choose **Provide a copy of your app signing key**. Don't pick a key upgrade.
3. **Run PEPK.** Download Google's encryption key and `pepk.jar` from that dialog, and run PEPK on the machine that holds the keystore. PEPK reads the password from a terminal prompt; without a terminal, pass it with `--keystore-pass`:

   ```sh
   java -jar pepk.jar --keystore=tidemark-release.jks --alias=tidemark-release \
     --output=tidemark-play-key.zip --include-cert --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```

   The zip holds the private key encrypted for Google and the certificate. `unzip -p tidemark-play-key.zip certificate.pem | openssl x509 -noout -fingerprint -sha256` shows the fingerprint before you upload.
4. **Upload, save and check.** Upload the zip as the encrypted private key and leave the optional upload key empty. Save, then check that the App signing page shows our fingerprint. Delete the zip and the downloaded files.
5. **Upload the AAB.** The AAB on each GitHub Release is signed with the same key, so it can be uploaded as is. Play accepts the app signing key as the upload key. Registering a separate upload key later would need a change to `release.yml`, since the AAB must then be signed with that key.

## Store graphics

The Play listing graphics live in `fastlane/metadata/android/<locale>/images/`: `icon.png` (512 x 512), `featureGraphic.png` (1024 x 500) and seven captioned `phoneScreenshots/` (1080 x 1920), in en-US and fr-FR. They are generated, not drawn by hand:

```sh
scripts/build-store-graphics.py
```

The script runs `StoreScreenshotTest` with `TIDEMARK_STORE_OUT` set, which renders the app screens on the JVM with the static Shoreline palette and sample data, then frames them with captions in a headless Chrome. Set `CHROME` if Chrome or Chromium isn't on `PATH`. The test is skipped in normal test runs. Rebuild the graphics after a visible UI change, and upload them in **Grow users → Store presence → Main store listing**. The captions are in the script; the sample data is in `DashboardSamples`, `DetailSamples` and the test itself.

## Signing certificate

The release key's certificate fingerprint. Every release must match it.

| Field | Value |
| --- | --- |
| SHA-256 | `BF:5F:CB:DF:21:A5:BC:9E:C6:75:37:FD:02:6E:E2:99:46:80:BE:72:60:14:CB:56:23:14:38:93:B2:A6:ED:93` |
| Created | 25 November 2026, valid until 12 April 2054 (RSA 4096, PKCS12) |
| Alias | `tidemark-release` |

## Troubleshooting

| Failure | Cause and fix |
| --- | --- |
| `tag 'vX' does not match tidemark.versionName` | Tag and `gradle.properties` disagree. Delete the tag, fix whichever is wrong, tag again. |
| `versionCode N must be greater than the highest published versionCode M` | Bump `tidemark.versionCode` above M. |
| `release vX has no versionCode asset` | A release was made outside this workflow. Attach a `versionCode` file holding its code: `printf '%s\n' <code> > versionCode && gh release upload vX versionCode`. |
| `release notes ... are missing` | Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. |
| `Missing signing secrets` | One of the four secrets is unset, or set on the wrong environment. |
| `The keystore does not open...` | Wrong password or alias, or the base64 was wrapped or truncated. Re-encode with `base64 -w0`. |
| `certificate ... does not match the expected` | The keystore in the secrets is not the release key, or `TIDEMARK_CERT_SHA256` is wrong. Stop and check against the offline backup before anything else. |
| `permissions outside the allow-list` | A dependency merged in a new permission. Remove it in `AndroidManifest.xml` with `tools:node="remove"`, or drop the dependency. Never widen the allow-list for a network permission. |
