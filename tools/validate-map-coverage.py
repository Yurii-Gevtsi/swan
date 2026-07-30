#!/usr/bin/env python3
"""Validate that every event can be represented on the map.

The app renders grouped markers from map_event_groups.json and then falls back
to a single marker for any event not consumed by a group. This validator keeps
both layers honest: group definitions must reference real events, coordinate
events must be included in the generated group snapshot, and the simulated UI
must still expose every event after grouping.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


NAVAL_FLEET_CATEGORIES = {
    "MARITIME_ASSET_DISRUPTION",
    "NAVAL_VESSEL_DAMAGE",
    "NAVAL_VESSEL_LOSS",
}


def load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except FileNotFoundError:
        raise ValueError(f"{path}: file is missing")
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path}: invalid JSON - {exc}") from exc


def has_coordinates(event: dict[str, Any]) -> bool:
    return isinstance(event.get("lat"), (int, float)) and isinstance(event.get("lng"), (int, float))


def ids_from_group(group: dict[str, Any]) -> set[str]:
    ids = set(group.get("eventIds") or [])
    ids.update(group.get("occurrenceEventIds") or [])
    representative_id = group.get("representativeEventId")
    if representative_id:
        ids.add(representative_id)
    return ids


def simulate_visible_ids(event_ids: set[str], groups: list[dict[str, Any]]) -> set[str]:
    consumed_ids: set[str] = set()
    for group in groups:
        visible_ids = [event_id for event_id in group.get("eventIds", []) if event_id in event_ids]
        if visible_ids:
            consumed_ids.update(visible_ids)
    return consumed_ids | (event_ids - consumed_ids)


def validate_pair(events_path: Path, groups_path: Path) -> list[str]:
    errors: list[str] = []
    events_snapshot = load_json(events_path)
    groups_snapshot = load_json(groups_path)
    events = events_snapshot.get("events")
    groups = groups_snapshot.get("groups")

    if not isinstance(events, list):
        return [f"{events_path}: 'events' must be a list"]
    if not isinstance(groups, list):
        return [f"{groups_path}: 'groups' must be a list"]

    event_ids_in_order = [event.get("id") for event in events]
    duplicate_ids = [event_id for event_id, count in Counter(event_ids_in_order).items() if event_id and count > 1]
    if duplicate_ids:
        errors.append(f"{events_path}: duplicate event ids: {', '.join(sorted(duplicate_ids)[:12])}")

    event_by_id = {event.get("id"): event for event in events if event.get("id")}
    event_ids = set(event_by_id)
    coordinate_ids = {event_id for event_id, event in event_by_id.items() if has_coordinates(event)}
    missing_coordinates = sorted(event_ids - coordinate_ids)
    if missing_coordinates:
        errors.append(
            f"{events_path}: {len(missing_coordinates)} events have no numeric coordinates; "
            f"first ids: {', '.join(missing_coordinates[:12])}"
        )

    grouped_ids: set[str] = set()
    for group in groups:
        grouped_ids.update(ids_from_group(group))

    stale_refs = sorted(grouped_ids - event_ids)
    if stale_refs:
        errors.append(
            f"{groups_path}: {len(stale_refs)} group references point to missing events; "
            f"first ids: {', '.join(stale_refs[:12])}"
        )

    missing_from_groups = sorted(coordinate_ids - grouped_ids)
    if missing_from_groups:
        errors.append(
            f"{groups_path}: {len(missing_from_groups)} coordinate events are absent from group definitions; "
            f"first ids: {', '.join(missing_from_groups[:12])}"
        )

    visible_all = simulate_visible_ids(event_ids, groups)
    missing_from_ui = sorted(event_ids - visible_all)
    if missing_from_ui:
        errors.append(
            f"{events_path}: {len(missing_from_ui)} events are not visible in all-map simulation; "
            f"first ids: {', '.join(missing_from_ui[:12])}"
        )

    for category in sorted({event.get("category") for event in events if event.get("category")}):
        filtered_ids = {event["id"] for event in events if event.get("category") == category}
        missing_filtered = sorted(filtered_ids - simulate_visible_ids(filtered_ids, groups))
        if missing_filtered:
            errors.append(
                f"{events_path}: category {category} hides {len(missing_filtered)} events; "
                f"first ids: {', '.join(missing_filtered[:12])}"
            )

    naval_ids = {event["id"] for event in events if event.get("category") in NAVAL_FLEET_CATEGORIES}
    missing_naval = sorted(naval_ids - simulate_visible_ids(naval_ids, groups))
    if missing_naval:
        errors.append(
            f"{events_path}: naval fleet aggregate filter hides {len(missing_naval)} events; "
            f"first ids: {', '.join(missing_naval[:12])}"
        )

    if groups_snapshot.get("eventCount") != len(events):
        errors.append(
            f"{groups_path}: eventCount={groups_snapshot.get('eventCount')} but {events_path} has {len(events)} events"
        )
    if groups_snapshot.get("groupCount") != len(groups):
        errors.append(f"{groups_path}: groupCount={groups_snapshot.get('groupCount')} but groups={len(groups)}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate map marker coverage for final and bundled event data.")
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    pairs = [
        (repo_root / "data/final/osint_events.json", repo_root / "data/final/map_event_groups.json"),
        (repo_root / "app/src/main/assets/osint_events.json", repo_root / "app/src/main/assets/map_event_groups.json"),
    ]

    errors: list[str] = []
    for events_path, groups_path in pairs:
        try:
            pair_errors = validate_pair(events_path, groups_path)
        except ValueError as exc:
            pair_errors = [str(exc)]
        errors.extend(pair_errors)

    if errors:
        print("Map coverage validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Map coverage OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
