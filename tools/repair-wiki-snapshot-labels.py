#!/usr/bin/env python3
"""Translate snapshot map-point English labels when they still contain Ukrainian text."""

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
DEFAULT_CACHE = REPO_ROOT / "tools" / "translation-cache-uk-en.json"
DEFAULT_INPUTS = (
    REPO_ROOT / "data" / "final" / "wiki_uav_strikes_2022_2025_snapshot.json",
    REPO_ROOT / "data" / "final" / "wiki_uav_strikes_2026_snapshot.json",
)
WHITESPACE = re.compile(r"\s+")


def contains_cyrillic(text: str) -> bool:
    return any("\u0400" <= char <= "\u052f" for char in text)


def normalize_text(value: str) -> str:
    text = str(value or "")
    for source, target in {
        "\u00a0": " ",
        "\u2013": " - ",
        "\u2014": " - ",
        "\u2018": "'",
        "\u2019": "'",
        "\u201c": '"',
        "\u201d": '"',
        "\u00ab": '"',
        "\u00bb": '"',
    }.items():
        text = text.replace(source, target)
    return WHITESPACE.sub(" ", text).strip()


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
            return normalize_text(translated).replace(" � ", " - ").replace("�", "")
        except Exception:
            if attempt == 3:
                raise
            time.sleep(1.5 * (attempt + 1))
    return ""


def iter_map_points(payload: dict):
    for record in payload.get("records", []):
        map_point = record.get("mapPoint")
        if isinstance(map_point, dict):
            yield map_point

    for duplicate_group in payload.get("pageDuplicateGroups", []):
        for key in ("duplicate", "first"):
            candidate = duplicate_group.get(key) or {}
            map_point = candidate.get("mapPoint")
            if isinstance(map_point, dict):
                yield map_point


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--inputs", nargs="+", type=Path, default=list(DEFAULT_INPUTS))
    args = parser.parse_args()

    cache = json.loads(args.cache.read_text(encoding="utf-8")) if args.cache.exists() else {}
    requests: set[str] = set()
    payloads: list[tuple[Path, dict]] = []

    for path in args.inputs:
        if not path.exists():
            continue
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        payloads.append((path, payload))
        for map_point in iter_map_points(payload):
            label_uk = normalize_text(map_point.get("labelUk") or map_point.get("labelEn") or "")
            label_en = normalize_text(map_point.get("labelEn") or "")
            if label_uk and contains_cyrillic(label_uk) and (not label_en or contains_cyrillic(label_en) or label_en == label_uk):
                if label_uk not in cache:
                    requests.add(label_uk)

    if requests:
        def fetch(source: str) -> tuple[str, str]:
            return source, translate_online(source)

        with ThreadPoolExecutor(max_workers=8) as executor:
            futures = {executor.submit(fetch, source): source for source in sorted(requests)}
            for future in as_completed(futures):
                source, translation = future.result()
                cache[source] = translation

    changed = 0
    for path, payload in payloads:
        for map_point in iter_map_points(payload):
            label_uk = normalize_text(map_point.get("labelUk") or map_point.get("labelEn") or "")
            label_en = normalize_text(map_point.get("labelEn") or "")
            if label_uk and contains_cyrillic(label_uk) and (not label_en or contains_cyrillic(label_en) or label_en == label_uk):
                translated = cache.get(label_uk, label_en)
                if translated and translated != map_point.get("labelEn"):
                    map_point["labelEn"] = translated
                    changed += 1
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    args.cache.parent.mkdir(parents=True, exist_ok=True)
    args.cache.write_text(json.dumps(dict(sorted(cache.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Repaired snapshot labels: {changed}")


if __name__ == "__main__":
    main()
