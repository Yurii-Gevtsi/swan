#!/usr/bin/env python3
"""Final pre-deploy validation of the site/data package.

Checks exactly what will be uploaded to hosting. ERRORS (exit 1) are problems
that would break the app or lose data: unparseable JSON, missing manifest
files, events missing required fields (the app's Moshi parser rejects the
whole snapshot in that case), duplicate ids, malformed dates/coordinates, or
a suspicious drop in event count, or Cyrillic mojibake (UTF-8 text that was
mis-decoded as cp1251/latin-1, or unrecoverable U+FFFD chars). WARNINGS are
values outside the app's enum
lists - such events display but do not match the corresponding filters.

Usage: validate-data-package.py --site-data <dir> [--min-events N]
"""

import argparse
import json
import re
import sys
from pathlib import Path

# Non-null EventEntity fields: if any is missing or null, the app's Moshi
# parser throws and the whole downloaded snapshot is rejected.
REQUIRED_EVENT_FIELDS = [
    "id", "status", "titleEn", "titleUk", "date", "datePrecision", "category",
    "eventScope", "theater", "approximateLocationLabelEn", "approximateLocationLabelUk",
    "lat", "lng", "radiusKm", "precision", "actor", "actorConfidence", "actorNote",
    "verificationStatus", "severity", "summaryEn", "summaryUk", "impactTags",
    "sources", "safetyNotes", "createdAt", "updatedAt",
]
NUMERIC_EVENT_FIELDS = {"lat", "lng", "radiusKm"}

# App enum values (Models.kt). Values outside these lists are warnings only:
# the app stores plain strings, so they render but do not match filter chips.
APP_CATEGORIES = {
    "INFRASTRUCTURE_DISRUPTION", "FUEL_SUPPLY_DISRUPTION", "REGIONAL_FISCAL_STRESS",
    "LOGISTICS_PRESSURE", "INDUSTRIAL_DISRUPTION", "PUBLIC_CASUALTY_DOCUMENTATION",
    "SANCTIONS_IMPACT", "OFFICIAL_STATEMENT", "ECONOMIC_INDICATOR_UPDATE",
    "MARITIME_ASSET_DISRUPTION", "MILITARY_ASSET_DISRUPTION", "NAVAL_VESSEL_DAMAGE",
    "NAVAL_VESSEL_LOSS", "SHADOW_FLEET_DISRUPTION", "SHADOW_FLEET_SANCTIONS",
    "VESSEL_SEIZURE_OR_DETENTION", "VESSEL_DEREGISTRATION", "PORT_LOGISTICS_DISRUPTION",
    "ENERGY_EXPORT_DISRUPTION", "CORRECTION", "RETRACTION",
}
APP_SEVERITIES = {"LOW", "MEDIUM", "HIGH", "SYSTEMIC", "UNKNOWN"}

# Signatures of UTF-8 text that was mis-decoded as cp1251/latin-1 (mojibake),
# plus the unrecoverable replacement char. These byte pairs do not occur in
# real Ukrainian/Russian prose, so any hit is a genuine encoding break. The
# legitimate "В»"/"С»" (Cyrillic letter before a closing guillemet) is
# deliberately excluded so real titles like «СПЛАВ» do not false-positive.
_MOJI_AFTER_R_S = (
    " ¤’‚„†‡…‰"
    "ЎўЋ“”•–—"
    "ѓѕџ™ЂЏ"
)
# UTF-8 text mis-decoded as cp1251/latin-1 (mojibake), plus the
# unrecoverable replacement char U+FFFD. These pairs do not occur in real
# Ukrainian/Russian prose. Legitimate "В»" (Cyrillic letter before a
# closing guillemet, e.g. «СПЛАВ») is excluded to avoid false positives.
MOJIBAKE = re.compile(
    "[РС][" + _MOJI_AFTER_R_S + "]"
    "|[ÐÃ][-¿]"
    "|�"
)


def mojibake_hits(value):
    return MOJIBAKE.findall(value or "")


errors = []
warnings = []


def load(base, name):
    path = base / name
    if not path.is_file():
        errors.append(f"{name}: missing from package")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        errors.append(f"{name}: invalid JSON - {exc}")
        return None


