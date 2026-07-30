#!/usr/bin/env python3
"""Generate complete public legal HTML pages from the canonical PDF documents."""

from __future__ import annotations

import html
import re
from pathlib import Path

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / "site"

DOCUMENTS = (
    ("privacy-policy.html", "legal/privacy-policy.pdf", "Privacy Policy"),
    ("terms.html", "legal/terms-of-use.pdf", "Terms of Use"),
    ("methodology.html", "legal/data-sources-and-editorial-methodology.pdf", "Methodology"),
)

ADDITIONAL_HEADINGS = {
    "About Black Swan",
    "Short In-App Disclaimer",
    "Event Card Disclaimer",
    "External Link Notice",
    "Support Ukraine Notice",
    "Privacy Choices Label",
    "Remove Ads Description",
    "Restore Purchases Text",
    "Report an Error Text",
    "Legal Menu",
    "Publisher Attribution",
    "Google Play Independence Disclaimer",
    "Recommended Website Footer",
}


def extracted_lines(pdf_path: Path) -> list[str]:
    lines: list[str] = []
    for page in PdfReader(pdf_path).pages:
        for raw in (page.extract_text() or "").splitlines():
            line = raw.strip()
            if not line or line == "•" or re.fullmatch(r"\d+", line):
                continue
            lines.append(line)
    return lines


def page_markup(pdf_path: Path, page_title: str) -> str:
    lines = extracted_lines(pdf_path)
    if len(lines) < 3:
        raise RuntimeError(f"Could not extract a complete document from {pdf_path}")
    document_title, effective_date, *body = lines
    body_markup = []
    for line in body:
        escaped = html.escape(line)
        if re.match(r"^\d+\. ", line) or line in ADDITIONAL_HEADINGS:
            body_markup.append(f"    <h2>{escaped}</h2>")
        else:
            body_markup.append(f"    <p>{escaped}</p>")
    nav = (
        '<a href="/methodology.html">Methodology</a>'
        '<a href="/privacy-policy.html">Privacy Policy</a>'
        '<a href="/terms.html">Terms of Use</a>'
    )
    pdf_url = "/" + pdf_path.relative_to(SITE).as_posix()
    return "\n".join((
        "<!doctype html>",
        '<html lang="en">',
        "<head>",
        '  <meta charset="utf-8">',
        '  <meta name="viewport" content="width=device-width, initial-scale=1">',
        f'  <meta name="description" content="Complete {html.escape(page_title)} for Black Swan: War Impact Map.">',
        f"  <title>{html.escape(page_title)} | GY Signal Studio</title>",
        '  <link rel="stylesheet" href="/styles.css">',
        "</head>",
        "<body>",
        f'  <header><div class="shell topbar"><a class="brand" href="/">GY <span>Signal Studio</span></a><nav aria-label="Primary navigation">{nav}</nav></div></header>',
        '  <main class="shell document">',
        f"    <h1>{html.escape(document_title)}</h1>",
        f"    <p>{html.escape(effective_date)}</p>",
        f'    <p class="notice">This HTML page contains the complete document text. A printable copy is also available as a <a href="{pdf_url}">PDF</a>.</p>',
        *body_markup,
        "  </main>",
        '  <footer><div class="shell"><span>&copy; 2026 GY Signal Studio. All rights reserved, except for identified third-party materials.</span><a href="/privacy-policy.html">Privacy Policy</a></div></footer>',
        "</body>",
        "</html>",
        "",
    ))


def main() -> None:
    for output_name, pdf_name, page_title in DOCUMENTS:
        pdf_path = SITE / pdf_name
        (SITE / output_name).write_text(page_markup(pdf_path, page_title), encoding="utf-8")
        print(f"Wrote {output_name} from {pdf_name}")


if __name__ == "__main__":
    main()
