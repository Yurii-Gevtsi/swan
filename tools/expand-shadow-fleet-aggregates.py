#!/usr/bin/env python3
"""Create generalized map records for the 20-vessel Black Sea SBS group."""

from __future__ import annotations

import copy
import json
from datetime import datetime, timezone
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
MANUAL_EVENTS = REPO_ROOT / "data" / "final" / "osint_events_manual_additions.json"
AGGREGATE_ID = "event_20260715_shadow_fleet_black_sea_group_001"
ITEM_ID_PREFIX = "event_20260715_shadow_fleet_black_sea_item_"

VESSEL_GROUPS = (
    ("oil_tanker", "Oil tanker", "нафтовий танкер", 17),
    ("gas_carrier", "Gas carrier", "газовоз", 2),
    ("tug", "Tug", "буксир", 1),
)


def main() -> None:
    root = json.loads(MANUAL_EVENTS.read_text(encoding="utf-8"))
    events = [
        event for event in root.get("events", [])
        if not str(event.get("id") or "").startswith(ITEM_ID_PREFIX)
    ]
    aggregate = next((event for event in events if event.get("id") == AGGREGATE_ID), None)
    if aggregate is None:
        raise RuntimeError(f"Missing aggregate event: {AGGREGATE_ID}")

    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    item_index = 0
    additions = []
    for kind, title_en, title_uk, count in VESSEL_GROUPS:
        for ordinal in range(1, count + 1):
            row, column = divmod(item_index, 5)
            event = copy.deepcopy(aggregate)
            event["id"] = f"{ITEM_ID_PREFIX}{item_index + 1:02d}_{kind}"
            event["titleEn"] = f"{title_en} (unnamed) #{ordinal} - Black Sea"
            event["titleUk"] = f"{title_uk} (без назви) #{ordinal} — Чорне море"
            event["lat"] = round(42.95 + row * 0.32 + (column % 2) * 0.035, 5)
            event["lng"] = round(32.75 + column * 0.62 + (row % 2) * 0.08, 5)
            event["radiusKm"] = 0
            event["precision"] = "MARITIME_REGIONAL"
            event["severity"] = "UNKNOWN"
            event["impactTags"] = (
                f"{aggregate.get('impactTags', '')}, SBS_SHADOW_FLEET_OPERATION, "
                "GENERALIZED_SEA_POINT, BLACK_SEA_GROUP_ITEM"
            ).strip(", ")
            event["safetyNotes"] = (
                "This is one item from the official 20-vessel aggregate. Its map point is a "
                "deliberately generalized display position in the central Black Sea, not an "
                "exact vessel or strike location."
            )
            event["updatedAt"] = generated_at
            additions.append(event)
            item_index += 1

    events.extend(additions)
    root["generatedAt"] = generated_at
    root["recordCount"] = len(events)
    root["events"] = sorted(
        events,
        key=lambda event: (str(event.get("date") or ""), str(event.get("id") or "")),
        reverse=True,
    )
    MANUAL_EVENTS.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Black Sea shadow-fleet item records: {len(additions)}")


if __name__ == "__main__":
    main()