def validate_events(base, min_events):
    snapshot = load(base, "osint_events.json")
    if snapshot is None:
        return
    events = snapshot.get("events")
    if not isinstance(events, list) or not events:
        errors.append("osint_events.json: 'events' is empty or not a list")
        return
    if snapshot.get("recordCount") != len(events):
        errors.append(f"osint_events.json: recordCount={snapshot.get('recordCount')} but events={len(events)}")
    if min_events and len(events) < min_events:
        errors.append(
            f"osint_events.json: only {len(events)} events, previous publish had more "
            f"(threshold {min_events}) - possible data loss, refusing to publish")

    seen_ids = set()
    unknown_categories, unknown_severities = {}, {}
    mojibake_count = 0
    for index, event in enumerate(events):
        label = event.get("id") or f"events[{index}]"
        for field in REQUIRED_EVENT_FIELDS:
            value = event.get(field)
            if value is None:
                errors.append(f"{label}: required field '{field}' is missing or null (app rejects the whole file)")
            elif field in NUMERIC_EVENT_FIELDS and not isinstance(value, (int, float)):
                errors.append(f"{label}: '{field}' must be a number, got {type(value).__name__}")
            elif isinstance(value, str) and mojibake_hits(value):
                mojibake_count += 1
                if mojibake_count <= 8:
                    errors.append(f"{label}: '{field}' contains corrupted (mojibake) text: {value[:50]!r}")
        event_id = event.get("id")
        if event_id in seen_ids:
            errors.append(f"{label}: duplicate event id")
        seen_ids.add(event_id)
        date = str(event.get("date") or "")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
            errors.append(f"{label}: date '{date}' is not YYYY-MM-DD")
        lat, lng = event.get("lat"), event.get("lng")
        if isinstance(lat, (int, float)) and not -90 <= lat <= 90:
            errors.append(f"{label}: lat {lat} out of range")
        if isinstance(lng, (int, float)) and not -180 <= lng <= 180:
            errors.append(f"{label}: lng {lng} out of range")
        category = str(event.get("category"))
        if category not in APP_CATEGORIES:
            unknown_categories[category] = unknown_categories.get(category, 0) + 1
        severity = str(event.get("severity"))
        if severity not in APP_SEVERITIES:
            unknown_severities[severity] = unknown_severities.get(severity, 0) + 1

    if mojibake_count > 8:
        errors.append(f"...and {mojibake_count - 8} more fields with corrupted (mojibake) text")

    for value, count in sorted(unknown_categories.items()):
        warnings.append(f"category '{value}' is outside app enums ({count} events) - not matched by category filter")
    for value, count in sorted(unknown_severities.items()):
        warnings.append(f"severity '{value}' is outside app enums ({count} events) - not matched by severity filter")

    sources_snapshot = load(base, "wikipedia_citation_sources.json")
    if sources_snapshot is not None:
        scan_mojibake("wikipedia_citation_sources.json", sources_snapshot.get("sources") or [])
        source_list = sources_snapshot.get("sources") or []
        ids = [s.get("sourceId") for s in source_list]
        if len(set(ids)) != len(ids):
            errors.append("wikipedia_citation_sources.json: duplicate sourceIds")
        for s in source_list:
            if not s.get("sourceUrl"):
                warnings.append(f"source {s.get('sourceId')}: empty sourceUrl")
        known = set(ids)
        missing_refs = {}
        for event in events:
            for source_id in [x.strip() for x in str(event.get("sources") or "").split(",") if x.strip()]:
                if source_id not in known:
                    missing_refs[source_id] = missing_refs.get(source_id, 0) + 1
        # Some ids are bundled in the app itself (SampleOsintData), so this is a warning.
        for source_id, count in sorted(missing_refs.items()):
            warnings.append(f"source id '{source_id}' referenced by {count} events but absent from citation file")


def scan_mojibake(name, obj, limit=5):
    """Recursively flag any mojibake-corrupted string in a loaded JSON tree."""
    found = [0]

    def walk(node):
        if isinstance(node, dict):
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
        elif isinstance(node, str) and mojibake_hits(node):
            found[0] += 1
            if found[0] <= limit:
                errors.append(f"{name}: corrupted (mojibake) text: {node[:50]!r}")

    walk(obj)
    if found[0] > limit:
        errors.append(f"{name}: ...and {found[0] - limit} more corrupted strings")


def validate_simple(base, name, list_key, expected=None):
    snapshot = load(base, name)
    if snapshot is None:
        return
    items = snapshot.get(list_key)
    if not isinstance(items, list) or not items:
        errors.append(f"{name}: '{list_key}' is empty or not a list")
    elif expected is not None and len(items) != expected:
        errors.append(f"{name}: expected {expected} {list_key}, got {len(items)}")
    scan_mojibake(name, items)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site-data", type=Path, required=True)
    parser.add_argument("--min-events", type=int, default=0,
                        help="Fail if the events snapshot has fewer records than this")
    args = parser.parse_args()
    base = args.site_data

    manifest = load(base, "manifest.json")
    if manifest is not None:
        for field in ("schemaVersion", "dataVersion", "generatedAt", "files"):
            if not manifest.get(field):
                errors.append(f"manifest.json: '{field}' is missing or empty")
        for name in manifest.get("files") or []:
            if not (base / name).is_file():
                errors.append(f"manifest.json lists '{name}' but it is missing from the package")

    validate_events(base, args.min_events)
    validate_simple(base, "map_event_groups.json", "groups")
    validate_simple(base, "region_attack_totals_2026.json", "regions")
    validate_simple(base, "fuel_shortage_regions_2026.json", "regions", expected=83)
    validate_simple(base, "regional_budget_stress_2026.json", "regions", expected=83)

    for line in warnings:
        print(f"  WARN  {line}")
    if errors:
        print(f"PACKAGE VALIDATION FAILED: {len(errors)} error(s)")
        for line in errors:
            print(f"  ERROR {line}")
        return 1
    print(f"Package OK ({len(warnings)} warnings).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
