#!/usr/bin/env python3
"""Checks fastlane/metadata/android against Google Play's listing rules.

    python3 .github/scripts/check_store_listing.py   (needs Pillow: apt install python3-pil)

Play rejects a whole edit when one part of the listing breaks its rules, and
CI only uploads from main, so pull requests run this first. The rules are Play's preview-asset and listing requirements
(support.google.com/googleplay/android-developer/answer/9866151 and 9859152):

- title (30), short description (80) and full description (4000) must be
  non-empty and within their limits; the title and short description carry
  no price or promotion keywords ("free", "no ads", "best", "#1", "sale",
  "download now", ...), or Play stops promoting the app (Play Console's
  store listing warning: "Should not use keywords that indicate price or
  promotion"); every changelog, default.txt or one per
  version code, at most 500 characters
- the default (en-US) listing must exist with an icon, a feature graphic
  and at least 2 screenshots (translations may borrow them); every image needs
  its exact supply name with a lower-case extension, and no animated PNGs
- icon: 512 x 512, 32-bit PNG (8-bit RGBA), at most 1 MB
- feature graphic 1024 x 500 and TV banner 1280 x 720: JPEG or 8-bit PNG
  without transparency, at most 15 MB
- screenshots: JPEG or 8-bit PNG without transparency, at most 8 MB and 8 per
  device type, at least 2 in all; phone sides 320 to 3840 px with the long side at most twice
  the short one; tablet sides 1080 to 3840 px (Play's tablet minimum, within
  its upload maximum); TV 16:9; Wear OS square
Play documents "24-bit PNG" for the opaque assets; it also takes 8-bit
palette PNGs without transparency (PokerSI's pixel-art listing is one), so
those pass too.

Every image is fully decoded with Pillow (dimensions are checked against the
asset's limits first, so a decompression bomb is refused before it inflates).
For PNGs the header is also read here, because Pillow doesn't report bit
depth or the exact colour type: only methods 0, an 8-bit RGB, palette (with
its PLTE) or RGBA image, no unknown critical chunks.
"""

import re
import struct
import sys
import zlib
from pathlib import Path

try:
    from PIL import Image as Pillow
except ImportError:
    sys.exit("Pillow is missing: sudo apt-get install python3-pil (or pip install pillow)")

ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "fastlane/metadata/android"

REQUIRED_TEXT = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
# Play's metadata guidelines: the title and short description must not
# suggest price, promotion, ranking, download counts or a call to action. Play
# flagged PokerSI's "No ads, no account." in its short description as exactly
# this (2026-10). The full description may state such facts plainly. The
# vocabulary is English: every listing here is en-US only, so a translation
# would need its own words added.
PROMO_FIELDS = ("title.txt", "short_description.txt")
PROMO_PATTERN = re.compile(
    r"\b(free|no[- ]ads?|ads?[- ]free|without ads|best|top|new|popular|sales?|"
    r"discounts?|cheap|deals?|bonus(?:es)?|offers?|cash ?back|award[- ]winning|"
    r"editor'?s'? choice|number one|million|downloads|"
    r"(?:download|install|play|try|get it) now)\b"
    r"|#\s?1\b"
    r"|\d+\s?% off\b"
    r"|[$€£¥₹]\s?\d|\d\s?(?:¢|[$€£¥₹])"
    r"|\b\d+(?:[.,]\d+)?\s?(?:usd|eur|gbp|dollars?|cents?)\b",
    re.IGNORECASE,
)
MB = 1024 * 1024
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}
# The listing's default language; it must carry the full set of graphics.
DEFAULT_LANGUAGE = "en-US"
# Screenshot folder: (shortest side, longest side, shape rule).
SCREENSHOTS = {
    "phoneScreenshots": (320, 3840, "2:1"),
    "sevenInchScreenshots": (1080, 3840, "2:1"),
    "tenInchScreenshots": (1080, 3840, "2:1"),
    "tvScreenshots": (320, 3840, "16:9"),
    "wearScreenshots": (384, 3840, "1:1"),
}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
# PNG colour type: samples per pixel.
PNG_CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
PNG_CRITICAL = {b"IHDR", b"PLTE", b"IDAT", b"IEND"}
# No listing image may be larger than this; anything claiming more is refused
# before Pillow decodes it.
MAX_SIDE = 7680

errors: list[str] = []


