#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


REPO_ROOT = Path(__file__).resolve().parents[1]
FINAL_DIR = REPO_ROOT / "data" / "final"
BASELINE_FILE = REPO_ROOT / "data" / "res2.json.txt"
MANUAL_NAVAL_FILE = FINAL_DIR / "impact_registry_manual_additions.json"
MANUAL_EVENTS_FILE = FINAL_DIR / "osint_events_manual_additions.json"
SOURCES_FILE = FINAL_DIR / "wikipedia_citation_sources.json"
APP_SOURCES_FILE = REPO_ROOT / "app" / "src" / "main" / "assets" / "wikipedia_citation_sources.json"

NAVAL_CATEGORIES = {"NAVAL_VESSEL_DAMAGE", "NAVAL_VESSEL_LOSS", "MARITIME_ASSET_DISRUPTION"}
FALLBACK_SOURCE_ID = "source_wiki_naval_losses_uk"
FALLBACK_SOURCE_URL = (
    "https://uk.wikipedia.org/wiki/"
    "%D0%A1%D0%BF%D0%B8%D1%81%D0%BE%D0%BA_%D0%B2%D1%82%D1%80%D0%B0%D1%82_"
    "%D0%BA%D0%BE%D1%80%D0%B0%D0%B1%D0%BB%D1%96%D0%B2_%D0%BF%D1%96%D0%B4_"
    "%D1%87%D0%B0%D1%81_%D1%80%D0%BE%D1%81%D1%96%D0%B9%D1%81%D1%8C%D0%BA%D0%BE-"
    "%D1%83%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D1%81%D1%8C%D0%BA%D0%BE%D1%97_"
    "%D0%B2%D1%96%D0%B9%D0%BD%D0%B8#%D0%92%D0%9C%D0%A1_%D0%A0%D0%BE%D1%81%D1%96%D1%97_3"
)

SOURCE_REGISTRY_OVERRIDES = {
    "source_mod_ua_black_azov_fleet_losses_2026_04_17": {
        "sourceName": "Ministry of Defence of Ukraine fleet losses overview",
        "publisher": "Ministry of Defence of Ukraine",
        "sourceType": "OFFICIAL_UA_STATE",
        "country": "Ukraine",
        "language": "uk",
        "reliabilityScore": 5,
        "lastChecked": "2026-07-19",
        "sourceDescription": "Official Ukrainian overview of Russian Black Sea and Azov fleet losses.",
        "sourceUrl": "https://mod.gov.ua/news/flot-shcho-yde-na-dno-naibilshi-vtraty-rosii-u-chornomu-ta-azovskomu-moriakh-za-chas-viiny",
    },
    "source_nv_ua_russian_ships_sunk_list_2024_02_14": {
        "sourceName": "NV list of Russian ships sunk or damaged by Ukraine",
        "publisher": "NV",
        "sourceType": "MEDIA_REPORT",
        "country": "Ukraine",
        "language": "uk",
        "reliabilityScore": 4,
        "lastChecked": "2026-07-19",
        "sourceDescription": "NV overview of Russian vessels reported sunk or damaged by Ukraine.",
        "sourceUrl": "https://nv.ua/ukr/ukraine/events/bdk-cezar-kunikov-minsk-novocherkask-saratov-kater-ivanovec-usi-korabli-zatopleni-ukrajinoyu-50366629.html",
    },
    FALLBACK_SOURCE_ID: {
        "sourceName": "Wikipedia list of ship losses during the Russo-Ukrainian War",
        "publisher": "Wikipedia",
        "sourceType": "WIKIPEDIA_FALLBACK",
        "country": "Ukraine",
        "language": "uk",
        "reliabilityScore": 3,
        "lastChecked": "2026-07-19",
        "sourceDescription": "Fallback source for naval rows whose table entry has no separate reference link.",
        "sourceUrl": FALLBACK_SOURCE_URL,
    },
}

