#!/usr/bin/env python3
"""Translate English-facing event fields while preserving Ukrainian source text."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = REPO_ROOT / "data" / "final" / "osint_events.json"
DEFAULT_APP_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "osint_events.json"
DEFAULT_CACHE = REPO_ROOT / "tools" / "translation-cache-uk-en.json"

ID_LIKE_ENGLISH = re.compile(r"^(event|entry|record|source)\b.*\d{4,}", re.IGNORECASE)
WHITESPACE = re.compile(r"\s+")

TRANSLIT = {
    "А": "A", "Б": "B", "В": "V", "Г": "H", "Ґ": "G", "Д": "D", "Е": "E",
    "Є": "Ye", "Ж": "Zh", "З": "Z", "И": "Y", "І": "I", "Ї": "Yi", "Й": "Y",
    "К": "K", "Л": "L", "М": "M", "Н": "N", "О": "O", "П": "P", "Р": "R",
    "С": "S", "Т": "T", "У": "U", "Ф": "F", "Х": "Kh", "Ц": "Ts", "Ч": "Ch",
    "Ш": "Sh", "Щ": "Shch", "Ь": "", "Ю": "Yu", "Я": "Ya", "Ъ": "", "Ы": "Y",
    "Э": "E", "Ё": "Yo",
}
TRANSLIT.update({key.lower(): value.lower() for key, value in list(TRANSLIT.items())})


def contains_cyrillic(text: str) -> bool:
    return any("\u0400" <= char <= "\u052f" for char in text)


def normalize_text(value: str) -> str:
    text = str(value or "")
    replacements = {
        "\u00a0": " ",
        "\u2013": " - ",
        "\u2014": " - ",
        "\u2212": "-",
        "\u2018": "'",
        "\u2019": "'",
        "\u201c": '"',
        "\u201d": '"',
        "\u00ab": '"',
        "\u00bb": '"',
    }
    for source, target in replacements.items():
        text = text.replace(source, target)
    return WHITESPACE.sub(" ", text).strip()


def transliterate(value: str) -> str:
    return "".join(TRANSLIT.get(character, character) for character in normalize_text(value))


def polish_translation(source: str, translation: str) -> str:
    result = normalize_text(translation)
    result = result.replace(" � ", " - ").replace("�", "")
    result = result.replace("No name", "unnamed")
    result = result.replace("dry cargo", "dry-cargo ship")
    result = result.replace("Dry cargo", "Dry-cargo ship")
    if "без назви" in source.lower():
        result = result.replace("(no name)", "(unnamed)")
    result = re.sub(
        r"\b[\w-]*[\u0400-\u052f][\w-]*\b",
        lambda match: transliterate(match.group(0)),
        result,
    )
    return WHITESPACE.sub(" ", result).strip(" -")


def should_translate(source: str, existing: str) -> bool:
    if not source or not contains_cyrillic(source):
        return False
    if not existing.strip():
        return True
    if contains_cyrillic(existing):
        return True
    if "\ufffd" in existing:
        return True
    if ID_LIKE_ENGLISH.match(existing):
        return True
    return normalize_text(source) == normalize_text(existing)


def translate_online(value: str) -> str:
    query = urllib.parse.urlencode(
        {"client": "gtx", "sl": "uk", "tl": "en", "dt": "t", "q": normalize_text(value)}
    )
    request = urllib.request.Request(
        f"https://translate.googleapis.com/translate_a/single?{query}",
        headers={"User-Agent": "Mozilla/5.0"},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                payload = json.loads(response.read().decode("utf-8"))
            translated = "".join(part[0] for part in payload[0] if part and part[0])
            return polish_translation(value, translated)
        except Exception:
            if attempt == 3:
                raise
            time.sleep(1.5 * (attempt + 1))
    return ""


def load_cache(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def save_cache(path: Path, cache: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(dict(sorted(cache.items())), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def translated_value(source: str, existing: str, cache: dict[str, str], offline: bool) -> str:
    source = normalize_text(source)
    existing = normalize_text(existing)
    if not should_translate(source, existing):
        return existing or source
    if source in cache:
        return polish_translation(source, cache[source])
    if offline:
        translation = transliterate(source)
    else:
        translation = translate_online(source)
    cache[source] = translation
    return translation


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--app-output", type=Path, default=DEFAULT_APP_OUTPUT)
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--offline", action="store_true", help="Use cached translations only.")
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    cache = load_cache(args.cache)

    requests: set[str] = set()
    if not args.offline:
        for event in payload.get("events", []):
            candidates = (
                str(event.get("titleUk") or event.get("titleEn") or ""),
                str(event.get("approximateLocationLabelUk") or event.get("approximateLocationLabelEn") or ""),
                str(event.get("summaryUk") or event.get("summaryEn") or ""),
            )
            existing_values = (
                str(event.get("titleEn") or ""),
                str(event.get("approximateLocationLabelEn") or ""),
                str(event.get("summaryEn") or ""),
            )
            for source, existing in zip(candidates, existing_values):
                source = normalize_text(source)
                if should_translate(source, existing) and source not in cache:
                    requests.add(source)

        if requests:
            def fetch(source: str) -> tuple[str, str]:
                return source, translate_online(source)

            fetched = 0
            with ThreadPoolExecutor(max_workers=8) as executor:
                futures = {executor.submit(fetch, source): source for source in sorted(requests)}
                for future in as_completed(futures):
                    source, translation = future.result()
                    cache[source] = translation
                    fetched += 1
                    if fetched % 50 == 0:
                        save_cache(args.cache, cache)
                        print(f"Fetched translations: {fetched}", flush=True)
            save_cache(args.cache, cache)

    changed_titles = 0
    changed_locations = 0
    changed_summaries = 0

    for event in payload.get("events", []):
        title_uk = str(event.get("titleUk") or event.get("titleEn") or "")
        location_uk = str(event.get("approximateLocationLabelUk") or event.get("approximateLocationLabelEn") or "")
        summary_uk = str(event.get("summaryUk") or event.get("summaryEn") or "")

        title_en = translated_value(title_uk, str(event.get("titleEn") or ""), cache, args.offline)
        location_en = translated_value(location_uk, str(event.get("approximateLocationLabelEn") or ""), cache, args.offline)
        summary_en = translated_value(summary_uk, str(event.get("summaryEn") or ""), cache, args.offline)

        if title_en != event.get("titleEn"):
            changed_titles += 1
        if location_en != event.get("approximateLocationLabelEn"):
            changed_locations += 1
        if summary_en != event.get("summaryEn"):
            changed_summaries += 1

        event["titleEn"] = title_en
        event["approximateLocationLabelEn"] = location_en
        event["summaryEn"] = summary_en

    serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    args.app_output.parent.mkdir(parents=True, exist_ok=True)
    args.app_output.write_text(serialized, encoding="utf-8")
    save_cache(args.cache, cache)

    print(f"Localized English titles: {changed_titles}")
    print(f"Localized English locations: {changed_locations}")
    print(f"Localized English summaries: {changed_summaries}")
    print(f"Cached translations: {len(cache)}")


if __name__ == "__main__":
    main()
