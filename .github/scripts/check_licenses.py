#!/usr/bin/env python3
"""Verify that all dependency licenses are compatible with the project's Apache-2.0 license."""

import json
import sys
from fnmatch import fnmatch
from pathlib import Path

# Licenses compatible with Apache-2.0
ALLOWED_LICENSES = {
    "Apache-2.0",
    "MIT",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "ISC",
    "ASDKL",  # Android Software Development Kit License
    "PCSDKToS",  # Play Core Software Development Kit Terms of Service
}

# Libraries whose licenses have been manually reviewed and approved.
# These are used when the library does not publish a standard SPDX license
# identifier (e.g. Chromium publishes a BSD-style license but only as a URL).
EXCEPTED_LIBRARIES = [
    "org.chromium.net:*",
]


def is_excepted(library_id: str) -> bool:
    return any(fnmatch(library_id, pattern) for pattern in EXCEPTED_LIBRARIES)


def check_licenses(json_path: str, flavor: str) -> list[str]:
    path = Path(json_path)
    if not path.exists():
        print(f"::error::aboutlibraries JSON not found at {json_path}")
        sys.exit(1)

    data = json.loads(path.read_text())
    violations = []

    for library in data["libraries"]:
        lib_id = library["uniqueId"]
        if is_excepted(lib_id):
            continue

        version = library.get("artifactVersion", "unknown")
        for license_id in library.get("licenses", []):
            if license_id not in ALLOWED_LICENSES:
                violations.append((lib_id, version, license_id, flavor))

    return violations


def main() -> None:
    if len(sys.argv) < 3 or len(sys.argv) % 2 == 0:
        print(f"Usage: {sys.argv[0]} <json_path> <flavor> [<json_path> <flavor> ...]")
        sys.exit(1)

    all_violations = []
    pairs = list(zip(sys.argv[1::2], sys.argv[2::2]))

    for json_path, flavor in pairs:
        violations = check_licenses(json_path, flavor)
        all_violations.extend(violations)

    if all_violations:
        print()
        print("❌ Libraries with incompatible or unknown licenses:")
        for lib_id, version, license_id, flavor in all_violations:
            print(f"  - {lib_id} ({version}) — license: {license_id} (flavor: {flavor})")
        print()
        print("To fix this, either:")
        print("  1. Replace the dependency with one using a compatible license")
        print(
            "  2. Add the library to EXCEPTED_LIBRARIES in .github/scripts/check_licenses.py"
            " after manual review"
        )
        print()
        print(f"Allowed licenses: {', '.join(sorted(ALLOWED_LICENSES))}")
        bad_libs = ", ".join(sorted({lib_id for lib_id, _, _, _ in all_violations}))
        count = len(all_violations)
        print(
            f"::error title=⛔ License Check Failed::{count} incompatible or unknown"
            f" licenses found: {bad_libs}"
        )
        sys.exit(1)
    else:
        for _, flavor in pairs:
            print(f"✔️ All dependency licenses are compatible ({flavor})")


if __name__ == "__main__":
    main()