LOCATION_POINTS: list[tuple[re.Pattern[str], dict[str, Any]]] = [
    (re.compile(r"novorossiysk", re.I), {"lat": 44.72, "lng": 37.77, "theater": "BLACK_SEA", "maritimeAreaId": "novorossiysk_maritime_area", "labelEn": "Novorossiysk maritime area", "labelUk": "Морський район Новоросійська"}),
    (re.compile(r"primorsk|saint petersburg|st\.? petersburg|baltic", re.I), {"lat": 60.35, "lng": 28.61, "theater": "BALTIC_SEA", "maritimeAreaId": None, "labelEn": "Baltic Sea / Primorsk area", "labelUk": "Балтійське море, район Приморська"}),
    (re.compile(r"caspian|kaspi", re.I), {"lat": 43.00, "lng": 50.00, "theater": "CASPIAN_REGION", "maritimeAreaId": None, "labelEn": "Caspian Sea", "labelUk": "Каспійське море"}),
    (re.compile(r"onega|karelia", re.I), {"lat": 61.80, "lng": 35.30, "theater": "RUSSIA_INTERNAL", "maritimeAreaId": None, "labelEn": "Lake Onega, Karelia", "labelUk": "Онезьке озеро, Карелія"}),
    (re.compile(r"volga-don", re.I), {"lat": 48.62, "lng": 43.50, "theater": "RUSSIA_INTERNAL", "maritimeAreaId": None, "labelEn": "Volga-Don Canal", "labelUk": "Волго-Донський канал"}),
    (re.compile(r"kerch", re.I), {"lat": 45.36, "lng": 36.47, "theater": "BLACK_SEA", "maritimeAreaId": "kerch_strait_maritime_area", "labelEn": "Kerch Strait maritime area", "labelUk": "Морський район Керченської протоки"}),
    (re.compile(r"azov|mariupol", re.I), {"lat": 46.00, "lng": 37.00, "theater": "AZOV_SEA", "maritimeAreaId": "azov_sea_general", "labelEn": "Azov Sea", "labelUk": "Азовське море"}),
    (re.compile(r"zmiinyi|odesa|kobleve|dnipro-buh|sevastopol|crimea|inkerman|panske|saky|black sea|ukrainian coastal waters", re.I), {"lat": 43.50, "lng": 34.00, "theater": "BLACK_SEA", "maritimeAreaId": "black_sea_general", "labelEn": "Black Sea", "labelUk": "Чорне море"}),
]


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def slug(value: str) -> str:
    normalized = re.sub(r"[^0-9a-zA-Z]+", "_", value).strip("_").lower()
    return normalized[:64] or hashlib.sha1(value.encode("utf-8")).hexdigest()[:12]


def source_id_for_url(url: str) -> str:
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:20]
    return f"source_naval_reference_{digest}"


def publisher_from_url(url: str) -> str:
    host = urlparse(url).netloc.lower().removeprefix("www.")
    return host or "Unknown"


def source_record(source_id: str, *, url: str, name: str | None = None, source_type: str = "MEDIA_REFERENCE", reliability: int = 3) -> dict[str, Any]:
    override = SOURCE_REGISTRY_OVERRIDES.get(source_id)
    if override:
        record = {"sourceId": source_id, **override}
    else:
        publisher = publisher_from_url(url)
        record = {
            "sourceId": source_id,
            "sourceName": name or f"Naval loss reference from {publisher}",
            "publisher": publisher,
            "sourceType": source_type,
            "country": "Unknown",
            "language": "uk" if ".ua" in publisher or "uk.wikipedia" in url else "unknown",
            "reliabilityScore": reliability,
            "lastChecked": "2026-07-19",
            "sourceDescription": "Source preserved from the naval losses reconciliation.",
            "sourceUrl": url,
        }
    record.setdefault("usedInRecordsCount", 1)
    record.setdefault("allowedUse", "Public citation with attribution.")
    record.setdefault("reliabilityNote", "Imported for naval vessel map coverage; verify against the original publication for full context.")
    return record


def ensure_sources(source_ids: list[str], source_urls: list[str], source_registry: dict[str, dict[str, Any]]) -> None:
    for source_id in source_ids:
        if source_id in SOURCE_REGISTRY_OVERRIDES:
            source_registry[source_id] = source_record(source_id, url=SOURCE_REGISTRY_OVERRIDES[source_id]["sourceUrl"])

    if not source_ids and not source_urls:
        source_registry[FALLBACK_SOURCE_ID] = source_record(FALLBACK_SOURCE_ID, url=FALLBACK_SOURCE_URL)

    for url in source_urls:
        source_id = source_id_for_url(url)
        source_registry[source_id] = source_record(source_id, url=url)


