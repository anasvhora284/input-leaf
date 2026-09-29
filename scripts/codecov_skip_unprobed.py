#!/usr/bin/env python3
"""Write Codecov JSON that skips JaCoCo lines with no instruction counters.

Codecov's Jacoco parser has no setting for a line that produced no probes.
An added source line missing from the report is still a patch miss, and a
line uploaded by another job as a miss stays a miss. Coverage -1 is
Codecov's skipped line and is left out of the patch denominator. It also
wins a merge, so this file is uploaded only with the JVM report: a line
the JVM report did not probe is skipped, including when the Android report
lists it as uncovered. A line with a missed instruction or a fully missed
branch is omitted here and still fails the 100% patch target.
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def skipped_lines(report_path: Path, root: Path) -> dict[str, dict[str, int]]:
    coverage: dict[str, dict[str, int]] = {}
    tree = ET.parse(report_path)
    for package in tree.getroot().iter("package"):
        package_name = package.attrib.get("name", "")
        for source in package.iter("sourcefile"):
            probed: set[int] = set()
            for line in source.iter("line"):
                counters = [
                    int(line.attrib.get(name, "0"))
                    for name in ("mi", "ci", "mb", "cb")
                ]
                if any(counters):
                    probed.add(int(line.attrib["nr"]))
            path = resolve_source(root, package_name, source.attrib["name"])
            if path is None:
                continue
            line_count = len(path.read_text(encoding="utf-8").splitlines())
            skipped = {
                str(number): -1
                for number in range(1, line_count + 1)
                if number not in probed
            }
            if skipped:
                coverage[path.relative_to(root).as_posix()] = skipped
    return coverage


def resolve_source(root: Path, package_name: str, filename: str) -> Path | None:
    relative = Path(package_name) / filename
    matches = [
        path
        for path in root.glob(f"**/{relative.as_posix()}")
        if "build" not in path.parts
    ]
    main = [path for path in matches if "src/main" in path.as_posix()]
    found = main or matches
    if len(found) == 1:
        return found[0]
    return None


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} report.xml skipped.json")
    report_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    coverage = skipped_lines(report_path, Path.cwd())
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps({"coverage": coverage}, separators=(",", ":")),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
