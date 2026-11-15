#!/usr/bin/env python3
"""Adds the texts the last build found in the code (its .stringsdata files) to Localizable.xcstrings.

Xcode does this itself in the IDE; on the command line run it after a build:
  python3 tools/sync-strings.py build/dd Appkin/Localizable.xcstrings
"""
import glob
import json
import sys

derived, catalog_path = sys.argv[1], sys.argv[2]
catalog = json.load(open(catalog_path))
strings = catalog["strings"]
for path in glob.glob(f"{derived}/**/*.stringsdata", recursive=True):
    for entry in json.load(open(path)).get("tables", {}).get("Localizable", []):
        key = entry["key"]
        if key and key not in strings:
            strings[key] = {"localizations": {"en": {"stringUnit": {"state": "translated", "value": key}}}}
catalog["strings"] = dict(sorted(strings.items()))
json.dump(catalog, open(catalog_path, "w"), indent=2, ensure_ascii=False)
print(len(strings), "texts")