def location_for(value: str, fallback_area_id: str | None = None, fallback_theater: str | None = None) -> dict[str, Any]:
    if fallback_area_id:
        for _, point in LOCATION_POINTS:
            if point["maritimeAreaId"] == fallback_area_id:
                return {**point, "theater": fallback_theater or point["theater"]}
    for pattern, point in LOCATION_POINTS:
        if pattern.search(value or ""):
            return point
    return {"lat": 43.50, "lng": 34.00, "theater": fallback_theater or "BLACK_SEA", "maritimeAreaId": "black_sea_general", "labelEn": value or "Black Sea", "labelUk": value or "Чорне море"}


def normalize_baseline_event(raw: dict[str, Any]) -> dict[str, Any] | None:
    if raw.get("category") not in NAVAL_CATEGORIES:
        return None
    title = raw.get("title") or {}
    summary = raw.get("summary") or {}
    asset = raw.get("asset") or {}
    source_ids = list(raw.get("sourceIds") or [])
    point = location_for(
        f"{raw.get('maritimeAreaId') or ''} {title.get('en') or ''} {title.get('uk') or ''}",
        raw.get("maritimeAreaId"),
        raw.get("theater"),
    )
    return {
        "id": f"event_naval_registry_{raw.get('id')}",
        "status": "PUBLISHED",
        "titleEn": title.get("en") or asset.get("assetName") or raw.get("id"),
        "titleUk": title.get("uk") or title.get("en") or asset.get("assetName") or raw.get("id"),
        "date": raw.get("date"),
        "datePrecision": "DAY",
        "category": raw.get("category"),
        "eventScope": "MILITARY_ASSET",
        "theater": point["theater"],
        "regionId": None,
        "federalDistrictId": None,
        "maritimeAreaId": point["maritimeAreaId"],
        "sanctionsJurisdictionId": None,
        "approximateLocationLabelEn": point["labelEn"],
        "approximateLocationLabelUk": point["labelUk"],
        "lat": point["lat"],
        "lng": point["lng"],
        "radiusKm": 0,
        "precision": "MARITIME_REGIONAL",
        "assetId": asset.get("assetId"),
        "actor": "UKRAINIAN_DEFENSE_FORCES",
        "actorConfidence": raw.get("verificationStatus") or "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
        "actorNote": "Generated from the reconciled naval vessel loss registry.",
        "verificationStatus": raw.get("verificationStatus") or "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
        "severity": raw.get("severity") or ("HIGH" if raw.get("category") == "NAVAL_VESSEL_LOSS" else "MEDIUM"),
        "summaryEn": summary.get("en") or title.get("en") or "",
        "summaryUk": summary.get("uk") or title.get("uk") or title.get("en") or "",
        "impactTags": ", ".join(raw.get("impactTags") or ["naval vessel", raw.get("category", "")]),
        "sources": ",".join(source_ids or [FALLBACK_SOURCE_ID]),
        "safetyNotes": raw.get("safetyNotes") or "Generalized maritime-area record; not an exact strike coordinate.",
        "createdAt": f"{raw.get('date')}T00:00:00Z",
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT00:00:00Z"),
        "_sourceIdsForRegistry": source_ids,
        "_sourceUrlsForRegistry": [],
    }


