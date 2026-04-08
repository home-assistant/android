#!/usr/bin/env python3
"""
Verify that all dependency licenses are compatible
with the project's Apache-2.0 license.
"""

import json
import sys
from fnmatch import fnmatch
from pathlib import Path
from typing import NamedTuple

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
# identifier.
EXCEPTED_LIBRARIES = [
    # Chromium publishes a BSD-style license but only as a URL
    "org.chromium.net:*",
    # https://github.com/Dimezis/BlurView/issues/259
    "com.github.Dimezis:BlurView",
]


class LicenseViolation(NamedTuple):
    library_id: str
    version: str
    license_id: str
    flavor: str


def is_excepted(library_id: str) -> bool:
    return any(fnmatch(library_id, pattern) for pattern in EXCEPTED_LIBRARIES)


def check_licenses(
    json_path: str, flavor: str
) -> tuple[list[LicenseViolation], dict[str, list[str]]]:
    path = Path(json_path)
    if not path.exists():
        print(f"::error::aboutlibraries JSON not found at {json_path}")
        sys.exit(1)

    data = json.loads(path.read_text())
    violations: list[LicenseViolation] = []
    library_licenses: dict[str, list[str]] = {}

    for library in data["libraries"]:
        lib_id = library["uniqueId"]
        licenses = library.get("licenses", [])
        library_licenses[lib_id] = licenses

        if is_excepted(lib_id):
            continue

        version = library.get("artifactVersion", "unknown")
        if not licenses:
            violations.append(
                LicenseViolation(lib_id, version, "NONE", flavor)
            )
            continue
        for license_id in licenses:
            if license_id not in ALLOWED_LICENSES:
                violations.append(
                    LicenseViolation(
                        lib_id, version, license_id, flavor
                    )
                )

    return violations, library_licenses


def check_stale_exceptions(
    all_library_licenses: dict[str, list[str]],
) -> list[str]:
    """Check for EXCEPTED_LIBRARIES entries that are no longer needed.

    An exception is stale when:
    - It no longer matches any dependency, or
    - All matched libraries now have approved licenses.
    """
    stale = []
    for pattern in EXCEPTED_LIBRARIES:
        matched = {
            lib_id: licenses
            for lib_id, licenses in all_library_licenses.items()
            if fnmatch(lib_id, pattern)
        }
        if not matched:
            stale.append(f"{pattern} (no longer a dependency)")
        elif all(
            licenses and all(lic in ALLOWED_LICENSES for lic in licenses)
            for licenses in matched.values()
        ):
            libs = ", ".join(sorted(matched))
            stale.append(f"{pattern} (approved license detected for {libs})")
    return stale


def main() -> None:
    if len(sys.argv) < 3 or len(sys.argv) % 2 == 0:
        print(
            f"Usage: {sys.argv[0]}"
            " <json_path> <flavor> [<json_path> <flavor> ...]"
        )
        sys.exit(1)

    all_violations: list[LicenseViolation] = []
    all_library_licenses: dict[str, list[str]] = {}
    pairs = list(zip(sys.argv[1::2], sys.argv[2::2]))

    for json_path, flavor in pairs:
        violations, library_licenses = check_licenses(json_path, flavor)
        all_violations.extend(violations)
        all_library_licenses.update(library_licenses)

    stale_exceptions = check_stale_exceptions(all_library_licenses)
    has_errors = False

    if stale_exceptions:
        has_errors = True
        print()
        print("❌ Stale entries in EXCEPTED_LIBRARIES (no longer needed):")
        for pattern in stale_exceptions:
            print(f"  - {pattern}")
        print()
        print(
            "Please remove them from EXCEPTED_LIBRARIES"
            " in .github/scripts/check_licenses.py"
        )
        stale_list = ", ".join(stale_exceptions)
        print(
            f"::error title=⛔ Stale Exceptions::{len(stale_exceptions)} stale"
            f" EXCEPTED_LIBRARIES entries: {stale_list}"
        )

    if all_violations:
        has_errors = True
        print()
        print("❌ Libraries with incompatible or unknown licenses:")
        for v in all_violations:
            print(
                f"  - {v.library_id} ({v.version})"
                f" — license: {v.license_id}"
                f" (flavor: {v.flavor})"
            )
        print()
        print("To fix this, either:")
        print(
            "  1. Replace the dependency with one"
            " using a compatible license"
        )
        print(
            "  2. Add the library to EXCEPTED_LIBRARIES in"
            " .github/scripts/check_licenses.py"
            " after manual review"
        )
        print()
        print(f"Allowed licenses: {', '.join(sorted(ALLOWED_LICENSES))}")
        bad_libs = ", ".join(
            sorted({v.library_id for v in all_violations})
        )
        count = len(all_violations)
        print(
            f"::error title=⛔ License Check Failed::"
            f"{count} incompatible or unknown"
            f" licenses found: {bad_libs}"
        )

    if has_errors:
        sys.exit(1)
    else:
        for _, flavor in pairs:
            print(f"✔️ All dependency licenses are compatible ({flavor})")


if __name__ == "__main__":
    main()