class Image:
    """What the checks need to know about one image file."""

    def __init__(self, kind: str, width: int, height: int, depth: int = 8, colour: int | None = None,
                 transparency: bool = False):
        self.kind, self.width, self.height = kind, width, height
        self.depth, self.colour, self.transparency = depth, colour, transparency

    @property
    def opaque_ok(self) -> bool:
        """JPEG, or an 8-bit RGB or palette PNG with no transparency."""
        if self.kind == "jpeg":
            return True
        return self.depth == 8 and self.colour in (2, 3) and not self.transparency

    @property
    def rgba(self) -> bool:
        return self.kind == "png" and self.depth == 8 and self.colour == 6


class BadImage(Exception):
    pass


def read_png_header(data: bytes) -> Image:
    """Walks the PNG chunks (with CRCs) for what Pillow doesn't report."""
    i, header, palette, transparency, seen_idat = 8, None, False, False, False
    while True:
        if i + 12 > len(data):
            raise BadImage("PNG ends without an IEND chunk")
        length, kind = struct.unpack(">I4s", data[i:i + 8])
        body = data[i + 8:i + 8 + length]
        if len(body) != length or i + 12 + length > len(data):
            raise BadImage(f"PNG chunk {kind!r} runs past the end of the file")
        if zlib.crc32(kind + body) != struct.unpack(">I", data[i + 8 + length:i + 12 + length])[0]:
            raise BadImage(f"PNG chunk {kind!r} fails its CRC")
        if kind[0] & 0x20 == 0 and kind not in PNG_CRITICAL:
            raise BadImage(f"PNG has an unknown critical chunk {kind!r}")
        if kind == b"acTL":
            raise BadImage("PNG is animated (APNG); Google Play wants a still image")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE" and not seen_idat:
            palette = True
        elif kind == b"tRNS":
            transparency = True
        elif kind == b"IDAT":
            seen_idat = True
        elif kind == b"IEND":
            break
        i += 12 + length
    if header is None or not seen_idat:
        raise BadImage("PNG has no IHDR or no image data")
    width, height, depth, colour, compression, filtering, interlace = header
    if compression != 0 or filtering != 0 or interlace not in (0, 1):
        raise BadImage("PNG uses a compression, filter or interlace method that doesn't exist")
    if colour not in PNG_CHANNELS:
        raise BadImage(f"PNG colour type {colour} is not valid")
    if colour == 3 and not palette:
        raise BadImage("PNG is indexed but has no palette before its image data")
    return Image("png", width, height, depth, colour, transparency or colour in (4, 6))


def read_image(path: Path) -> Image | None:
    data = path.read_bytes()
    try:
        if data.startswith(PNG_SIGNATURE):
            image = read_png_header(data)
        elif data.startswith(b"\xff\xd8"):
            image = None
        else:
            raise BadImage("not a PNG or JPEG")
        with Pillow.open(path) as probe:
            kind = {"PNG": "png", "JPEG": "jpeg"}.get(probe.format)
            if kind is None:
                raise BadImage(f"Pillow reads it as {probe.format}, not PNG or JPEG")
            width, height = probe.size
            if max(width, height) > MAX_SIDE:
                raise BadImage(f"{width}x{height} is larger than any Play listing image")
            probe.load()  # decodes every pixel: truncated or corrupt data fails here
        if image is None:
            image = Image("jpeg", width, height)
        elif (image.width, image.height) != (width, height):
            raise BadImage("PNG header and image data disagree on the size")
        return image
    except (BadImage, struct.error, OSError, SyntaxError, ValueError, Pillow.DecompressionBombError) as exc:
        error(path, str(exc) or "unreadable image")
        return None


def error(path: Path, message: str) -> None:
    errors.append(f"::error file={path.relative_to(ROOT)}::{message}")


def check_text(lang: Path) -> None:
    for name, limit in REQUIRED_TEXT.items():
        path = lang / name
        # Play counts characters without the trailing newline.
        text = path.read_text(encoding="utf-8").rstrip("\n") if path.is_file() else ""
        if not text.strip():
            error(path if path.is_file() else lang, f"{name} is missing or empty; Google Play requires it")
        elif len(text) > limit:
            error(path, f"{len(text)} characters; Google Play allows {limit}")
        if name in PROMO_FIELDS and (hit := PROMO_PATTERN.search(text)):
            error(path, f'"{hit.group(0)}" reads as a price or promotion keyword; '
                        "Google Play won't promote an app whose title or short description uses one")
    changelogs = lang / "changelogs"
    if changelogs.is_dir():
        for path in sorted(changelogs.glob("*.txt")):
            length = len(path.read_text(encoding="utf-8").rstrip("\n"))
            if length > CHANGELOG_LIMIT:
                error(path, f"{length} characters; Google Play allows {CHANGELOG_LIMIT}")


