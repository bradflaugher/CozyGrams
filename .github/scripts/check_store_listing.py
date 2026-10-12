#!/usr/bin/env python3
"""Checks fastlane/metadata/android against Google Play's listing rules.

    python3 .github/scripts/check_store_listing.py

Play rejects a whole edit when one part of the listing breaks its rules, and
CI only uploads from main, so pull requests run this first. Standard library
only. The rules are Play's preview-asset and listing requirements
(support.google.com/googleplay/android-developer/answer/9866151 and 9859152):

- title (30), short description (80) and full description (4000) must be
  non-empty and within their limits; every changelog, default.txt or one per
  version code, at most 500 characters
- icon: 512 x 512, 32-bit PNG (8-bit RGBA), at most 1 MB
- feature graphic 1024 x 500 and TV banner 1280 x 720: JPEG or 8-bit PNG
  without transparency, at most 15 MB
- screenshots: JPEG or 8-bit PNG without transparency, at most 8 MB and 8 per
  device type; phone sides 320 to 3840 px with the long side at most twice
  the short one; tablet sides 1080 to 3840 px (Play's tablet minimum, within
  its upload maximum); TV 16:9; Wear OS square
Play documents "24-bit PNG" for the opaque assets; it also takes 8-bit
palette PNGs without transparency (PokerSI's pixel-art listing is one), so
those pass too.

Every image is decoded far enough to trust it: PNG chunks are walked with
their CRCs and the image data is inflated to the size its header promises;
JPEG markers are walked through the frame header to a scan with data, and
the file must end in EOI.
"""

import struct
import sys
import zlib
from pathlib import Path

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
# JPEG start-of-frame markers (every kind but DHT, JPG and DAC).
JPEG_SOF = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
# Markers with no length field.
JPEG_STANDALONE = {0x01, *range(0xD0, 0xD8)}
# Adam7 interlace passes: x start, y start, x step, y step.
ADAM7 = [(0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4), (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2)]

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


def png_data_size(width: int, height: int, bits_per_pixel: int, interlace: int) -> int:
    """Bytes of filtered scanlines the header promises (each row has a filter byte)."""
    passes = [(0, 0, 1, 1)] if interlace == 0 else ADAM7
    size = 0
    for x0, y0, dx, dy in passes:
        cols, rows = (width - x0 + dx - 1) // dx, (height - y0 + dy - 1) // dy
        if cols > 0 and rows > 0:
            size += rows * (1 + (cols * bits_per_pixel + 7) // 8)
    return size


def read_png(data: bytes) -> Image:
    i, header, idat, transparency = 8, None, [], False
    while True:
        if i + 12 > len(data):
            raise BadImage("PNG ends without an IEND chunk")
        length, kind = struct.unpack(">I4s", data[i:i + 8])
        body = data[i + 8:i + 8 + length]
        if len(body) != length or i + 12 + length > len(data):
            raise BadImage(f"PNG chunk {kind!r} runs past the end of the file")
        crc = struct.unpack(">I", data[i + 8 + length:i + 12 + length])[0]
        if zlib.crc32(kind + body) != crc:
            raise BadImage(f"PNG chunk {kind!r} fails its CRC")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"tRNS":
            transparency = True
        elif kind == b"IDAT":
            idat.append(body)
        elif kind == b"IEND":
            break
        i += 12 + length
    if header is None or not idat:
        raise BadImage("PNG has no IHDR or no image data")
    width, height, depth, colour, _compression, _filter, interlace = header
    if colour not in PNG_CHANNELS:
        raise BadImage(f"PNG colour type {colour} is not valid")
    try:
        pixels = zlib.decompress(b"".join(idat))
    except zlib.error as exc:
        raise BadImage(f"PNG image data does not inflate: {exc}") from None
    if interlace not in (0, 1):
        raise BadImage(f"PNG interlace method {interlace} is not valid")
    if len(pixels) != png_data_size(width, height, PNG_CHANNELS[colour] * depth, interlace):
        raise BadImage("PNG image data is not the size its header promises")
    return Image("png", width, height, depth, colour, transparency or colour in (4, 6))


def read_jpeg(data: bytes) -> Image:
    end = len(data.rstrip(b"\x00"))
    if data[end - 2:end] != b"\xff\xd9":
        raise BadImage("JPEG does not end with an EOI marker")
    i, frame = 2, None
    while i < end:
        if data[i] != 0xFF:
            raise BadImage("JPEG marker segments are out of step")
        while i < end and data[i] == 0xFF:  # fill bytes before a marker
            i += 1
        marker = data[i]
        i += 1
        if marker in JPEG_STANDALONE:
            continue
        if marker == 0xD9:
            break
        length = struct.unpack(">H", data[i:i + 2])[0]
        if length < 2 or i + length > end:
            raise BadImage("JPEG segment runs past the end of the file")
        if marker in JPEG_SOF:
            height, width = struct.unpack(">HH", data[i + 3:i + 7])
            frame = Image("jpeg", width, height)
        elif marker == 0xDA:
            # Start of scan: the frame must come first, and entropy-coded data
            # must follow before the final EOI.
            if frame is None:
                raise BadImage("JPEG scan comes before its frame header")
            if i + length >= end - 2:
                raise BadImage("JPEG has no image data after its scan header")
            return frame
        i += length
    raise BadImage("JPEG has no frame header and scan")


def read_image(path: Path) -> Image | None:
    data = path.read_bytes()
    try:
        if data.startswith(PNG_SIGNATURE):
            return read_png(data)
        if data.startswith(b"\xff\xd8"):
            return read_jpeg(data)
        raise BadImage("not a PNG or JPEG")
    except (BadImage, struct.error) as exc:
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


def check_screenshots(folder: Path, shortest: int, longest: int, shape: str) -> None:
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


def main() -> int:
    for lang in sorted(p for p in METADATA.iterdir() if p.is_dir()):
        check_text(lang)
        images = lang / "images"
        if not images.is_dir():
            continue
        check_icon(images / "icon.png")
        check_graphic(images, "featureGraphic", (1024, 500))
        check_graphic(images, "tvBanner", (1280, 720))
        for name, (shortest, longest, shape) in SCREENSHOTS.items():
            if (images / name).is_dir():
                check_screenshots(images / name, shortest, longest, shape)
    for line in errors:
        print(line)
    if not errors:
        print("Store listing is within Google Play's rules")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
