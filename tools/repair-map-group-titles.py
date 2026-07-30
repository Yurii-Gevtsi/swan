#!/usr/bin/env python3
"""Repair generated map group titles from the cleaned event snapshot."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_EVENTS = REPO_ROOT / "data" / "final" / "osint_events.json"
DEFAULT_INPUT = REPO_ROOT / "data" / "final" / "map_event_groups.json"
DEFAULT_APP_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "map_event_groups.json"

UNNAMED_CLUSTER_TITLES = {
    "map_group_unnamed_shadow_fleet_azov_sea_general": {
        "titleUk": "Безіменні судна тіньового флоту - Азовське море",
        "titleEn": "Unnamed shadow fleet vessels - Sea of Azov",
    },
    "map_group_unnamed_shadow_fleet_black_sea_general": {
        "titleUk": "Безіменні судна тіньового флоту - Чорне море",
        "titleEn": "Unnamed shadow fleet vessels - Black Sea",
    },
}


def contains_cyrillic(text: str) -> bool:
    return any("\u0400" <= char <= "\u052f" for char in text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--events", type=Path, default=DEFAULT_EVENTS)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--app-output", type=Path, default=DEFAULT_APP_OUTPUT)
    args = parser.parse_args()

    event_payload = json.loads(args.events.read_text(encoding="utf-8-sig"))
    group_payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    events_by_id = {event["id"]: event for event in event_payload.get("events", [])}

    changed = 0
    for group in group_payload.get("groups", []):
        override = UNNAMED_CLUSTER_TITLES.get(str(group.get("id") or ""))
        if override:
            for key, value in override.items():
                if group.get(key) != value:
                    group[key] = value
                    changed += 1

        representative = events_by_id.get(str(group.get("representativeEventId") or ""))
        if not representative:
            continue

        title_uk = str(representative.get("titleUk") or representative.get("titleEn") or "")
        title_en = str(representative.get("titleEn") or representative.get("titleUk") or "")

        if "\ufffd" in str(group.get("titleUk") or "") and title_uk:
            group["titleUk"] = title_uk
            changed += 1
        if contains_cyrillic(str(group.get("titleEn") or "")) and title_en:
            group["titleEn"] = title_en
            changed += 1

    serialized = json.dumps(group_payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    args.app_output.parent.mkdir(parents=True, exist_ok=True)
    args.app_output.write_text(serialized, encoding="utf-8")
    print(f"Repaired map group titles: {changed}")


if __name__ == "__main__":
    main()
