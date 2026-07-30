#!/usr/bin/env python3
"""Validate localized content for mojibake and missing English translations."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


UKRAINIAN_LETTERS = set(
    "АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ"
    "абвгґдеєжзиіїйклмнопрстуфхцчшщьюя"
)
SUSPICIOUS_MOJIBAKE_MARKERS = (
    "Р\u00A0",
    "Р’",
    "РЋ",
    "Рџ",
    "РІ",
    "Р°",
    "Рµ",
    "Рѕ",
    "РЎ",
    "РЇ",
    "С‚",
    "СЊ",
    "С–",
    "С”",
    "С—",
    "вЂ",
    "Ð",
    "Ñ",
    "Р ",
)
ID_LIKE_ENGLISH = re.compile(r"^(event|entry|record|source)\b.*\d{4,}", re.IGNORECASE)
DEFAULT_TEXT_FILES = (
    "README.md",
    "data/weekly/AGENT_INSTRUCTIONS.md",
)
DEFAULT_JSON_GLOBS = (
    "app/src/main/assets/*.json",
    "data/final/*.json",
)
SITE_DATA_GLOBS = (
    "site/data/*.json",
)


@dataclass
class Finding:
    path: Path
    location: str
    message: str


def push_finding(findings: list[Finding], seen: set[tuple[Path, str, str]], path: Path, location: str, message: str) -> None:
    key = (path, location, message)
    if key in seen:
        return
    seen.add(key)
    findings.append(Finding(path, location, message))


def contains_cyrillic(text: str) -> bool:
    return any("\u0400" <= char <= "\u052F" for char in text)


def ukrainian_letter_count(text: str) -> int:
    return sum(char in UKRAINIAN_LETTERS for char in text)


def mojibake_score(text: str) -> int:
    return sum(text.count(marker) for marker in SUSPICIOUS_MOJIBAKE_MARKERS)


def looks_like_mojibake(text: str) -> bool:
    if not text:
        return False
    score = mojibake_score(text)
    if "Р\u00A0" in text or "РЋ" in text:
        return True
    if score < 6:
        return False
    return score > ukrainian_letter_count(text) * 2


def iter_targets(repo_root: Path, include_site_data: bool) -> tuple[list[Path], list[Path]]:
    text_targets: list[Path] = []
    for rel in DEFAULT_TEXT_FILES:
        path = repo_root / rel
        if path.is_file():
            text_targets.append(path)

    json_targets: set[Path] = set()
    for pattern in DEFAULT_JSON_GLOBS:
        for path in repo_root.glob(pattern):
            if path.is_file():
                json_targets.add(path.resolve())
    if include_site_data:
        for pattern in SITE_DATA_GLOBS:
            for path in repo_root.glob(pattern):
                if path.is_file():
                    json_targets.add(path.resolve())

    return text_targets, sorted(json_targets)


def validate_text_file(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    seen: set[tuple[Path, str, str]] = set()
    try:
        text = path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as exc:
        return [Finding(path, "file", f"cannot be decoded as UTF-8: {exc}")]

    for index, line in enumerate(text.splitlines(), start=1):
        if looks_like_mojibake(line):
            push_finding(
                findings,
                seen,
                path,
                f"line {index}",
                f"possible mojibake: {line.strip()[:160]}",
            )
            if len(findings) >= 8:
                break
    return findings


def walk_json(node: Any, path: str = "$") -> Iterable[tuple[str, Any]]:
    yield path, node
    if isinstance(node, dict):
        for key, value in node.items():
            yield from walk_json(value, f"{path}.{key}")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from walk_json(value, f"{path}[{index}]")


def validate_json_file(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    seen: set[tuple[Path, str, str]] = set()
    try:
        raw = path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as exc:
        return [Finding(path, "file", f"cannot be decoded as UTF-8: {exc}")]

    for index, line in enumerate(raw.splitlines(), start=1):
        if looks_like_mojibake(line):
            push_finding(
                findings,
                seen,
                path,
                f"line {index}",
                f"possible mojibake in JSON text: {line.strip()[:160]}",
            )
            if len(findings) >= 8:
                break

    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        push_finding(findings, seen, path, "file", f"invalid JSON: {exc}")
        return findings

    for json_path, node in walk_json(payload):
        if not isinstance(node, dict):
            continue

        for key, value in node.items():
            if not isinstance(value, str):
                continue

            stripped = value.strip()
            if key.endswith("Uk") and stripped and looks_like_mojibake(stripped):
                push_finding(findings, seen, path, json_path, f"'{key}' looks like mojibake")

            if key.endswith("En"):
                if stripped and looks_like_mojibake(stripped):
                    push_finding(findings, seen, path, json_path, f"'{key}' looks like mojibake")
                if stripped and contains_cyrillic(stripped):
                    push_finding(findings, seen, path, json_path, f"'{key}' contains Cyrillic text")
                if stripped and ID_LIKE_ENGLISH.match(stripped):
                    push_finding(
                        findings,
                        seen,
                        path,
                        json_path,
                        f"'{key}' looks like an untranslated generated identifier",
                    )

            if not key.endswith("Uk"):
                continue

            en_key = f"{key[:-2]}En"
            if en_key not in node:
                continue

            en_value = node.get(en_key)
            if not isinstance(en_value, str) or not en_value.strip():
                if stripped:
                    push_finding(findings, seen, path, json_path, f"'{en_key}' is empty while '{key}' has text")
                continue

            en_stripped = en_value.strip()
            if contains_cyrillic(en_stripped):
                push_finding(findings, seen, path, json_path, f"'{en_key}' contains Cyrillic text")
            if stripped == en_stripped and contains_cyrillic(stripped):
                push_finding(
                    findings,
                    seen,
                    path,
                    json_path,
                    f"'{en_key}' matches '{key}' and does not look translated",
                )

    return findings


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root to scan (default: the current repository).",
    )
    parser.add_argument(
        "--include-site-data",
        action="store_true",
        help="Also validate files in site/data/ (used before deploy).",
    )
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    text_targets, json_targets = iter_targets(repo_root, include_site_data=args.include_site_data)

    findings: list[Finding] = []
    for path in text_targets:
        findings.extend(validate_text_file(path))
    for path in json_targets:
        findings.extend(validate_json_file(path))

    if findings:
        print(f"LOCALIZATION VALIDATION FAILED: {len(findings)} issue(s)")
        for finding in findings[:200]:
            print(f"  ERROR {finding.path.relative_to(repo_root)} :: {finding.location} :: {finding.message}")
        if len(findings) > 200:
            print(f"  ... {len(findings) - 200} more issue(s) omitted")
        return 1

    print("Localization OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
