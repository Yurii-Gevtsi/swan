#!/usr/bin/env python3
"""Normalize event verification labels to the app's three-level vocabulary."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


CANONICAL = {"CONFIRMED", "REPORTED", "DISPUTED"}


def normalize(value: object) -> str:
    raw = str(value or "").strip().upper()
    if raw in CANONICAL:
        return raw
    if any(token in raw for token in ("DISPUT", "CONTEST", "DENIED", "DENIAL")):
        return "DISPUTED"
    if raw in {"OFFICIAL_CONFIRMED", "INSTITUTIONAL_CONFIRMED", "MULTI_SOURCE_CONFIRMED"}:
        return "CONFIRMED"
    return "REPORTED"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--app-output", type=Path)
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8"))
    events = payload.get("events", [])
    counts: dict[str, int] = {}
    for event in events:
        status = normalize(event.get("verificationStatus"))
        event["verificationStatus"] = status
        counts[status] = counts.get(status, 0) + 1

    payload["verificationStatusLevels"] = ["CONFIRMED", "REPORTED", "DISPUTED"]
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(encoded, encoding="utf-8")
    if args.app_output:
        args.app_output.parent.mkdir(parents=True, exist_ok=True)
        args.app_output.write_text(encoded, encoding="utf-8")
    print("Normalized verification statuses: " + ", ".join(f"{k}={counts.get(k, 0)}" for k in ("CONFIRMED", "REPORTED", "DISPUTED")))


if __name__ == "__main__":
    main()
