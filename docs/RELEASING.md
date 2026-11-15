# Releasing Appkin

The signing key never leaves the owner's machine: GitHub has no signing secrets. A release
is signed and published there by `tools/release.sh`, from the same commit CI tested.

## What each part does

- **CI** (`.github/workflows/ci.yml`) tests every pull request and every push to `master`.
- **The release script** (`tools/release.sh`, on the owner's machine) builds the minified
  APK and the AAB for Google Play from a clean checkout of `origin/master`, signs them,
  verifies the APK's signature, writes both with SHA-256 checksums to `dist/<tag>/`, then
  pushes the tag and creates the GitHub Release with the APK, as the Wakirox Bot.
- **The Release workflow** (`.github/workflows/release.yml`) runs on that tag: it tests the
  tagged source again and builds the release variant, unsigned, with no secrets.
- **Google Play** gets the AAB from `dist/<tag>/` in a separate, deliberate step.

## Make a release

```sh
git fetch origin
git switch --detach <commit on master>   # usually origin/master
tools/release.sh v2.0 --dry-run          # build, sign and check; nothing published
tools/release.sh v2.0                    # the same, then tag and publish
```

The checked-out commit is released. The script refuses when the checkout has changes, when
the commit isn't on `master`, when the tag exists, or when CI hasn't passed on that commit.
Master may have moved on: merging never has to wait for a release. It reads the signing values from
`$APPKIN_KEYSTORE_PROPERTIES` (by default `~/.android/keys/rainy-keystore.properties`:
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and hands them to Gradle through
the environment only; they are never printed. API keys for the bundled examples come from
the machine's Gradle properties (`configApiKeysFile` in `$GRADLE_USER_HOME/gradle.properties`,
default `~/.gradle/gradle.properties`, or the project's `gradle.properties`), or from
`APP_CONFIG_KEYS_FILE`, `APP_CONFIG_KEYS_BASE64` or `TMDB_API_KEY`, as in any local build.
The script refuses to build when none is set or the referenced file is missing; `--allow-missing-keys`
overrides that for one run only (it is never stored). The check is in `tools/release-keys.sh`, tested
by `tools/release-keys-test.sh`.

## Version and tag strategy

Release tags are `vMAJOR.MINOR` or `vMAJOR.MINOR.PATCH`, for example `v2.0` or `v2.0.1`.
The `versionName` is the tag without its `v` (`2.0`), and the `versionCode` is (a missing
patch counts as 0):

```text
MAJOR * 1,000,000 + MINOR * 1,000 + PATCH
```

Minor and patch values must each be between 0 and 999. This encoding is deterministic
and keeps version order. Local builds default to `versionName` `1.0` and `versionCode`
`1`; `APP_VERSION_NAME` and `APP_VERSION_CODE` override them.

## Verify a downloaded release

Download the APK and its `.sha256` file from the same GitHub Release, then run:

```sh
sha256sum --check Appkin-v2.0.apk.sha256
apksigner verify --verbose --print-certs Appkin-v2.0.apk
```

Compare the certificate digest with a separately recorded trusted digest from a
known-good release, not only with information from the same download location.

## The key

The APK is signed with the same upload key as the owner's other apps; Google Play signs
what it distributes with its own app signing key (Play App Signing). Keep encrypted
backups of the keystore and its passwords in at least two separate places: **losing them
prevents updates signed as the existing app** (Play can reset an upload key; a key for
APKs downloaded from GitHub can't be replaced without breaking updates).
