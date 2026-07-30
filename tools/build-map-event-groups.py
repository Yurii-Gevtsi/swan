#!/usr/bin/env python3
"""Build stable map-object groups and an audit report from app-ready events."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = REPO_ROOT / "data" / "final" / "osint_events.json"
DEFAULT_OUTPUT = REPO_ROOT / "data" / "final" / "map_event_groups.json"
DEFAULT_APP_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "map_event_groups.json"
DEFAULT_REPORT = REPO_ROOT / "data" / "final" / "map_event_groups_report.txt"

GENERIC_TOKENS = {
    "аеродром", "airport", "airfield", "база", "base", "військовий", "военный",
    "газовий", "газовый", "завод", "factory", "plant", "комплекс", "нафтобаза",
    "нефтебаза", "нафтопереробний", "нефтеперерабатывающий", "нпз", "об'єкт",
    "объект", "object", "підприємство", "предприятие", "промисловий", "industrial",
    "склад", "warehouse", "термінал", "терминал", "terminal", "центр", "center",
    "без", "назви", "нафтовидобувна", "платформа", "каспійське", "море", "родов",
    "родовище", "р-ще", "імені", "ім",
}

UNNAMED_SHADOW_FLEET_CLUSTERS = {
    "azov_sea_general": {
        "lat": 45.75,
        "lng": 36.90,
        "titleUk": "Безіменні судна тіньового флоту — Азовське море",
        "titleEn": "Unnamed shadow fleet vessels — Sea of Azov",
    },
    "black_sea_general": {
        "lat": 43.50,
        "lng": 34.00,
        "titleUk": "Безіменні судна тіньового флоту — Чорне море",
        "titleEn": "Unnamed shadow fleet vessels — Black Sea",
    },
}


def normalize_title(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "").lower().replace("ё", "е")
    value = value.replace("’", "'").replace("`", "'")
    value = re.sub(r"[^0-9a-zа-яіїєґ'\s]+", " ", value, flags=re.IGNORECASE)
    return re.sub(r"\s+", " ", value).strip()


def tokens(value: str) -> set[str]:
    return {token for token in normalize_title(value).split() if len(token) >= 2}


def distinctive(title_tokens: set[str]) -> set[str]:
    return {token for token in title_tokens if token not in GENERIC_TOKENS and len(token) >= 3}


def location_key(event: dict[str, Any]) -> str:
    return f"{float(event['lat']):.3f}|{float(event['lng']):.3f}"


def is_shadow_fleet(event: dict[str, Any]) -> bool:
    return (
        event.get("category") == "SHADOW_FLEET_DISRUPTION"
        or "SBS_SHADOW_FLEET_OPERATION" in str(event.get("impactTags") or "")
    )


def is_unnamed_shadow_fleet(event: dict[str, Any]) -> bool:
    if not is_shadow_fleet(event):
        return False
    title = normalize_title(f"{event.get('titleUk') or ''} {event.get('titleEn') or ''}")
    return any(marker in title for marker in ("без назви", "unnamed", "no name"))


def named_shadow_fleet_identity(event: dict[str, Any]) -> str | None:
    if not is_shadow_fleet(event) or is_unnamed_shadow_fleet(event):
        return None
    title = str(event.get("titleUk") or event.get("titleEn") or "")
    quoted = re.search(r"[«\"]([^»\"]+)[»\"]", title)
    if not quoted:
        return None
    return normalize_title(quoted.group(1)) or None


def title_score(event: dict[str, Any]) -> tuple[int, int, int, str]:
    title = str(event.get("titleUk") or event.get("titleEn") or "")
    title_tokens = tokens(title)
    source_count = len({item.strip() for item in str(event.get("sources") or "").split(",") if item.strip()})
    return (len(distinctive(title_tokens)), len(normalize_title(title)), source_count, str(event.get("id") or ""))


def strict_same_object(left: dict[str, Any], right: dict[str, Any]) -> bool:
    if location_key(left) != location_key(right):
        return False
    left_asset = str(left.get("assetId") or "").strip()
    right_asset = str(right.get("assetId") or "").strip()
    if left_asset and left_asset == right_asset:
        return True

    left_title = normalize_title(str(left.get("titleUk") or left.get("titleEn") or ""))
    right_title = normalize_title(str(right.get("titleUk") or right.get("titleEn") or ""))
    if not left_title or not right_title:
        return False
    if left_title == right_title:
        return True

    left_ordinal = re.search(r"#\s*(\d+)", str(left.get("titleUk") or left.get("titleEn") or ""))
    right_ordinal = re.search(r"#\s*(\d+)", str(right.get("titleUk") or right.get("titleEn") or ""))
    if left_ordinal and right_ordinal and left_ordinal.group(1) != right_ordinal.group(1):
        return False

    left_tokens = tokens(left_title)
    right_tokens = tokens(right_title)
    shared_distinctive = distinctive(left_tokens) & distinctive(right_tokens)
    if not shared_distinctive:
        return False

    shorter, longer = (left_tokens, right_tokens) if len(left_tokens) <= len(right_tokens) else (right_tokens, left_tokens)
    if shorter <= longer:
        return bool(distinctive(shorter))
    return False


def generic_alias(short_event: dict[str, Any], detailed_event: dict[str, Any]) -> bool:
    if short_event.get("date") != detailed_event.get("date"):
        return False
    short_tokens = tokens(str(short_event.get("titleUk") or short_event.get("titleEn") or ""))
    detailed_tokens = tokens(str(detailed_event.get("titleUk") or detailed_event.get("titleEn") or ""))
    if not short_tokens or short_tokens == detailed_tokens or not short_tokens < detailed_tokens:
        return False
    return not distinctive(short_tokens)


class DisjointSet:
    def __init__(self, size: int) -> None:
        self.parent = list(range(size))

    def find(self, item: int) -> int:
        while self.parent[item] != item:
            self.parent[item] = self.parent[self.parent[item]]
            item = self.parent[item]
        return item

    def union(self, left: int, right: int) -> None:
        left_root = self.find(left)
        right_root = self.find(right)
        if left_root != right_root:
            self.parent[right_root] = left_root


def build_groups(events: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[str]]:
    dsu = DisjointSet(len(events))

    unnamed_by_area: dict[str, list[int]] = defaultdict(list)
    named_vessels: dict[tuple[str, str], list[int]] = defaultdict(list)
    for index, event in enumerate(events):
        maritime_area_id = str(event.get("maritimeAreaId") or "")
        if is_unnamed_shadow_fleet(event) and maritime_area_id in UNNAMED_SHADOW_FLEET_CLUSTERS:
            unnamed_by_area[maritime_area_id].append(index)
            continue
        vessel_identity = named_shadow_fleet_identity(event)
        if vessel_identity:
            named_vessels[(maritime_area_id, vessel_identity)].append(index)

    for indexes in [*unnamed_by_area.values(), *named_vessels.values()]:
        for index in indexes[1:]:
            dsu.union(indexes[0], index)

    location_indexes: dict[str, list[int]] = defaultdict(list)
    for index, event in enumerate(events):
        location_indexes[location_key(event)].append(index)

    for indexes in location_indexes.values():
        for offset, left_index in enumerate(indexes):
            for right_index in indexes[offset + 1:]:
                if strict_same_object(events[left_index], events[right_index]):
                    dsu.union(left_index, right_index)

    ambiguous_aliases: list[str] = []
    initial_roots: dict[int, list[int]] = defaultdict(list)
    for index in range(len(events)):
        initial_roots[dsu.find(index)].append(index)

    # A generic same-day title such as "factory" may join a detailed object only
    # when there is exactly one plausible detailed group at that map position.
    for root, indexes in list(initial_roots.items()):
        representative_index = max(indexes, key=lambda item: title_score(events[item]))
        representative = events[representative_index]
        if distinctive(tokens(str(representative.get("titleUk") or representative.get("titleEn") or ""))):
            continue
        candidates: set[int] = set()
        for other_root, other_indexes in initial_roots.items():
            if other_root == root:
                continue
            other_representative = events[max(other_indexes, key=lambda item: title_score(events[item]))]
            if location_key(representative) != location_key(other_representative):
                continue
            if any(generic_alias(events[left], events[right]) for left in indexes for right in other_indexes):
                candidates.add(other_root)
        if len(candidates) == 1:
            dsu.union(root, next(iter(candidates)))
        elif len(candidates) > 1:
            ambiguous_aliases.append(
                f"{representative.get('date')} | {location_key(representative)} | "
                f"{representative.get('titleUk')} | candidates={len(candidates)}"
            )

    final_roots: dict[int, list[int]] = defaultdict(list)
    for index in range(len(events)):
        final_roots[dsu.find(index)].append(index)

    groups: list[dict[str, Any]] = []
    for indexes in final_roots.values():
        members = [events[index] for index in indexes]
        representative = max(members, key=title_score)
        cluster_area_id = str(representative.get("maritimeAreaId") or "")
        is_unnamed_cluster = len(members) > 1 and all(is_unnamed_shadow_fleet(member) for member in members)
        by_date: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for member in members:
            by_date[str(member.get("date") or "")].append(member)
        occurrence_events = [max(items, key=title_score) for _, items in sorted(by_date.items(), reverse=True)]
        if is_unnamed_cluster:
            cluster = UNNAMED_SHADOW_FLEET_CLUSTERS[cluster_area_id]
            group_id = f"map_group_unnamed_shadow_fleet_{cluster_area_id}"
            group_lat = cluster["lat"]
            group_lng = cluster["lng"]
            group_title_uk = cluster["titleUk"]
            group_title_en = cluster["titleEn"]
            occurrence_events = sorted(members, key=lambda item: (str(item.get("date") or ""), str(item.get("id") or "")), reverse=True)
            hit_count = len(members)
            aggregation_type = "UNNAMED_SHADOW_FLEET_COUNT"
        else:
            identity = f"{location_key(representative)}|{normalize_title(str(representative.get('titleUk') or representative.get('titleEn') or ''))}"
            group_id = "map_group_" + hashlib.sha1(identity.encode("utf-8")).hexdigest()[:16]
            group_lat = sum(float(member["lat"]) for member in members) / len(members)
            group_lng = sum(float(member["lng"]) for member in members) / len(members)
            group_title_uk = representative.get("titleUk") or representative.get("titleEn") or ""
            group_title_en = representative.get("titleEn") or representative.get("titleUk") or ""
            hit_count = len(occurrence_events)
            aggregation_type = None
        alias_count = 0 if is_unnamed_cluster else sum(max(0, len(items) - 1) for items in by_date.values())
        groups.append({
            "id": group_id,
            "representativeEventId": representative["id"],
            "eventIds": sorted(member["id"] for member in members),
            "occurrenceEventIds": [event["id"] for event in occurrence_events],
            "hitCount": hit_count,
            "aliasDuplicateCount": alias_count,
            "lat": group_lat,
            "lng": group_lng,
            "titleUk": group_title_uk,
            "titleEn": group_title_en,
            "aggregationType": aggregation_type,
        })

    groups.sort(key=lambda item: (-item["hitCount"], item["titleUk"], item["id"]))
    return groups, sorted(ambiguous_aliases)


def write_report(path: Path, events: list[dict[str, Any]], groups: list[dict[str, Any]], ambiguous: list[str]) -> None:
    repeated = [group for group in groups if group["hitCount"] > 1]
    aliases = [group for group in groups if group["aliasDuplicateCount"] > 0]
    lines = [
        "Map event grouping audit",
        f"Input events: {len(events)}",
        f"Object groups: {len(groups)}",
        f"Objects hit on multiple dates: {len(repeated)}",
        f"Same-day alias duplicates hidden on map: {sum(group['aliasDuplicateCount'] for group in aliases)}",
        f"Ambiguous generic aliases left separate: {len(ambiguous)}",
        "",
        "Objects hit on multiple dates:",
    ]
    lines.extend(
        f"- {group['hitCount']} hits | {group['titleUk']} | {group['lat']},{group['lng']}"
        for group in repeated
    )
    lines.extend(["", "Same-day alias duplicate groups:"])
    lines.extend(
        f"- {group['aliasDuplicateCount']} aliases | {group['titleUk']} | {group['lat']},{group['lng']}"
        for group in aliases
    )
    lines.extend(["", "Ambiguous generic aliases left separate:"])
    lines.extend(f"- {item}" for item in ambiguous)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--app-output", type=Path, default=DEFAULT_APP_OUTPUT)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    events = list(payload.get("events") or [])
    groups, ambiguous = build_groups(events)
    output = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "eventCount": len(events),
        "groupCount": len(groups),
        "groups": groups,
    }
    serialized = json.dumps(output, ensure_ascii=False, indent=2) + "\n"
    for output_path in (args.output, args.app_output):
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(serialized, encoding="utf-8")
    write_report(args.report, events, groups, ambiguous)
    print(f"Input events: {len(events)}")
    print(f"Map object groups: {len(groups)}")
    print(f"Objects hit on multiple dates: {sum(group['hitCount'] > 1 for group in groups)}")
    print(f"Same-day alias duplicates: {sum(group['aliasDuplicateCount'] for group in groups)}")
    print(f"Ambiguous aliases left separate: {len(ambiguous)}")


if __name__ == "__main__":
    main()
