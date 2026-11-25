#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds the static privacy policy site that .github/workflows/pages.yml publishes.

    python3 scripts/build-pages.py <output-dir>

Output:
    index.html            short bilingual landing page
    privacy/index.html    PRIVACY.md
    fr/privacy/index.html PRIVACY.fr.md
    404.html

The policy files use the same small Markdown subset that the in-app renderer handles:
headings, paragraphs, bullet lists, bold and links. Italics, numbered lists and inline code
are rendered too. Anything else is shown as plain, escaped text, so the site never shows
something the app would not. Only the Python standard library is used.
"""

from __future__ import annotations

import html
import re
import sys
from pathlib import Path

SITE = "https://tidemark.reddevs.io"
REPO = "https://github.com/reddevsdotio/tidemark"
ROOT = Path(__file__).resolve().parent.parent

PAGES = [
    # (source, language, path on the site)
    ("PRIVACY.md", "en", "/privacy/"),
    ("PRIVACY.fr.md", "fr", "/fr/privacy/"),
]

STYLE = """
:root { color-scheme: light dark; --fg: #1b1c1e; --bg: #fbfbfd; --muted: #5b5f66; --link: #0b57d0; }
@media (prefers-color-scheme: dark) {
  :root { --fg: #e4e4e7; --bg: #121316; --muted: #a1a4ab; --link: #a8c7fa; }
}
html { -webkit-text-size-adjust: 100%; }
body { margin: 0; background: var(--bg); color: var(--fg);
  font: 1.0625rem/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 42rem; margin: 0 auto; padding: 2rem 1rem 4rem; }
h1 { font-size: 1.75rem; line-height: 1.25; margin: 0 0 1rem; }
h2 { font-size: 1.25rem; margin: 2rem 0 0.5rem; }
h3 { font-size: 1.0625rem; margin: 1.5rem 0 0.5rem; }
a { color: var(--link); overflow-wrap: anywhere; }
ul, ol { padding-left: 1.5rem; }
nav { font-size: 0.9375rem; color: var(--muted); margin-bottom: 2rem; }
footer { font-size: 0.875rem; color: var(--muted); margin-top: 3rem; }
code { font-size: 0.9em; }
""".strip()

INLINE = re.compile(
    r"\[(?P<text>[^\]]+)\]\((?P<url>[^)\s]+)\)"  # [text](url)
    r"|\*\*(?P<strong>.+?)\*\*"  # **bold**
    r"|(?<![\w*])\*(?P<em>[^*\s][^*]*?)\*(?![\w*])"  # *italic*
    r"|(?<!\w)_(?P<em2>[^_\s][^_]*?)_(?!\w)"  # _italic_
    r"|`(?P<code>[^`]+)`"  # `code`
)
SAFE_URL = re.compile(r"^(https://|http://|mailto:|/|#)", re.IGNORECASE)


def inline(text: str) -> str:
    """Renders inline Markdown. Text outside recognised markup is escaped."""
    out: list[str] = []
    pos = 0
    for m in INLINE.finditer(text):
        out.append(html.escape(text[pos : m.start()]))
        if m.group("text") is not None:
            url = m.group("url")
            label = inline(m.group("text"))
            if SAFE_URL.match(url):
                out.append(f'<a href="{html.escape(url)}">{label}</a>')
            else:
                out.append(label)
        elif m.group("strong") is not None:
            out.append(f"<strong>{inline(m.group('strong'))}</strong>")
        elif m.group("em") is not None:
            out.append(f"<em>{inline(m.group('em'))}</em>")
        elif m.group("em2") is not None:
            out.append(f"<em>{inline(m.group('em2'))}</em>")
        else:
            out.append(f"<code>{html.escape(m.group('code'))}</code>")
        pos = m.end()
    out.append(html.escape(text[pos:]))
    return "".join(out)


HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
BULLET = re.compile(r"^\s*[-*+]\s+(.*)$")
NUMBERED = re.compile(r"^\s*\d+[.)]\s+(.*)$")


def markdown(source: str) -> tuple[str, str]:
    """Returns (title, body HTML) for a Markdown document."""
    blocks: list[str] = []
    title = ""
    para: list[str] = []
    items: list[str] = []
    list_tag = ""

    def flush_para() -> None:
        if para:
            blocks.append(f"<p>{inline(' '.join(para))}</p>")
            para.clear()

    def flush_list() -> None:
        nonlocal list_tag
        if items:
            body = "\n".join(f"  <li>{inline(i)}</li>" for i in items)
            blocks.append(f"<{list_tag}>\n{body}\n</{list_tag}>")
            items.clear()
        list_tag = ""

    for raw in source.splitlines():
        line = raw.rstrip()
        if not line.strip():
            flush_para()
            flush_list()
            continue
        if m := HEADING.match(line):
            flush_para()
            flush_list()
            level = len(m.group(1))
            text = m.group(2)
            if level == 1 and not title:
                title = re.sub(r"[*_`]", "", text)
            blocks.append(f"<h{level}>{inline(text)}</h{level}>")
            continue
        bullet = BULLET.match(line)
        numbered = None if bullet else NUMBERED.match(line)
        if bullet or numbered:
            flush_para()
            tag = "ul" if bullet else "ol"
            if list_tag and list_tag != tag:
                flush_list()
            list_tag = tag
            items.append((bullet or numbered).group(1))
            continue
        if items and raw[:1].isspace():
            # Indented continuation of the previous list item.
            items[-1] += " " + line.strip()
            continue
        flush_list()
        para.append(line.strip())
    flush_para()
    flush_list()
    return title, "\n".join(blocks)


def page(lang: str, title: str, body: str, path: str | None, alternates: dict[str, str], nav: str) -> str:
    links = [f'<link rel="canonical" href="{SITE}{path}">'] if path else []
    for alt_lang, alt_path in alternates.items():
        links.append(f'<link rel="alternate" hreflang="{alt_lang}" href="{SITE}{alt_path}">')
    head_links = "\n".join(links)
    source_label = "Code source" if lang == "fr" else "Source code"
    footer = (
        f'<footer>Tidemark · GPL-3.0-or-later · {source_label} : '
        if lang == "fr"
        else f"<footer>Tidemark · GPL-3.0-or-later · {source_label}: "
    ) + f'<a href="{REPO}">{REPO.removeprefix("https://")}</a></footer>'
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
{head_links}
<style>
{STYLE}
</style>
</head>
<body>
<main>
<nav>{nav}</nav>
{body}
{footer}
</main>
</body>
</html>
"""


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <output-dir>", file=sys.stderr)
        return 2
    out = Path(argv[1])
    alternates = {lang: path for _, lang, path in PAGES}

    for source, lang, path in PAGES:
        text = (ROOT / source).read_text(encoding="utf-8")
        title, body = markdown(text)
        if not title:
            title = "Politique de confidentialité de Tidemark" if lang == "fr" else "Tidemark privacy policy"
        nav = (
            '<a href="/">Tidemark</a> · <a href="/privacy/" hreflang="en" lang="en">English</a>'
            if lang == "fr"
            else '<a href="/">Tidemark</a> · <a href="/fr/privacy/" hreflang="fr" lang="fr">Français</a>'
        )
        target = out / path.strip("/") / "index.html"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(page(lang, title, body, path, alternates, nav), encoding="utf-8")
        print(f"{source} -> {target}")

    index_body = f"""<h1>Tidemark</h1>
<p>A read-only, offline viewer for your Health Connect data on Android.</p>
<p lang="fr">Une visionneuse hors ligne, en lecture seule, de vos données Connexion Santé sur Android.</p>
<ul>
  <li><a href="/privacy/">Privacy policy</a></li>
  <li><a href="/fr/privacy/" hreflang="fr" lang="fr">Politique de confidentialité</a></li>
  <li><a href="{REPO}">Source code</a> · <a href="{REPO}/releases">Releases</a></li>
</ul>"""
    (out / "index.html").write_text(page("en", "Tidemark", index_body, "/", {}, ""), encoding="utf-8")

    not_found = """<h1>Page not found</h1>
<p><a href="/">Tidemark</a> · <a href="/privacy/">Privacy policy</a></p>
<p lang="fr">Page introuvable. <a href="/fr/privacy/">Politique de confidentialité</a></p>"""
    (out / "404.html").write_text(page("en", "Page not found · Tidemark", not_found, None, {}, ""), encoding="utf-8")
    print(f"index.html and 404.html -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
