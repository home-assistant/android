#!/usr/bin/env python3
"""Collect screenshot test failures for the PR comment posted by pr.yml.

Parses the validate*ScreenshotTest JUnit XML results, copies each failure's
reference, newly rendered, and diff images to `image_dir`, and writes a
markdown comment body showing them side by side to `body_path`. Writes nothing
when there is no failure.

Usage: collect_screenshot_failures.py <image_dir> <body_path>
"""
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Cap the failures shown (3 images each) to keep the comment readable, the run
# artifacts hold the full report.
MAX_FAILURES = 5

# Markdown table column title per XML failure message field, in display order.
IMAGE_FIELDS = {"Expected": "Reference", "Actual": "New", "Diff Image": "Diff"}

MARKER = "<!-- screenshot-test-diffs -->"
UPDATE_COMMAND = (
    "./gradlew :app:updateFullDebugScreenshotTest :wear:updateDebugScreenshotTest :common:updateDebugScreenshotTest"
)


def collect_failures():
    """Return (title, images per IMAGE_FIELDS key) per failure, from all modules' validate results."""
    failures = []
    for xml_path in sorted(Path(".").glob("*/build/test-results/validate*ScreenshotTest/TEST-*.xml")):
        for case in ET.parse(xml_path).getroot().iter("testcase"):
            failure = case.find("failure")
            if failure is None:
                continue
            message = f"{failure.get('message') or ''}\n{failure.text or ''}"
            # The engine reports "name_{device=spec:...}", the spec is noise in a title.
            name = re.sub(r"_\{.*\}$", "", case.get("name") or "")
            test_class = (case.get("classname") or "").rsplit(".", 1)[-1]
            title = f"{test_class} / {name}"
            difference = re.search(r"^Difference: (.+)$", message, re.MULTILINE)
            if difference:
                title += f" ({difference.group(1).strip()} difference)"
            images = {}
            for field in IMAGE_FIELDS:
                match = re.search(rf"^{field}: (.+)$", message, re.MULTILINE)
                if match and Path(match.group(1).strip()).is_file():
                    images[field] = Path(match.group(1).strip())
            failures.append((title, images))
    return failures


def write_comment(failures, image_dir, body_path):
    lines = [
        MARKER,
        "### ❌ Screenshot tests failed",
        "",
        f"{len(failures)} screenshot(s) differ from their reference, changed pixels are highlighted below.",
        "The full report is in the run artifacts. If the changes are intended, update the references with:",
        "",
        "```",
        UPDATE_COMMAND,
        "```",
    ]
    for index, (title, images) in enumerate(failures[:MAX_FAILURES], start=1):
        lines += ["", f"**{title}**", ""]
        if not images:
            lines += ["_No images available, see the run artifacts._"]
            continue
        cells = []
        for field, column in IMAGE_FIELDS.items():
            image = images.get(field)
            if image is None:
                cells.append("_missing_")
                continue
            copy = image_dir / f"failure-{index}-{column.lower()}.png"
            shutil.copy(image, copy)
            cells.append(f"![{column}]({copy})")
        lines += [
            "| " + " | ".join(IMAGE_FIELDS.values()) + " |",
            "| " + " | ".join("---" for _ in IMAGE_FIELDS) + " |",
            "| " + " | ".join(cells) + " |",
        ]
    if len(failures) > MAX_FAILURES:
        lines += ["", f"…and {len(failures) - MAX_FAILURES} more in the run artifacts."]
    body_path.write_text("\n".join(lines) + "\n")


def main():
    image_dir, body_path = Path(sys.argv[1]), Path(sys.argv[2])
    failures = collect_failures()
    if not failures:
        print("No screenshot test failures found")
        return
    image_dir.mkdir(parents=True, exist_ok=True)
    write_comment(failures, image_dir, body_path)
    print(f"Collected {len(failures)} failure(s)")


if __name__ == "__main__":
    main()
