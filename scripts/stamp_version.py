#!/usr/bin/env python3
"""Stamp one release version across every build manifest before CI builds.

Usage: python scripts/stamp_version.py <version> [versionCode]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def stamp_cargo(version: str) -> None:
    path = ROOT / "Desktop/src-tauri/Cargo.toml"
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    in_package = False
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("["):
            in_package = stripped == "[package]"
            continue
        if in_package and stripped.startswith("version") and "=" in stripped:
            lines[index] = re.sub(r'(^\s*version\s*=\s*")[^"]*(")', rf"\g<1>{version}\g<2>", line)
            path.write_text("".join(lines), encoding="utf-8")
            return
    raise SystemExit("Could not find [package] version in Desktop/src-tauri/Cargo.toml")


def stamp_json(relative: str, version: str) -> None:
    path = ROOT / relative
    data = json.loads(path.read_text(encoding="utf-8"))
    data["version"] = version
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def stamp_android(version: str, version_code: str) -> None:
    path = ROOT / "Android/app/build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    text, code_count = re.subn(r"(?m)^(\s*versionCode\s*=\s*)\d+", rf"\g<1>{version_code}", text, count=1)
    text, name_count = re.subn(r'(?m)^(\s*versionName\s*=\s*)"[^"]*"', rf'\g<1>"{version}"', text, count=1)
    if code_count != 1 or name_count != 1:
        raise SystemExit("Could not update versionCode/versionName in build.gradle.kts")
    path.write_text(text, encoding="utf-8")


def stamp_iss(version: str) -> None:
    for name in ["installer/gloom.iss"]:
        path = ROOT / name
        if path.exists():
            text = path.read_text(encoding="utf-8")
            text = re.sub(r'(?m)^#define\s+MyAppVersion\s+"[^"]*"', f'#define MyAppVersion "{version}"', text)
            path.write_text(text, encoding="utf-8")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: stamp_version.py <version> [versionCode]", file=sys.stderr)
        return 2

    version = sys.argv[1].strip().removeprefix("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        print(f"Invalid semantic version: {version}", file=sys.stderr)
        return 2

    version_code = sys.argv[2] if len(sys.argv) > 2 else version.split(".")[2]
    if not version_code.isdigit():
        print(f"Invalid versionCode: {version_code}", file=sys.stderr)
        return 2

    stamp_cargo(version)
    stamp_json("Desktop/src-tauri/tauri.conf.json", version)
    stamp_json("Desktop/package.json", version)
    stamp_android(version, version_code)
    stamp_iss(version)
    print(f"Stamped release version {version} (versionCode {version_code})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
