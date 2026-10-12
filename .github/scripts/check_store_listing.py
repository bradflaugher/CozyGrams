#!/usr/bin/env python3
"""Checks fastlane/metadata/android against Google Play's listing rules.

    python3 .github/scripts/check_store_listing.py   (needs Pillow: apt install python3-pil)

Play rejects a whole edit when one part of the listing breaks its rules, and
CI only uploads from main, so pull requests run this first. The rules are Play's preview-asset and listing requirements
(support.google.com/googleplay/android-developer/answer/9866151 and 9859152):

- title (30), short description (80) and full description (4000) must be
  non-empty and within their limits; every changelog, default.txt or one per
  version code, at most 500 characters
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
MB = 1024 * 1024
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}
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
    changelogs = lang / "changelogs"
    if changelogs.is_dir():
        for path in sorted(changelogs.glob("*.txt")):
            length = len(path.read_text(encoding="utf-8").rstrip("\n"))
            if length > CHANGELOG_LIMIT:
                error(path, f"{length} characters; Google Play allows {CHANGELOG_LIMIT}")


def check_size(path: Path, max_bytes: int) -> None:
    if path.stat().st_size > max_bytes:
        error(path, f"{path.stat().st_size / MB:.1f} MB; Google Play allows {max_bytes / MB:.0f} MB")


def check_icon(path: Path) -> None:
    if not path.is_file():
        return
    image = read_image(path)
    if image is None:
        return
    if (image.width, image.height) != (512, 512):
        error(path, f"{image.width}x{image.height}; Google Play wants 512x512")
    if not image.rgba:
        error(path, "Google Play wants the icon as a 32-bit PNG (8-bit RGBA)")
    check_size(path, 1 * MB)


def check_graphic(images: Path, stem: str, size: tuple[int, int]) -> None:
    for path in sorted(images.glob(f"{stem}.*")):
        if path.suffix.lower() not in IMAGE_SUFFIXES:
            continue
        image = read_image(path)
        if image is None:
            continue
        if (image.width, image.height) != size:
            error(path, f"{image.width}x{image.height}; Google Play wants {size[0]}x{size[1]}")
        if not image.opaque_ok:
            error(path, "Google Play wants a JPEG or an 8-bit PNG without transparency")
        check_size(path, 15 * MB)


def check_screenshots(folder: Path, shortest: int, longest: int, shape: str) -> int:
    shots = sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES)
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
    for lang in sorted(p for p in METADATA.iterdir() if p.is_dir()):
        check_text(lang)
        images = lang / "images"
        if not images.is_dir():
            continue
        check_icon(images / "icon.png")
        check_graphic(images, "featureGraphic", (1024, 500))
        check_graphic(images, "tvBanner", (1280, 720))
        total = 0
        for name, (shortest, longest, shape) in SCREENSHOTS.items():
            if (images / name).is_dir():
                total += check_screenshots(images / name, shortest, longest, shape)
        if total < 2:
            error(images, f"{total} screenshots; Google Play needs at least 2 to publish the listing")
    for line in errors:
        print(line)
    if not errors:
        print("Store listing is within Google Play's rules")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
