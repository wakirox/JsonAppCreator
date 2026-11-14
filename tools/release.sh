#!/usr/bin/env bash
# Signs and publishes an Appkin release on the owner's machine, where the signing key stays:
#
#   tools/release.sh v2.0              # build, sign, check, tag and publish the GitHub Release (v2.0.1 for a patch)
#   tools/release.sh v2.0 --dry-run    # build, sign and check only: nothing is tagged or published
#
# Only from a clean checkout of a commit on master that CI passed (master may have moved on since). It builds the minified APK and the AAB for Google
# Play, signed with the key in $APPKIN_KEYSTORE_PROPERTIES (storeFile, storePassword, keyAlias, keyPassword; never
# printed), verifies the APK's signature, and writes both with SHA-256 checksums to dist/<tag>/. Then it pushes the tag
# (CI's Release workflow tests it again) and creates the GitHub Release with the APK, as the Wakirox Bot.
# Uploading the AAB to Google Play is a separate step.
set -euo pipefail

tag="${1:-}"
dry_run=false
allow_missing_keys=false
for arg in "${@:2}"; do
  case "$arg" in
    --dry-run) dry_run=true ;;
    --allow-missing-keys) allow_missing_keys=true ;;
    *) echo "Usage: $0 vMAJOR.MINOR[.PATCH] [--dry-run] [--allow-missing-keys]" >&2; exit 2 ;;
  esac
done
[[ "$tag" =~ ^v([0-9]+)\.([0-9]+)(\.([0-9]+))?$ ]] || { echo "Usage: $0 vMAJOR.MINOR[.PATCH] [--dry-run] [--allow-missing-keys]" >&2; exit 2; }
major=$((10#${BASH_REMATCH[1]})); minor=$((10#${BASH_REMATCH[2]})); patch=$((10#${BASH_REMATCH[4]:-0}))
(( minor <= 999 && patch <= 999 )) || { echo "Minor and patch must be 0 to 999" >&2; exit 2; }
version_code=$((major * 1000000 + minor * 1000 + patch))

cd "$(git rev-parse --show-toplevel)"
repo=wakirox/JsonAppCreator
properties="${APPKIN_KEYSTORE_PROPERTIES:-$HOME/.android/keys/rainy-keystore.properties}"
export PATH="$HOME/.local/bin:$PATH"

# The checked-out commit, which must be on master (not necessarily its newest) and have passed CI there.
git fetch -q origin master --tags
[ -z "$(git status --porcelain)" ] || { echo "The checkout has changes: release from a clean one" >&2; exit 1; }
commit=$(git rev-parse HEAD)
git merge-base --is-ancestor "$commit" origin/master || { echo "$commit isn't on origin/master" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" > /dev/null && { echo "$tag already exists" >&2; exit 1; }
ci=$(gh run list --repo "$repo" --workflow CI --commit "$commit" --json conclusion -q '[.[] | select(.conclusion == "success")] | length')
[ "${ci:-0}" -gt 0 ] || { echo "CI hasn't passed on $commit yet" >&2; exit 1; }

# Signing values go to Gradle through the environment only.
if ! $allow_missing_keys && [ -z "${APP_CONFIG_KEYS_FILE:-}" ] && [ -z "${APP_CONFIG_KEYS_BASE64:-}" ] && [ -z "${TMDB_API_KEY:-}" ] \
   && ! grep -q '^configApiKeysFile=' local.properties 2>/dev/null; then
  echo "Bundled configuration keys are not set (APP_CONFIG_KEYS_FILE / APP_CONFIG_KEYS_BASE64 / TMDB_API_KEY / local.properties configApiKeysFile)." >&2
  echo "Pass --allow-missing-keys if you intentionally want to build without bundled keys." >&2
  exit 1
fi
[ -r "$properties" ] || { echo "No signing properties at $properties" >&2; exit 1; }
value() { grep -m1 "^$1=" "$properties" | cut -d= -f2-; }
store_file=$(value storeFile)
[[ "$store_file" = /* ]] || store_file="$(dirname "$properties")/$store_file"
[ -r "$store_file" ] || store_file="$HOME/.android/keys/wakirox_keystore"
ANDROID_KEYSTORE_PATH="$store_file" ANDROID_KEYSTORE_PASSWORD="$(value storePassword)" \
ANDROID_KEY_ALIAS="$(value keyAlias)" ANDROID_KEY_PASSWORD="$(value keyPassword)" \
APP_VERSION_NAME="${tag#v}" APP_VERSION_CODE="$version_code" \
  ./gradlew -q :app:assembleRelease :app:bundleRelease

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
apksigner=$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -type f -name apksigner | sort -V | tail -1)
"$apksigner" verify "$apk"
signer=$("$apksigner" verify --print-certs "$apk" | grep -m1 'SHA-256 digest' | awk '{print $NF}')

out="dist/$tag"
mkdir -p "$out"
cp "$apk" "$out/Appkin-$tag.apk"
cp "$aab" "$out/Appkin-$tag.aab"
(cd "$out" && sha256sum "Appkin-$tag.apk" > "Appkin-$tag.apk.sha256" && sha256sum "Appkin-$tag.aab" > "Appkin-$tag.aab.sha256")
echo "Built $tag (versionCode $version_code) from $commit, signed by $signer, in $out"

if $dry_run; then
  echo "Dry run: nothing tagged or published"
  exit 0
fi

git -c user.name='wakimiao[bot]' -c user.email='339346785+wakimiao[bot]@users.noreply.github.com' tag -a "$tag" -m "Appkin $tag" "$commit"
git-bot-push origin "$tag"
gh-bot release create "$tag" "$out/Appkin-$tag.apk" "$out/Appkin-$tag.apk.sha256" \
  --repo "$repo" --title "Appkin $tag" --generate-notes --verify-tag
echo "Published https://github.com/$repo/releases/tag/$tag"
