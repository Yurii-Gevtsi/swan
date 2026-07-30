#!/usr/bin/env python3
"""Import a weekly raw-strike input file into the app data pipeline.

Reads a simple weekly input JSON (see data/weekly/AGENT_INSTRUCTIONS.md for the
schema), converts each entry into a full Room EventEntity record, registers new
sources, and upserts everything into:

  data/final/osint_events_manual_additions.json   (events; picked up by finalize-data.ps1)
  data/final/wikipedia_citation_sources.json      (sources)
  app/src/main/assets/wikipedia_citation_sources.json

Entries that duplicate an already known event (same date + Ukrainian title) are
skipped and reported. Run finalize-data.ps1 afterwards (publish-weekly.ps1 does
both).
"""

import argparse
import datetime as dt
import json
import re
import sys
import unicodedata
from pathlib import Path

import category_taxonomy as tax

REPO_ROOT = Path(__file__).resolve().parent.parent
MANUAL_ADDITIONS = REPO_ROOT / "data" / "final" / "osint_events_manual_additions.json"
FINAL_EVENTS = REPO_ROOT / "data" / "final" / "osint_events.json"
FINAL_SOURCES = REPO_ROOT / "data" / "final" / "wikipedia_citation_sources.json"
ASSET_SOURCES = REPO_ROOT / "app" / "src" / "main" / "assets" / "wikipedia_citation_sources.json"

CATEGORIES = {
    "INFRASTRUCTURE_DISRUPTION", "FUEL_SUPPLY_DISRUPTION", "REGIONAL_FISCAL_STRESS",
    "LOGISTICS_PRESSURE", "INDUSTRIAL_DISRUPTION", "PUBLIC_CASUALTY_DOCUMENTATION",
    "SANCTIONS_IMPACT", "OFFICIAL_STATEMENT", "ECONOMIC_INDICATOR_UPDATE",
    "MARITIME_ASSET_DISRUPTION", "MILITARY_ASSET_DISRUPTION", "NAVAL_VESSEL_DAMAGE",
    "NAVAL_VESSEL_LOSS", "SHADOW_FLEET_DISRUPTION", "SHADOW_FLEET_SANCTIONS",
    "VESSEL_SEIZURE_OR_DETENTION", "VESSEL_DEREGISTRATION", "PORT_LOGISTICS_DISRUPTION",
    "ENERGY_EXPORT_DISRUPTION", "CORRECTION", "RETRACTION",
}

# Convenience aliases so hand-written weekly rows can use plain Ukrainian words.
CATEGORY_ALIASES = {
    "флот": "SHADOW_FLEET_DISRUPTION",
    "тіньовий флот": "SHADOW_FLEET_DISRUPTION",
    "танкер": "SHADOW_FLEET_DISRUPTION",
    "вмф": "NAVAL_VESSEL_DAMAGE",
    "корабель": "NAVAL_VESSEL_DAMAGE",
    "паливо": "FUEL_SUPPLY_DISRUPTION",
    "нпз": "INFRASTRUCTURE_DISRUPTION",
    "інфраструктура": "INFRASTRUCTURE_DISRUPTION",
    "завод": "INDUSTRIAL_DISRUPTION",
    "промисловість": "INDUSTRIAL_DISRUPTION",
    "санкції": "SANCTIONS_IMPACT",
    "порт": "PORT_LOGISTICS_DISRUPTION",
    "енергоекспорт": "ENERGY_EXPORT_DISRUPTION",
    "логістика": "LOGISTICS_PRESSURE",
    "військовий об'єкт": "MILITARY_ASSET_DISRUPTION",
    "аеродром": "MILITARY_ASSET_DISRUPTION",
    "бюджет": "REGIONAL_FISCAL_STRESS",
}

