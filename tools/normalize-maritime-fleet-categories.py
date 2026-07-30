#!/usr/bin/env python3
"""Separate civilian vessels from military naval assets after data merging."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


MILITARY_VESSEL_MARKERS = (
    "військов", "воєнн", "сторожов", "патрульн", "корвет", "фрегат",
    "ракетн", "підводн", "мрк", "корабель проєкту", "корабль проекта",
    "warship", "naval", "corvette", "frigate", "missile ship", "submarine",
    "patrol ship", "project 10410", "project 11356", "project 23550",
    "project 22800", "project 21631",
)
CIVILIAN_VESSEL_MARKERS = (
    "танкер", "суховантаж", "буксир", "пором", "барж", "газовоз", "судно",
    "плавзас", "tanker", "dry cargo", "cargo ship", "tug", "ferry", "barge",
    "gas carrier", "vessel", "ship \"",
)
ENERGY_INFRASTRUCTURE_MARKERS = ("термінал", "terminal", "платформ", "platform")


def event_text(event: dict) -> str:
    fields = (
        event.get("titleUk"), event.get("titleEn"), event.get("summaryUk"),
        event.get("summaryEn"), event.get("impactTags"),
    )
    return " ".join(str(field or "") for field in fields).lower()


def title_text(event: dict) -> str:
    return " ".join(
        str(event.get(field) or "") for field in ("titleUk", "titleEn")
    ).lower()


def add_tag(event: dict, tag: str) -> None:
    tags = [item.strip() for item in str(event.get("impactTags") or "").split(",") if item.strip()]
    if tag not in tags:
        tags.append(tag)
    event["impactTags"] = ", ".join(tags)


def normalize(events: list[dict]) -> tuple[list[str], list[str]]:
    moved_to_shadow_fleet: list[str] = []
    moved_to_fuel: list[str] = []

    for event in events:
        if event.get("category") != "MARITIME_ASSET_DISRUPTION":
            continue
        if str(event.get("id") or "").startswith("event_naval_registry_"):
            continue

        title = title_text(event)
        text = event_text(event)
        # The description often says that Ukrainian military struck a civilian
        # vessel. Only the title can establish that the target itself is naval.
        if any(marker in title for marker in MILITARY_VESSEL_MARKERS):
            continue
        if any(marker in text for marker in CIVILIAN_VESSEL_MARKERS):
            event["category"] = "SHADOW_FLEET_DISRUPTION"
            add_tag(event, "SHADOW_FLEET_CLASSIFIED_CIVILIAN_VESSEL")
            moved_to_shadow_fleet.append(str(event.get("id") or "unknown"))
        elif any(marker in text for marker in ENERGY_INFRASTRUCTURE_MARKERS):
            event["category"] = "FUEL_SUPPLY_DISRUPTION"
            add_tag(event, "MARITIME_ENERGY_INFRASTRUCTURE")
            moved_to_fuel.append(str(event.get("id") or "unknown"))

    return moved_to_shadow_fleet, moved_to_fuel


def write_json(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--app-output", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    document = json.loads(args.input.read_text(encoding="utf-8"))
    if isinstance(document, list):
        events = document
    elif isinstance(document, dict) and isinstance(document.get("events"), list):
        events = document["events"]
        document["recordCount"] = len(events)
    else:
        raise ValueError("Expected an event array or an object with an events array")

    shadow_fleet, fuel = normalize(events)
    write_json(args.input, document)
    write_json(args.app_output, document)

    classified_shadow_fleet = [
        str(event.get("id") or "unknown")
        for event in events
        if event.get("category") == "SHADOW_FLEET_DISRUPTION"
        and "SHADOW_FLEET_CLASSIFIED_CIVILIAN_VESSEL" in str(event.get("impactTags") or "")
    ]
    maritime_energy = [
        str(event.get("id") or "unknown")
        for event in events
        if event.get("category") == "FUEL_SUPPLY_DISRUPTION"
        and "MARITIME_ENERGY_INFRASTRUCTURE" in str(event.get("impactTags") or "")
    ]

    report = [
        "MARITIME CATEGORY NORMALIZATION",
        f"Newly moved to SHADOW_FLEET_DISRUPTION: {len(shadow_fleet)}",
        f"Civilian vessels in SHADOW_FLEET_DISRUPTION: {len(classified_shadow_fleet)}",
        *classified_shadow_fleet,
        f"Newly moved to FUEL_SUPPLY_DISRUPTION: {len(fuel)}",
        f"Maritime energy infrastructure in FUEL_SUPPLY_DISRUPTION: {len(maritime_energy)}",
        *maritime_energy,
    ]
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    main()
