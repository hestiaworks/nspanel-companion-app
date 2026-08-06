#!/usr/bin/env python3
"""Convert a SemVer release tag into a monotonically increasing Android code."""

from __future__ import annotations

import re
import sys

VERSION = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?$")
OFFSETS = {"alpha": 0, "beta": 30, "rc": 60}


def version_code(version: str) -> int:
    match = VERSION.fullmatch(version.strip())
    if not match:
        raise ValueError("Version must look like 1.2.3, 1.2.3-beta.1, or v1.2.3")
    major, minor, patch = map(int, match.group(1, 2, 3))
    if major > 2000 or minor > 99 or patch > 99:
        raise ValueError("Version exceeds the supported Android version-code range")
    channel, sequence = match.group(4), match.group(5)
    base = major * 1_000_000 + minor * 10_000 + patch * 100
    if not channel:
        return base + 99
    number = int(sequence)
    if number < 1 or number > 29:
        raise ValueError("Prerelease sequence must be between 1 and 29")
    return base + OFFSETS[channel] + number


if __name__ == "__main__":
    try:
        print(version_code(sys.argv[1]))
    except (IndexError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(2)
