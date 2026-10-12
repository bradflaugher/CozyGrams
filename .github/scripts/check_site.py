#!/usr/bin/env python3
"""Checks the GitHub Pages site in site/ before it is deployed.

    python3 .github/scripts/check_site.py

The site is hand-written HTML with no build step, so this is the only thing
between a typo and a broken page. It fails on:

- a page, stylesheet, script, image or icon the site loads from anywhere but
  itself (the game sends nothing, and its website shouldn't either); links a
  visitor clicks, and the canonical and OpenGraph addresses, may point outward
- a local link or asset that doesn't resolve to a file in site/ (a directory
  resolves to its index.html; /CozyGrams/... is the site root, which 404.html
  needs because GitHub Pages serves it at any depth)
- a #fragment that names no id on its page
- a missing index.html, privacy/index.html or 404.html, or a privacy policy
  without its contact address
"""

import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

SITE = Path(__file__).resolve().parents[2] / "site"
BASE = "/CozyGrams/"
REQUIRED = ["index.html", "privacy/index.html", "404.html"]
CONTACT = "brad@bradflaugher.com"

# Attributes that make the browser fetch something on its own.
LOADS = {("img", "src"), ("script", "src"), ("source", "src"), ("source", "srcset"),
         ("img", "srcset"), ("iframe", "src"), ("video", "src"), ("audio", "src"),
         ("video", "poster"), ("embed", "src"), ("object", "data")}
CSS_URL = re.compile(r"url\(\s*['\"]?([^'\")]+)['\"]?\s*\)")


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs = []  # (url, loads_itself)
        self.ids = set()
        self.styles = []
        self._in_style = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag == "style":
            self._in_style = True
        if a.get("style"):
            self.styles.append(a["style"])
        for name, value in attrs:
            if value is None:
                continue
            if (tag, name) in LOADS:
                for part in value.split(","):
                    self.refs.append((part.strip().split(" ")[0], True))
            elif name == "href" and tag == "link":
                rel = (a.get("rel") or "").lower()
                self.refs.append((value, rel != "canonical"))
            elif name == "href":  # <a>, <use>
                self.refs.append((value, tag == "use"))

    handle_startendtag = handle_starttag

    def handle_endtag(self, tag):
        if tag == "style":
            self._in_style = False

    def handle_data(self, data):
        if self._in_style:
            self.styles.append(data)


def resolve(src: Path, url: str):
    """The file a local URL names, or None when it points outside site/."""
    path = unquote(urlsplit(url).path)
    if path.startswith(BASE):
        target = SITE / path[len(BASE):]
    elif path.startswith("/"):
        return None
    else:
        target = src.parent / path
    target = target.resolve()
    if target.is_dir() or path.endswith("/"):
        target = target / "index.html"
    try:
        target.relative_to(SITE)
    except ValueError:
        return None
    return target


def is_external(url: str) -> bool:
    s = urlsplit(url)
    return bool(s.scheme or s.netloc)


def main() -> int:
    errors = []
    for name in REQUIRED:
        if not (SITE / name).is_file():
            errors.append(f"site/{name}: missing")

    ids = {}
    pages = {}
    for f in sorted(SITE.rglob("*.html")):
        p = Page()
        p.feed(f.read_text(encoding="utf-8"))
        pages[f.resolve()] = p
        ids[f.resolve()] = p.ids

    def check(src: Path, url: str, loads: bool):
        rel = src.relative_to(SITE)
        if not url or url.startswith(("mailto:", "data:")):
            return
        if is_external(url):
            if loads:
                errors.append(f"site/{rel}: loads {url} from outside the site")
            return
        split = urlsplit(url)
        if not split.path:  # "#fragment" on this page
            if split.fragment and src.suffix == ".html" and split.fragment not in ids.get(src.resolve(), set()):
                errors.append(f"site/{rel}: #{split.fragment} names no id on the page")
            return
        target = resolve(src, url)
        if target is None or not target.is_file():
            errors.append(f"site/{rel}: {url} does not resolve to a file in site/")
        elif split.fragment and target.suffix == ".html" and split.fragment not in ids.get(target, set()):
            errors.append(f"site/{rel}: {url} names no id on that page")

    for f, p in pages.items():
        for url, loads in p.refs:
            check(f, url, loads)
        for css in p.styles:
            for url in CSS_URL.findall(css):
                check(f, url, True)

    for f in sorted(SITE.rglob("*.css")):
        for url in CSS_URL.findall(f.read_text(encoding="utf-8")):
            check(f.resolve(), url, True)
        if re.search(r"@import", f.read_text(encoding="utf-8")):
            errors.append(f"site/{f.relative_to(SITE)}: @import is not allowed")

    privacy = SITE / "privacy/index.html"
    if privacy.is_file() and CONTACT not in privacy.read_text(encoding="utf-8"):
        errors.append(f"site/privacy/index.html: no contact address ({CONTACT})")

    for e in errors:
        print(f"::error::{e}")
    if errors:
        return 1
    print(f"site/: {len(pages)} pages checked, every link and asset resolves, nothing loads from elsewhere.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