# (eventScope, theater, precision) defaults per category; theater/precision are
# only used when the entry does not provide its own values.
CATEGORY_DEFAULTS = {
    "INFRASTRUCTURE_DISRUPTION": ("TERRITORIAL_RUSSIA", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "FUEL_SUPPLY_DISRUPTION": ("REGIONAL_INDICATOR", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "REGIONAL_FISCAL_STRESS": ("REGIONAL_INDICATOR", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "LOGISTICS_PRESSURE": ("LOGISTICS_ROUTE", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "INDUSTRIAL_DISRUPTION": ("TERRITORIAL_RUSSIA", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "PUBLIC_CASUALTY_DOCUMENTATION": ("TERRITORIAL_RUSSIA", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "SANCTIONS_IMPACT": ("SANCTIONS_ENFORCEMENT", "SANCTIONS_JURISDICTION", "SANCTIONS_JURISDICTION"),
    "OFFICIAL_STATEMENT": ("ECONOMIC_INDICATOR", "UNKNOWN_OR_GENERAL", "REGION_LEVEL"),
    "ECONOMIC_INDICATOR_UPDATE": ("ECONOMIC_INDICATOR", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "MARITIME_ASSET_DISRUPTION": ("MARITIME", "BLACK_SEA", "MARITIME_REGIONAL"),
    "MILITARY_ASSET_DISRUPTION": ("MILITARY_ASSET", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "NAVAL_VESSEL_DAMAGE": ("MILITARY_ASSET", "BLACK_SEA", "MARITIME_REGIONAL"),
    "NAVAL_VESSEL_LOSS": ("MILITARY_ASSET", "BLACK_SEA", "MARITIME_REGIONAL"),
    "SHADOW_FLEET_DISRUPTION": ("MARITIME", "BLACK_SEA", "MARITIME_REGIONAL"),
    "SHADOW_FLEET_SANCTIONS": ("SANCTIONS_ENFORCEMENT", "GLOBAL_SHIPPING", "GLOBAL_SHIPPING_GENERAL"),
    "VESSEL_SEIZURE_OR_DETENTION": ("SANCTIONS_ENFORCEMENT", "EUROPEAN_WATERS", "MARITIME_REGIONAL"),
    "VESSEL_DEREGISTRATION": ("SANCTIONS_ENFORCEMENT", "GLOBAL_SHIPPING", "GLOBAL_SHIPPING_GENERAL"),
    "PORT_LOGISTICS_DISRUPTION": ("PORT_INFRASTRUCTURE", "BLACK_SEA", "MARITIME_REGIONAL"),
    "ENERGY_EXPORT_DISRUPTION": ("ENERGY_EXPORT", "RUSSIA_INTERNAL", "REGION_LEVEL"),
    "CORRECTION": ("ECONOMIC_INDICATOR", "UNKNOWN_OR_GENERAL", "REGION_LEVEL"),
    "RETRACTION": ("ECONOMIC_INDICATOR", "UNKNOWN_OR_GENERAL", "REGION_LEVEL"),
}

SEVERITIES = {"LOW", "MEDIUM", "HIGH", "SYSTEMIC", "UNKNOWN"}
VERIFICATION_STATUSES = {
    "OFFICIAL_CONFIRMED", "INSTITUTIONAL_CONFIRMED", "MULTI_SOURCE_CONFIRMED",
    "OFFICIAL_STATEMENT_ONLY", "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
    "ECONOMIC_DATA_CONFIRMED", "UPDATED", "CORRECTED", "RETRACTED",
}

TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "h", "ґ": "g", "д": "d", "е": "e", "є": "ie",
    "ж": "zh", "з": "z", "и": "y", "і": "i", "ї": "i", "й": "i", "к": "k", "л": "l",
    "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "kh", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "shch", "ь": "",
    "ю": "iu", "я": "ia", "ё": "e", "ъ": "", "ы": "y", "э": "e",
}


def slugify(text, max_len=40):
    text = text.lower()
    text = "".join(TRANSLIT.get(ch, ch) for ch in text)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-z0-9]+", "_", text).strip("_")
    return text[:max_len].rstrip("_") or "event"


def load_json(path):
    with open(path, encoding="utf-8-sig") as handle:
        return json.load(handle)


def save_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def normalize_category(raw):
    if not raw:
        return None
    value = str(raw).strip()
    if value.upper() in CATEGORIES:
        return value.upper()
    return CATEGORY_ALIASES.get(value.lower())


def dedupe_key(date, title_uk):
    return f"{date}||{(title_uk or '').strip().lower()}"


# Secondary dedup: catches the same event phrased differently across an
# overlapping collection ("НПЗ у Рязані" vs "Рязанський НПЗ"). Two entries are
# the same when they share a day, sit within FUZZY_KM of each other, and share
# a distinctive title word — proximity alone is not enough because different
# objects share an oblast-centre coordinate.
FUZZY_KM = 25.0
_GENERIC_TOKENS = {
    "нпз", "завод", "база", "нафтобаза", "станція", "склад", "термінал", "об", "єкт",
    "обєкт", "судно", "танкер", "міст", "море", "моря", "область", "обл", "край",
    "район", "без", "назви", "рф", "росії", "россии", "унаслідок", "внаслідок",
    "the", "and", "near", "oblast", "region", "plant", "depot", "station", "ship",
    "vessel", "tanker", "refinery", "bridge", "unnamed", "russian", "russia",
}


def title_tokens(*values):
    text = " ".join(v or "" for v in values).lower()
    text = re.sub(r"[^0-9a-zа-яіїєґё'\s-]+", " ", text)
    return {
        tok for tok in re.split(r"[\s'-]+", text)
        if len(tok) >= 4 and tok not in _GENERIC_TOKENS
    }


def haversine_km(lat1, lng1, lat2, lng2):
    import math
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def is_fuzzy_duplicate(date, lat, lng, tokens, category, index_by_date):
    # Same day + same canonical category + close + a shared title word.
    # The category guard stops a refinery and a substation in the same city
    # (which share the place name) from being merged.
    for elat, elng, etokens, ecategory in index_by_date.get(date, ()):
        if (ecategory == category and tokens & etokens
                and haversine_km(lat, lng, elat, elng) <= FUZZY_KM):
            return True
    return False


def build_source(entry, today):
    source = entry.get("source") or {}
    url = (source.get("url") or "").strip()
    if not url:
        return None
    publisher = source.get("publisher") or source.get("name") or "Unknown publisher"
    date_part = (entry.get("date") or today).replace("-", "_")
    return {
        "sourceId": f"source_{slugify(publisher, 24)}_{date_part}",
        "sourceName": source.get("name") or f"{publisher} report, {entry.get('date') or today}",
        "publisher": publisher,
        "sourceType": source.get("type") or "MEDIA_REPORT",
        "country": source.get("country") or "Ukraine",
        "language": source.get("language") or "uk",
        "reliabilityScore": int(source.get("reliabilityScore") or 3),
        "lastChecked": today,
        "usedInRecordsCount": 0,
        "sourceDescription": source.get("description")
            or "Weekly OSINT intake source; verify against the original publication.",
        "allowedUse": "Public citation with attribution.",
        "reliabilityNote": source.get("reliabilityNote")
            or "Registered through the weekly intake pipeline.",
        "sourceUrl": url,
    }


def build_event(entry, event_id, source_ids, now_iso):
    category = normalize_category(entry.get("category"))
    scope_default, theater_default, precision_default = CATEGORY_DEFAULTS[category]

    title_uk = (entry.get("objectNameUk") or entry.get("objectName") or "").strip()
    title_en = (entry.get("objectNameEn") or title_uk).strip()

    # Collapse to one of the six canonical map categories (keyword-aware, so an
    # oil refinery tagged INDUSTRIAL becomes OIL and a tanker becomes shadow fleet).
    canonical = tax.canonical_category(
        category, text=f"{title_uk} {title_en} {entry.get('impactTags') or ''}")
    location_uk = (entry.get("locationLabelUk") or "Російська Федерація (узагальнено)").strip()
    location_en = (entry.get("locationLabelEn") or location_uk).strip()

    severity = str(entry.get("severity") or "MEDIUM").upper()
    if severity not in SEVERITIES:
        severity = "MEDIUM"
    verification = str(entry.get("verificationStatus")
                       or "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE").upper()
    if verification not in VERIFICATION_STATUSES:
        verification = "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE"

    return {
        "id": event_id,
        "status": "PUBLISHED",
        "titleEn": title_en,
        "titleUk": title_uk,
        "date": entry["date"],
        "datePrecision": "DAY",
        "category": canonical,
        "eventScope": entry.get("eventScope") or scope_default,
        "theater": entry.get("theater") or theater_default,
        "regionId": entry.get("regionId"),
        "federalDistrictId": entry.get("federalDistrictId"),
        "maritimeAreaId": entry.get("maritimeAreaId"),
        "sanctionsJurisdictionId": entry.get("sanctionsJurisdictionId"),
        "approximateLocationLabelEn": location_en,
        "approximateLocationLabelUk": location_uk,
        "lat": float(entry["lat"]),
        "lng": float(entry["lng"]),
        "radiusKm": int(entry.get("radiusKm") or 35),
        "precision": entry.get("precision") or precision_default,
        "assetId": entry.get("assetId"),
        "actor": entry.get("actor") or "UNATTRIBUTED",
        "actorConfidence": entry.get("actorConfidence") or "MEDIA_REPORTED",
        "actorNote": entry.get("actorNote") or "",
        "verificationStatus": verification,
        "severity": severity,
        "summaryEn": entry.get("summaryEn") or entry.get("summaryUk") or title_en,
        "summaryUk": entry.get("summaryUk") or title_uk,
        "impactTags": entry.get("impactTags") or category.replace("_", " ").title(),
        "sources": ", ".join(source_ids),
        "safetyNotes": entry.get("safetyNotes")
            or "Location generalized. No exact coordinates or tactical details.",
        "createdAt": now_iso,
        "updatedAt": now_iso,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="Weekly input JSON file")
    parser.add_argument("--dry-run", action="store_true", help="Validate and report only")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.is_file():
        sys.exit(f"Input file not found: {input_path}")

    payload = load_json(input_path)
    entries = payload.get("entries") or []
    if not entries:
        sys.exit("Input file has no 'entries'.")

    now = dt.datetime.now(dt.timezone.utc)
    now_iso = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    today = now.strftime("%Y-%m-%d")

    manual = load_json(MANUAL_ADDITIONS) if MANUAL_ADDITIONS.is_file() else {
        "schemaVersion": 1, "generatedAt": now_iso, "sourceFile": "weekly_intake",
        "importTarget": "Room EventEntity", "recordCount": 0, "skippedCount": 0, "events": [],
    }
    known_keys = {dedupe_key(e.get("date"), e.get("titleUk")) for e in manual.get("events", [])}
    known_ids = {e.get("id") for e in manual.get("events", [])}

    # Per-date spatial/token index for the fuzzy duplicate check.
    index_by_date = {}

    def index_event(event):
        try:
            lat, lng = float(event["lat"]), float(event["lng"])
        except (KeyError, TypeError, ValueError):
            return
        tokens = title_tokens(event.get("titleUk"), event.get("titleEn"))
        if tokens:
            cat = tax.canonical_category(event.get("category"), event=event)
            index_by_date.setdefault(str(event.get("date")), []).append((lat, lng, tokens, cat))

    for event in manual.get("events", []):
        index_event(event)
    if FINAL_EVENTS.is_file():
        for event in load_json(FINAL_EVENTS).get("events", []):
            known_keys.add(dedupe_key(event.get("date"), event.get("titleUk")))
            known_ids.add(event.get("id"))
            index_event(event)

    sources_snapshot = load_json(FINAL_SOURCES) if FINAL_SOURCES.is_file() else {
        "schemaVersion": 1, "recordCount": 0, "sources": [],
    }
    sources_by_url = {s.get("sourceUrl"): s for s in sources_snapshot.get("sources", [])}
    sources_by_id = {s.get("sourceId"): s for s in sources_snapshot.get("sources", [])}

    added, skipped, errors = [], [], []
    for index, entry in enumerate(entries, start=1):
        label = entry.get("objectNameUk") or entry.get("objectName") or f"entry #{index}"

        category = normalize_category(entry.get("category"))
        if category is None:
            errors.append(f"{label}: unknown category '{entry.get('category')}'")
            continue
        entry["category"] = category

        date = str(entry.get("date") or "").strip()
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
            errors.append(f"{label}: date must be YYYY-MM-DD, got '{date}'")
            continue
        entry["date"] = date

        if not (entry.get("objectNameUk") or entry.get("objectName")):
            errors.append(f"entry #{index}: objectNameUk is required")
            continue
        if entry.get("lat") is None or entry.get("lng") is None:
            errors.append(f"{label}: lat/lng are required for the map")
            continue

        key = dedupe_key(date, entry.get("objectNameUk") or entry.get("objectName"))
        if key in known_keys:
            skipped.append(f"{label} ({date}): already present, skipped")
            continue

        entry_tokens = title_tokens(
            entry.get("objectNameUk"), entry.get("objectName"), entry.get("objectNameEn"))
        entry_canonical = tax.canonical_category(
            category,
            text=f"{entry.get('objectNameUk') or ''} {entry.get('objectNameEn') or ''} {entry.get('impactTags') or ''}")
        if entry_tokens and is_fuzzy_duplicate(
                date, float(entry["lat"]), float(entry["lng"]), entry_tokens, entry_canonical, index_by_date):
            skipped.append(f"{label} ({date}): near-duplicate of an existing event, skipped")
            continue

        new_source = build_source(entry, today)
        source_ids = []
        if new_source is not None:
            existing = sources_by_url.get(new_source["sourceUrl"])
            if existing is not None:
                existing["usedInRecordsCount"] = int(existing.get("usedInRecordsCount") or 0) + 1
                existing["lastChecked"] = today
                source_ids.append(existing["sourceId"])
            else:
                base_id = new_source["sourceId"]
                suffix = 2
                while new_source["sourceId"] in sources_by_id:
                    new_source["sourceId"] = f"{base_id}_{suffix}"
                    suffix += 1
                new_source["usedInRecordsCount"] = 1
                sources_snapshot["sources"].append(new_source)
                sources_by_url[new_source["sourceUrl"]] = new_source
                sources_by_id[new_source["sourceId"]] = new_source
                source_ids.append(new_source["sourceId"])
        else:
            errors.append(f"{label}: source.url is required")
            continue

        base_event_id = f"event_{date.replace('-', '')}_wk_{slugify(entry.get('objectNameEn') or label)}"
        event_id = base_event_id
        suffix = 2
        while event_id in known_ids:
            event_id = f"{base_event_id}_{suffix}"
            suffix += 1

        event = build_event(entry, event_id, source_ids, now_iso)
        manual["events"].append(event)
        known_keys.add(key)
        known_ids.add(event_id)
        if entry_tokens:
            index_by_date.setdefault(date, []).append(
                (float(entry["lat"]), float(entry["lng"]), entry_tokens, entry_canonical))
        added.append(f"{event_id}: {label} ({date})")

    print(f"Input entries: {len(entries)}")
    print(f"Added events:  {len(added)}")
    for line in added:
        print(f"  + {line}")
    if skipped:
        print(f"Skipped (duplicates): {len(skipped)}")
        for line in skipped:
            print(f"  = {line}")
    if errors:
        print(f"Errors: {len(errors)}")
        for line in errors:
            print(f"  ! {line}")

    if args.dry_run:
        print("Dry run: nothing written.")
        return 1 if errors else 0

    if errors and not added:
        sys.exit("All entries failed validation; nothing written.")

    manual["recordCount"] = len(manual["events"])
    manual["generatedAt"] = now_iso
    save_json(MANUAL_ADDITIONS, manual)
    print(f"Updated {MANUAL_ADDITIONS}")

    sources_snapshot["recordCount"] = len(sources_snapshot["sources"])
    save_json(FINAL_SOURCES, sources_snapshot)
    save_json(ASSET_SOURCES, sources_snapshot)
    print(f"Updated {FINAL_SOURCES}")
    print(f"Updated {ASSET_SOURCES}")

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
