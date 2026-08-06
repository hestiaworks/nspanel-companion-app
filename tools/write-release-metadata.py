#!/usr/bin/env python3
"""Write deterministic updater metadata for a signed NSPanel APK."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    if args.version_code <= 0:
        parser.error("version code must be positive")
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    payload = {
        "application_id": "dev.hacompanion.panel",
        "apk": args.apk.name,
        "sha256": digest,
        "size": args.apk.stat().st_size,
        "version": args.version,
        "version_code": args.version_code,
        "channel": "prerelease" if "-" in args.version else "stable",
        "minimum_android_api": 26,
        "abi": "arm64-v8a",
    }
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
