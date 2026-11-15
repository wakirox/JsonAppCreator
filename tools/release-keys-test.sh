#!/usr/bin/env bash
# Tests the bundled-keys guard with temporary HOME / GRADLE_USER_HOME directories: tools/release-keys-test.sh
set -u
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=tools/release-keys.sh
. "$here/release-keys.sh"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
fail=0
expect() { # name expected-status command...
  local name=$1 want=$2; shift 2
  ( unset APP_CONFIG_KEYS_FILE APP_CONFIG_KEYS_BASE64 TMDB_API_KEY ORG_GRADLE_PROJECT_configApiKeysFile GRADLE_USER_HOME
    export HOME="$tmp/home"; "$@" ) 2>/dev/null
  local got=$?
  if [ "$got" -eq "$want" ]; then echo "ok   $name"; else echo "FAIL $name (want $want, got $got)"; fail=1; fi
}
mkdir -p "$tmp/home/.gradle" "$tmp/guh" "$tmp/proj"
echo "k=v" > "$tmp/keys.properties"

expect "nothing set" 1 check_bundled_keys "$tmp/proj"
expect "TMDB env" 0 env TMDB_API_KEY=x bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/proj'"
expect "BASE64 env" 0 env APP_CONFIG_KEYS_BASE64=eA== bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/proj'"
expect "file env, exists" 0 env APP_CONFIG_KEYS_FILE="$tmp/keys.properties" bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/proj'"
expect "file env, missing" 2 env APP_CONFIG_KEYS_FILE="$tmp/nope" bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/proj'"
echo "configApiKeysFile=$tmp/keys.properties" > "$tmp/home/.gradle/gradle.properties"
expect "home gradle property, exists" 0 check_bundled_keys "$tmp/proj"
echo "configApiKeysFile=$tmp/nope" > "$tmp/home/.gradle/gradle.properties"
expect "home gradle property, missing" 2 check_bundled_keys "$tmp/proj"
rm "$tmp/home/.gradle/gradle.properties"
echo "configApiKeysFile = $tmp/keys.properties" > "$tmp/guh/gradle.properties"
expect "GRADLE_USER_HOME property" 0 env GRADLE_USER_HOME="$tmp/guh" bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/proj'"
expect "ignored without GRADLE_USER_HOME" 1 check_bundled_keys "$tmp/proj"
echo "configApiKeysFile=../keys.properties" > "$tmp/proj/gradle.properties"
expect "project property, relative" 0 check_bundled_keys "$tmp/proj"
echo "configApiKeysFile=$tmp/keys.properties" > "$tmp/proj/gradle.properties"
expect "ORG_GRADLE_PROJECT env" 0 env ORG_GRADLE_PROJECT_configApiKeysFile="$tmp/keys.properties" bash -c ". '$here/release-keys.sh'; check_bundled_keys '$tmp/none'"
echo "configApiKeysFile=$tmp/keys.properties" > "$tmp/proj/local.properties"
rm "$tmp/proj/gradle.properties"
expect "local.properties is not read" 1 check_bundled_keys "$tmp/proj"
exit $fail
