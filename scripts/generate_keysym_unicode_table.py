#!/usr/bin/env python3
"""Generate KeysymUnicodeTableData.kt from the official X11 keysym → Unicode table."""

from __future__ import annotations

import re
import sys
from pathlib import Path

KEYSYMS_URL = "https://www.cl.cam.ac.uk/~mgk25/ucs/keysyms.txt"
PATTERN = re.compile(r"^0x([0-9A-Fa-f]+)\s+U([0-9A-Fa-f]+)\s+(\S)")


def parse_keysyms(text: str) -> dict[int, int]:
    entries: dict[int, int] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = PATTERN.match(line)
        if not match:
            continue
        keysym = int(match.group(1), 16)
        code_point = int(match.group(2), 16)
        status = match.group(3)
        if code_point == 0 or status in {"f", "r"}:
            continue
        entries.setdefault(keysym, code_point)
    return entries


def kotlin_char_literal(code_point: int) -> str:
    if code_point > 0xFFFF:
        return f"String(Character.toChars({code_point}))"
    char = chr(code_point)
    if char == "\\":
        return '"\\\\"'
    if char == '"':
        return '"\\""'
    if char == "\n":
        return '"\\n"'
    if char == "\t":
        return '"\\t"'
    if char == "\r":
        return '"\\r"'
    if ord(char) < 0x20 or ord(char) == 0x7F:
        return f'"\\u{code_point:04x}"'
    return f'"{char}"'


def render(entries: dict[int, int]) -> str:
    lines = [
        "package com.inputleaf.android.inject",
        "",
        "// AUTO-GENERATED from https://www.cl.cam.ac.uk/~mgk25/ucs/keysyms.txt",
        "// Do not edit manually. Regenerate with scripts/generate_keysym_unicode_table.py",
        "internal object KeysymUnicodeTableData {",
        "    internal val TABLE: Map<Int, String> = mapOf(",
    ]
    sorted_keys = sorted(entries)
    for index, keysym in enumerate(sorted_keys):
        literal = kotlin_char_literal(entries[keysym])
        comma = "," if index < len(sorted_keys) - 1 else ""
        lines.append(f"        0x{keysym:x} to {literal}{comma}")
    lines.extend(
        [
            "    )",
            "}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else repo_root / "scripts" / "keysyms.txt"
    output = repo_root / "app/src/main/java/com/inputleaf/android/inject/KeysymUnicodeTableData.kt"

    if not source.exists():
        print(f"Missing keysym source file: {source}", file=sys.stderr)
        return 1

    entries = parse_keysyms(source.read_text(encoding="utf-8"))
    output.write_text(render(entries), encoding="utf-8")
    print(f"Generated {len(entries)} entries -> {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