def check_size(path: Path, max_bytes: int) -> None:
    if path.stat().st_size > max_bytes:
        error(path, f"{path.stat().st_size / MB:.1f} MB; Google Play allows {max_bytes / MB:.0f} MB")


def listed(images: Path, stem: str) -> list[Path]:
    """Every file supply would take for a named image, and any it would miss."""
    found = []
    # supply takes exactly <stem>.png, .jpg or .jpeg; icon.backup.png is not one.
    for path in sorted(images.iterdir()):
        if path.stem != stem or path.suffix.lower() not in IMAGE_SUFFIXES:
            continue
        if path.suffix != path.suffix.lower():
            # supply globs lower-case extensions; on Linux it would skip this.
            error(path, "use a lower-case extension, or supply won't upload it")
            continue
        found.append(path)
    if len(found) > 1:
        error(images, f"more than one {stem} ({', '.join(p.name for p in found)}); keep one")
    return found


def check_icon(images: Path, required: bool) -> None:
    paths = listed(images, "icon")
    if required and not paths:
        error(images, "no icon.png; Google Play requires the 512x512 icon")
    for path in paths:
        image = read_image(path)
        if image is None:
            continue
        if (image.width, image.height) != (512, 512):
            error(path, f"{image.width}x{image.height}; Google Play wants 512x512")
        if not image.rgba:
            error(path, "Google Play wants the icon as a 32-bit PNG (8-bit RGBA)")
        check_size(path, 1 * MB)


def check_graphic(images: Path, stem: str, size: tuple[int, int], required: bool) -> None:
    paths = listed(images, stem)
    if required and not paths:
        error(images, f"no {stem}; Google Play requires one ({size[0]}x{size[1]})")
    for path in paths:
        image = read_image(path)
        if image is None:
            continue
        if (image.width, image.height) != size:
            error(path, f"{image.width}x{image.height}; Google Play wants {size[0]}x{size[1]}")
        if not image.opaque_ok:
            error(path, "Google Play wants a JPEG or an 8-bit PNG without transparency")
        check_size(path, 15 * MB)


def check_screenshots(folder: Path, shortest: int, longest: int, shape: str) -> int:
    shots = []
    for path in sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES):
        if path.suffix != path.suffix.lower():
            # supply globs lower-case extensions; on Linux it would skip this.
            error(path, "use a lower-case extension, or supply won't upload it")
        else:
            shots.append(path)
    if len(shots) > 8:
        error(folder, f"{len(shots)} screenshots; Google Play allows 8 per device type")
    for path in shots:
        image = read_image(path)
        if image is None:
            continue
        width, height = image.width, image.height
        short, long = sorted((width, height))
        if short < shortest or long > longest:
            error(path, f"{width}x{height}; sides must be {shortest} to {longest} px")
        if shape == "2:1" and long > 2 * short:
            error(path, f"{width}x{height}; the long side may be at most twice the short one")
        if shape == "16:9" and width * 9 != height * 16:
            error(path, f"{width}x{height}; TV screenshots must be 16:9 landscape")
        if shape == "1:1" and width != height:
            error(path, f"{width}x{height}; Wear OS screenshots must be square")
        if not image.opaque_ok:
            error(path, "Google Play wants a JPEG or an 8-bit PNG without transparency")
        check_size(path, 8 * MB)
    return len(shots)


def main() -> int:
    default = METADATA / DEFAULT_LANGUAGE
    if not default.is_dir():
        error(METADATA if METADATA.is_dir() else ROOT, f"no {DEFAULT_LANGUAGE} listing; it is the default and must exist")
    for lang in sorted(p for p in METADATA.iterdir() if p.is_dir()) if METADATA.is_dir() else []:
        check_text(lang)
        # The default listing carries the full set; a translation may localize
        # only some images and borrow the rest from it, as Play allows.
        full = lang.name == DEFAULT_LANGUAGE
        images = lang / "images"
        if not images.is_dir():
            if full:
                error(lang, "no images folder; Google Play needs an icon, a feature graphic and screenshots")
            continue
        check_icon(images, required=full)
        check_graphic(images, "featureGraphic", (1024, 500), required=full)
        check_graphic(images, "tvBanner", (1280, 720), required=False)
        total = 0
        for name, (shortest, longest, shape) in SCREENSHOTS.items():
            if (images / name).is_dir():
                total += check_screenshots(images / name, shortest, longest, shape)
        if full and total < 2:
            error(images, f"{total} screenshots; Google Play needs at least 2 to publish the listing")
    for line in errors:
        print(line)
    if not errors:
        print("Store listing is within Google Play's rules")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