def normalize_manual_item(raw: dict[str, Any]) -> dict[str, Any] | None:
    if raw.get("category") not in NAVAL_CATEGORIES:
        return None
    name = str(raw.get("objectName") or "").strip()
    date = str(raw.get("strikeDate") or "").strip()
    location = str(raw.get("strikeLocation") or "").strip()
    if not name or not date:
        return None
    source_urls = list(raw.get("sourceUrls") or [])
    source_ids = [source_id_for_url(url) for url in source_urls] or [FALLBACK_SOURCE_ID]
    point = location_for(location)
    status_text = "sunk or destroyed" if raw.get("category") == "NAVAL_VESSEL_LOSS" else "damaged"
    title_en = f"{name} {status_text}"
    title_uk = title_en
    summary_en = f"{name} was reported {status_text} near {location or point['labelEn']}."
    summary_uk = summary_en
    event_id = f"event_naval_registry_{date.replace('-', '')}_{slug(name)}"
    return {
        "id": event_id,
        "status": "PUBLISHED",
        "titleEn": title_en,
        "titleUk": title_uk,
        "date": date,
        "datePrecision": "DAY",
        "category": raw.get("category"),
        "eventScope": "MILITARY_ASSET",
        "theater": point["theater"],
        "regionId": None,
        "federalDistrictId": None,
        "maritimeAreaId": point["maritimeAreaId"],
        "sanctionsJurisdictionId": None,
        "approximateLocationLabelEn": point["labelEn"],
        "approximateLocationLabelUk": point["labelUk"],
        "lat": point["lat"],
        "lng": point["lng"],
        "radiusKm": 0,
        "precision": "MARITIME_REGIONAL",
        "assetId": f"asset_ru_navy_{slug(name)}",
        "actor": "UKRAINIAN_DEFENSE_FORCES",
        "actorConfidence": "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
        "actorNote": raw.get("sourceNote") or "Generated from the reconciled naval vessel loss registry.",
        "verificationStatus": "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
        "severity": "HIGH" if raw.get("category") == "NAVAL_VESSEL_LOSS" else "MEDIUM",
        "summaryEn": summary_en,
        "summaryUk": summary_uk,
        "impactTags": f"naval vessel, {raw.get('category')}",
        "sources": ",".join(source_ids),
        "safetyNotes": "Generalized maritime-area record; not an exact strike coordinate.",
        "createdAt": f"{date}T00:00:00Z",
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT00:00:00Z"),
        "_sourceIdsForRegistry": [],
        "_sourceUrlsForRegistry": source_urls,
    }


def strip_internal_fields(event: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in event.items() if not key.startswith("_")}


def upsert_manual_events(events: list[dict[str, Any]]) -> None:
    payload = load_json(MANUAL_EVENTS_FILE)
    existing = [event for event in payload.get("events", []) if not str(event.get("id", "")).startswith("event_naval_registry_")]
    payload["events"] = existing + [strip_internal_fields(event) for event in events]
    payload["recordCount"] = len(payload["events"])
    payload["generatedAt"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    write_json(MANUAL_EVENTS_FILE, payload)


def upsert_sources(events: list[dict[str, Any]]) -> None:
    payload = load_json(SOURCES_FILE)
    sources = {source.get("sourceId"): source for source in payload.get("sources", []) if source.get("sourceId")}
    for source_id, override in SOURCE_REGISTRY_OVERRIDES.items():
        sources[source_id] = source_record(source_id, url=override["sourceUrl"])
    for event in events:
        ensure_sources(event.get("_sourceIdsForRegistry") or [], event.get("_sourceUrlsForRegistry") or [], sources)
    payload["sources"] = sorted(sources.values(), key=lambda item: item["sourceId"])
    payload["recordCount"] = len(payload["sources"])
    write_json(SOURCES_FILE, payload)
    APP_SOURCES_FILE.parent.mkdir(parents=True, exist_ok=True)
    write_json(APP_SOURCES_FILE, payload)


def main() -> None:
    baseline = load_json(BASELINE_FILE)
    manual = load_json(MANUAL_NAVAL_FILE)
    events: dict[tuple[str, str], dict[str, Any]] = {}
    for raw in baseline.get("events", []):
        event = normalize_baseline_event(raw)
        if event:
            events[(event["titleEn"].lower(), event["date"])] = event
    for raw in manual.get("items", []):
        event = normalize_manual_item(raw)
        if event:
            key = (re.sub(r"\s+(damaged|sunk or destroyed)$", "", event["titleEn"].lower()), event["date"])
            events.setdefault(key, event)
    final_events = sorted(events.values(), key=lambda item: (item["date"], item["titleEn"]))
    upsert_sources(final_events)
    upsert_manual_events(final_events)
    print(json.dumps({"navalEventCount": len(final_events), "manualEventsFile": str(MANUAL_EVENTS_FILE)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
