#!/usr/bin/env python3
"""Enrich the 16 July 2026 Engels-2 event with a direct Suspilne source."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENTS_PATH = ROOT / "data" / "final" / "osint_events.json"
SOURCES_PATH = ROOT / "data" / "final" / "wikipedia_citation_sources.json"
EVENT_ID = "event_20260716_wiki_uav_strike_c2e30831c76e"
SOURCE_ID = "source_suspilne_tu95_engels_2026_07_17"


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    events_payload = json.loads(EVENTS_PATH.read_text(encoding="utf-8-sig"))
    event = next((item for item in events_payload["events"] if item.get("id") == EVENT_ID), None)
    if event is None:
        raise SystemExit(f"Event not found: {EVENT_ID}")

    event.update({
        "status": "PUBLISHED",
        "titleEn": "Tu-95 aircraft destroyed at Engels-2 air base",
        "titleUk": "\u0417\u043d\u0438\u0449\u0435\u043d\u043d\u044f \u043b\u0456\u0442\u0430\u043a\u0430 \u0422\u0443-95 \u043d\u0430 \u0430\u0435\u0440\u043e\u0434\u0440\u043e\u043c\u0456 \u00ab\u0415\u043d\u0433\u0435\u043b\u044c\u0441-2\u00bb",
        "category": "MILITARY_ASSET_DISRUPTION",
        "approximateLocationLabelEn": "Engels-2 air base, Saratov Oblast",
        "approximateLocationLabelUk": "\u0430\u0435\u0440\u043e\u0434\u0440\u043e\u043c \u00ab\u0415\u043d\u0433\u0435\u043b\u044c\u0441-2\u00bb, \u0421\u0430\u0440\u0430\u0442\u043e\u0432\u0441\u044c\u043a\u0430 \u043e\u0431\u043b\u0430\u0441\u0442\u044c",
        "actor": "SECURITY_SERVICE_OF_UKRAINE",
        "actorConfidence": "OFFICIAL_STATEMENT",
        "actorNote": "President Volodymyr Zelenskyy said the Security Service of Ukraine destroyed the aircraft; the statement was reported by Suspilne.",
        "verificationStatus": "REPORTED",
        "severity": "HIGH",
        "summaryEn": "President Volodymyr Zelenskyy said the Security Service of Ukraine destroyed a Tu-95 strategic bomber at Engels, an aircraft used for missile strikes against Ukraine.",
        "summaryUk": "\u041f\u0440\u0435\u0437\u0438\u0434\u0435\u043d\u0442 \u0412\u043e\u043b\u043e\u0434\u0438\u043c\u0438\u0440 \u0417\u0435\u043b\u0435\u043d\u0441\u044c\u043a\u0438\u0439 \u043f\u043e\u0432\u0456\u0434\u043e\u043c\u0438\u0432, \u0449\u043e \u0421\u0411\u0423 \u0437\u043d\u0438\u0449\u0438\u043b\u0430 \u0432 \u0415\u043d\u0433\u0435\u043b\u044c\u0441\u0456 \u043b\u0456\u0442\u0430\u043a \u0422\u0443-95, \u044f\u043a\u0438\u0439 \u0420\u043e\u0441\u0456\u044f \u0432\u0438\u043a\u043e\u0440\u0438\u0441\u0442\u043e\u0432\u0443\u0432\u0430\u043b\u0430 \u0434\u043b\u044f \u0440\u0430\u043a\u0435\u0442\u043d\u0438\u0445 \u0443\u0434\u0430\u0440\u0456\u0432 \u043f\u043e \u0423\u043a\u0440\u0430\u0457\u043d\u0456.",
        "impactTags": "STRATEGIC_AVIATION, TU-95, ENGELS_2, MILITARY_AIRCRAFT",
        "sources": f"source_wiki_reference_57efe3ad5fe559570f39, {SOURCE_ID}",
        "updatedAt": "2026-07-17T14:08:33Z",
    })
    write_json(EVENTS_PATH, events_payload)

    sources_payload = json.loads(SOURCES_PATH.read_text(encoding="utf-8-sig"))
    sources = sources_payload["sources"]
    source = {
        "sourceId": SOURCE_ID,
        "sourceName": "SBU destroyed a Tu-95 military aircraft in Engels, Zelenskyy says",
        "publisher": "Suspilne",
        "sourceType": "MEDIA_REPORT",
        "country": "Ukraine",
        "language": "uk",
        "reliabilityScore": 4,
        "lastChecked": "2026-07-17",
        "usedInRecordsCount": 1,
        "sourceDescription": "Suspilne report on President Volodymyr Zelenskyy's statement that the Security Service of Ukraine destroyed a Tu-95 aircraft at Engels.",
        "allowedUse": "Public reference link; open the original article for full context.",
        "reliabilityNote": "Media report attributing the claim to the President of Ukraine; retained as reported pending independent verification.",
        "sourceUrl": "https://suspilne.media/1357612-sili-sbu-znisili-vijskovij-litak-tu-95-v-engelsi-zelenskij/",
    }
    sources[:] = [item for item in sources if item.get("sourceId") != SOURCE_ID]
    sources.append(source)
    sources_payload["recordCount"] = len(sources)
    write_json(SOURCES_PATH, sources_payload)

    print(f"Updated {EVENT_ID} and added {SOURCE_ID}.")


if __name__ == "__main__":
    main()
