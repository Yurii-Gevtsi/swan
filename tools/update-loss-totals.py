#!/usr/bin/env python3
"""Refresh the "Estimated enemy losses" totals shown on the Total tab.

Writes the same schema to both data/final/loss_totals.json (source of record)
and app/src/main/assets/loss_totals.json (bundled asset; publish-weekly.ps1
copies it into site/data so the app can sync it without a store release).

Usage: see LOSS_TOTALS_INSTRUCTIONS.md for how the headless agent is expected
to gather the numbers before calling this script.
"""

import argparse
import datetime as dt
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
FINAL_OUTPUT = REPO_ROOT / "data" / "final" / "loss_totals.json"
ASSET_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "loss_totals.json"

# (key, labelUk, labelEn) — order here is the order shown in the app.
CATEGORIES = [
    ("personnel", "Особовий склад", "Personnel"),
    ("tanks", "Танки", "Tanks"),
    ("armored_vehicles", "Бойові броньовані машини", "Armored combat vehicles"),
    ("artillery", "Артилерійські системи", "Artillery systems"),
    ("mlrs", "Реактивні системи залпового вогню", "Multiple-launch rocket systems"),
    ("air_defense", "Засоби протиповітряної оборони", "Air-defense systems"),
    ("aircraft", "Літаки", "Aircraft"),
    ("helicopters", "Гелікоптери", "Helicopters"),
    ("uav", "Безпілотні літальні апарати", "Unmanned aerial vehicles"),
    ("cruise_missiles", "Крилаті ракети", "Cruise missiles"),
    ("ships", "Кораблі та катери", "Ships and boats"),
    ("submarines", "Підводні човни", "Submarines"),
    ("vehicles_fuel_tanks", "Автомобілі та автоцистерни", "Vehicles and fuel tanks"),
    ("special_equipment", "Спеціальна техніка", "Special equipment"),
    ("ground_robots", "Наземні робототехнічні комплекси", "Ground robotic systems"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--as-of-uk", required=True, help='e.g. "16 серпня 2026 року"')
    parser.add_argument("--as-of-en", required=True, help='e.g. "16 August 2026"')
    parser.add_argument("--source-name", required=True)
    parser.add_argument("--source-url", required=True)
    for key, _, label_en in CATEGORIES:
        parser.add_argument(f"--{key.replace('_', '-')}", type=int, required=True, help=label_en)
    args = parser.parse_args()

    now_iso = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    values = vars(args)

    totals = []
    for key, label_uk, label_en in CATEGORIES:
        value = values[key]
        if value < 0:
            parser.error(f"--{key.replace('_', '-')} must be >= 0, got {value}")
        totals.append({"key": key, "labelUk": label_uk, "labelEn": label_en, "value": value})

    # Losses are a running cumulative count; refuse an accidental regression
    # against whichever snapshot (asset or data/final) is currently on disk.
    for existing_path in (ASSET_OUTPUT, FINAL_OUTPUT):
        if existing_path.is_file():
            try:
                previous = json.loads(existing_path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                continue
            previous_by_key = {e["key"]: e["value"] for e in previous.get("totals", [])}
            for entry in totals:
                prev_value = previous_by_key.get(entry["key"])
                if prev_value is not None and entry["value"] < prev_value:
                    parser.error(
                        f"{entry['key']}: new value {entry['value']} is lower than the "
                        f"current {prev_value} in {existing_path.name} - refusing to publish "
                        "a regression. Pass the correct cumulative total."
                    )
            break

    payload = {
        "schemaVersion": 1,
        "generatedAt": now_iso,
        "asOfDateUk": args.as_of_uk,
        "asOfDateEn": args.as_of_en,
        "sourceName": args.source_name,
        "sourceUrl": args.source_url,
        "totals": totals,
    }

    for path in (FINAL_OUTPUT, ASSET_OUTPUT):
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"Wrote {path}")


if __name__ == "__main__":
    main()
