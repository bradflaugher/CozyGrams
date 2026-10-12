#!/usr/bin/env python3
"""Checks fastlane/metadata/android against Google Play's listing limits.

    python3 .github/scripts/check_store_listing.py

Play rejects a whole edit when one part of the listing breaks its rules, and
CI only uploads from main, so pull requests run this first. Standard library
only. The limits are Play's (support.google.com/googleplay/android-developer/
answer/9866151 and 9859152):

- title 30, short description 80, full description 4000, release notes 500
  characters
- icon: 512 x 512 PNG, at most 1 MB
- feature graphic: 1024 x 500, JPEG or PNG without alpha
- TV banner: 1280 x 720, JPEG or PNG without alpha
- screenshots: JPEG or PNG without alpha, at most 8 MB and 8 per device type,
  the long side at most twice the short one; phone sides 320 to 3840 px,
  tablet sides 1080 to 7680 px, TV landscape
"""

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "fastlane/metadata/android"

TEXT_LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "changelogs/default.txt": 500,
}
# Screenshot folders and their allowed side lengths in pixels.
SCREENSHOTS = {
    "phoneScreenshots": (320, 3840),
    "sevenInchScreenshots": (1080, 7680),
    "tenInchScreenshots": (1080, 7680),
    "tvScreenshots": (320, 3840),
    "wearScreenshots": (384, 3840),
}
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}
MB = 1024 * 1024

errors: list[str] = []


def error(path: Path, message: str) -> None:
    errors.append(f"::error file={path.relative_to(ROOT)}::{message}")


def image_info(path: Path) -> tuple[str, int, int, bool]:
    """Returns (format, width, height, has_alpha) from the file header."""
    data = path.read_bytes()
    if data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR":
        width, height, _depth, colour = struct.unpack(">IIBB", data[16:26])
        # Colour types 4 and 6 carry an alpha channel; a tRNS chunk adds one
        # to the others.
        return "png", width, height, colour in (4, 6) or b"tRNS" in data[:data.find(b"IDAT")]
    if data[:2] == b"\xff\xd8":
        i = 2
        while i + 9 < len(data):
            if data[i] != 0xFF:
                i += 1
                continue
            marker = data[i + 1]
            # Start-of-frame markers (baseline, progressive, ...) hold the size.
            if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                height, width = struct.unpack(">HH", data[i + 5:i + 9])
                return "jpeg", width, height, False
            i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    return "unknown", 0, 0, False


def check_text(lang: Path) -> None:
    for name, limit in TEXT_LIMITS.items():
        path = lang / name
        if not path.is_file():
            continue
        # Play counts characters without the trailing newline.
        length = len(path.read_text(encoding="utf-8").rstrip("\n"))
        if length > limit:
            error(path, f"{length} characters; Google Play allows {limit}")


def check_fixed(path: Path, size: tuple[int, int], alpha_ok: bool, max_bytes: int) -> None:
    if not path.is_file():
        return
    kind, width, height, alpha = image_info(path)
    if kind == "unknown":
        error(path, "not a PNG or JPEG")
        return
    if (width, height) != size:
        error(path, f"{width}x{height}; Google Play wants {size[0]}x{size[1]}")
    if alpha and not alpha_ok:
        error(path, "has an alpha channel; Google Play wants it opaque")
    if path.stat().st_size > max_bytes:
        error(path, f"{path.stat().st_size / MB:.1f} MB; Google Play allows {max_bytes / MB:.0f} MB")


def check_screenshots(folder: Path, sides: tuple[int, int]) -> None:
    shots = sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES)
    if len(shots) > 8:
        error(folder, f"{len(shots)} screenshots; Google Play allows 8 per device type")
    for path in shots:
        kind, width, height, alpha = image_info(path)
        if kind == "unknown":
            error(path, "not a PNG or JPEG")
            continue
        short, long = sorted((width, height))
        if short < sides[0] or long > sides[1]:
            error(path, f"{width}x{height}; sides must be {sides[0]} to {sides[1]} px")
        if long > 2 * short:
            error(path, f"{width}x{height}; the long side may be at most twice the short one")
        if folder.name == "tvScreenshots" and width < height:
            error(path, f"{width}x{height}; TV screenshots are landscape")
        if alpha:
            error(path, "has an alpha channel; Google Play wants screenshots opaque")
        if path.stat().st_size > 8 * MB:
            error(path, f"{path.stat().st_size / MB:.1f} MB; Google Play allows 8 MB")


def main() -> int:
    for lang in sorted(p for p in METADATA.iterdir() if p.is_dir()):
        check_text(lang)
        images = lang / "images"
        if not images.is_dir():
            continue
        check_fixed(images / "icon.png", (512, 512), alpha_ok=True, max_bytes=1 * MB)
        for suffix in (".png", ".jpg", ".jpeg"):
            check_fixed(images / f"featureGraphic{suffix}", (1024, 500), alpha_ok=False, max_bytes=15 * MB)
            check_fixed(images / f"tvBanner{suffix}", (1280, 720), alpha_ok=False, max_bytes=15 * MB)
        for name, sides in SCREENSHOTS.items():
            if (images / name).is_dir():
                check_screenshots(images / name, sides)
    for line in errors:
        print(line)
    if not errors:
        print("Store listing is within Google Play's limits")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
