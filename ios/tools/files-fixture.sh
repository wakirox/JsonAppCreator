#!/bin/bash
# Puts a test .appkin.json into the booted simulator's Files "On My iPhone" storage, for testOpenInAppkinFromFiles.
# usage: files-fixture.sh <simulator udid>
set -e
udid="$1"
groups=~/Library/Developer/CoreSimulator/Devices/$udid/data/Containers/Shared/AppGroup
for g in "$groups"/*; do
  if [ "$(plutil -extract MCMMetadataIdentifier raw "$g/.com.apple.mobile_container_manager.metadata.plist" 2>/dev/null)" = "group.com.apple.FileProvider.LocalStorage" ]; then
    cat > "$g/File Provider Storage/hello.appkin.json" <<JSON
{"schemaVersion":3,"name":"Opened From Files","icon":"📂","version":"1","author":"Test","mainView":{"id":"l0","type":"layout","views":[{"type":"text","label":"Hello from a file"}]}}
JSON
  fi
done
