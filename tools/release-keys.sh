#!/usr/bin/env bash
# Checks that bundled configuration API keys are supplied the way app/build.gradle.kts reads them:
# APP_CONFIG_KEYS_FILE, APP_CONFIG_KEYS_BASE64 or TMDB_API_KEY in the environment, or the Gradle property
# configApiKeysFile (ORG_GRADLE_PROJECT_configApiKeysFile, $GRADLE_USER_HOME/gradle.properties (default
# ~/.gradle/gradle.properties) or the project's gradle.properties). Source this file; it defines functions only.

# Prints the last configApiKeysFile value in a Java properties file, if any.
_gradle_properties_value() {
  [ -r "$1" ] || return 0
  sed -nE 's/^[[:space:]]*configApiKeysFile[[:space:]]*[=:][[:space:]]*(.*[^[:space:]])[[:space:]]*$/\1/p' "$1" | tail -1
}

# Prints the Gradle property configApiKeysFile as Gradle would resolve it (environment, then user home, then project).
gradle_config_api_keys_file() {
  local value="${ORG_GRADLE_PROJECT_configApiKeysFile:-}" root="${1:-.}"
  [ -n "$value" ] || value=$(_gradle_properties_value "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties")
  [ -n "$value" ] || value=$(_gradle_properties_value "$root/gradle.properties")
  printf '%s\n' "$value"
}

# Returns 0 when keys are supplied, 1 when none are, 2 when a referenced file doesn't exist (a message goes to stderr).
check_bundled_keys() {
  local root="${1:-.}" file
  file="${APP_CONFIG_KEYS_FILE:-}"
  [ -n "$file" ] || file=$(gradle_config_api_keys_file "$root")
  if [ -n "$file" ]; then
    case "$file" in /*) ;; *) file="$root/$file" ;; esac
    [ -f "$file" ] || { echo "The configuration API-key file $file does not exist." >&2; return 2; }
    return 0
  fi
  [ -n "${APP_CONFIG_KEYS_BASE64:-}" ] || [ -n "${TMDB_API_KEY:-}" ]
}
