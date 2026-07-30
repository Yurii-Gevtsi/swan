#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Import all attack rows from the Ukrainian Wikipedia UAV-strikes page.

The page is used as a discovery source only.  The importer keeps the complete
table snapshot, including citation numbers and the concrete reference URLs,
then creates app-ready additions for new Russia/maritime records.
"""

from __future__ import annotations

import hashlib
import argparse
from collections import Counter
import json
import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import quote, urljoin, urlsplit, urlunsplit

import requests
from bs4 import BeautifulSoup


REPO_ROOT = Path(__file__).resolve().parents[1]
FINAL_DIR = REPO_ROOT / "data" / "final"
FINAL_EVENTS = FINAL_DIR / "osint_events.json"
MANUAL_EVENTS = FINAL_DIR / "osint_events_manual_additions.json"
REGISTRY_FILE = FINAL_DIR / "impact_registry.json"
SNAPSHOT_FILE = FINAL_DIR / "wiki_uav_strikes_2022_2025_snapshot.json"
REPORT_FILE = FINAL_DIR / "wiki_uav_strikes_2022_2025_import_report.txt"
CITATION_SOURCES_FILE = FINAL_DIR / "wikipedia_citation_sources.json"
APP_CITATION_SOURCES_FILE = REPO_ROOT / "app" / "src" / "main" / "assets" / "wikipedia_citation_sources.json"
WIKIPEDIA_SNAPSHOT_FILES = (
    FINAL_DIR / "wiki_uav_strikes_2022_2025_snapshot.json",
    FINAL_DIR / "wiki_uav_strikes_2026_snapshot.json",
)

SOURCE_ID = "source_wiki_uav_strikes_2022_2025"
SOURCE_TITLE = "Перелік атак БпЛА по російських цілях (2022-2025)"
SOURCE_URL = (
    "https://uk.wikipedia.org/wiki/"
    "%D0%9F%D0%B5%D1%80%D0%B5%D0%BB%D1%96%D0%BA_%D0%B0%D1%82%D0%B0%D0%BA_%D0%91%D0%BF%D0%9B%D0%90_"
    "%D0%BF%D0%BE_%D1%80%D0%BE%D1%81%D1%96%D0%B9%D1%81%D1%8C%D0%BA%D0%B8%D1%85_%D1%86%D1%96%D0%BB%D1%8F%D1%85_(2022-2025)"
)

HEADERS = {"User-Agent": "OSINT-SWAN Wikipedia discovery importer/1.0"}

TABLE_SECTIONS = {
    1: "Атаки 2022 року",
    2: "Атаки 2023 року",
    3: "Атаки 2024 року",
    4: "Атаки 2025 року",
    5: "Атаки безпілотними надводними апаратами",
}


def configure_source(year: str) -> None:
    global SOURCE_ID, SOURCE_TITLE, SOURCE_URL, SNAPSHOT_FILE, REPORT_FILE, TABLE_SECTIONS

    if year != "2026":
        return

    SOURCE_TITLE = "Перелік атак БпЛА по російських цілях (2026)"
    SOURCE_ID = "source_wiki_uav_strikes_2026"
    SOURCE_URL = "https://uk.wikipedia.org/wiki/" + quote(SOURCE_TITLE.replace(" ", "_"), safe="/()_-")
    SNAPSHOT_FILE = FINAL_DIR / "wiki_uav_strikes_2026_snapshot.json"
    REPORT_FILE = FINAL_DIR / "wiki_uav_strikes_2026_import_report.txt"
    TABLE_SECTIONS = {
        1: "Атаки безпілотниками",
        2: "Атаки безпілотними надводними апаратами",
    }

CATEGORY_KEYWORDS = {
    "FUEL_SUPPLY_DISRUPTION": (
        "нафтобаза", "нпз", "нафтоперероб", "нафтопровід", "нафтотермінал",
        "термінал", "пмм", "палив", "резервуар", "нафтопродукт", "газопровід",
    ),
    "AIRFIELD_OR_MILITARY_INFRASTRUCTURE_DISRUPTION": (
        "аеродром", "авіабаза", "авіазавод", "літак", "бомбардувальник",
        "військовий аеродром", "авіаційний", "винищувач",
    ),
    "AMMUNITION_DEPOT_DISRUPTION": (
        "склад боєприпасів", "склад бк", "арсенал", "вибухівк", "порох",
        "вибух", "військова частина",
    ),
    "INDUSTRIAL_DISRUPTION": (
        "завод", "виробнич", "підприємств", "електропідстанц", "підстанц",
        "машинобуд", "мікроелектрон", "впк",
    ),
    "LOGISTICS_PRESSURE": (
        "залізнич", "станці", "міст", "порт", "термінал", "нафтопровід",
    ),
    "MARITIME_ASSET_DISRUPTION": (
        "кораб", "судн", "катер", "буксир", "човен", "морськ", "акватор",
        "проток", "флот",
    ),
}

MARITIME_HINTS = (
    "чорне море", "азовське море", "керченськ", "керченська протока", "акватор",
    "кораб", "судн", "катер", "буксир", "човен", "флот", "морський", "острів зміїний",
)

MARITIME_HINTS = MARITIME_HINTS + (
    "\u0441\u0435\u0440\u0435\u0434\u0437\u0435\u043c\u043d\u0435 \u043c\u043e\u0440\u0435",
    "\u043a\u0430\u0441\u043f\u0456\u0439\u0441\u044c\u043a\u0435 \u043c\u043e\u0440\u0435",
    "\u043e\u0437\u0435\u0440\u043e",
)

RUSSIA_HINTS = (
    "рф", "росі", "обл.", "край", "татарстан", "мордов", "москва", "санкт-петербург",
    "брянськ", "бєлгород", "курськ", "ростов", "воронеж", "краснодар", "смоленськ",
    "ленінград", "нижньогород", "самар", "саратов", "рязан", "тул", "псков", "орлов",
    "волгоград", "калуз", "липец", "новгород", "ярослав", "тамбов", "твер",
)

UKRAINE_HINTS = (
    "україн", "донецьк", "луганськ", "запоріж", "херсон", "харків", "миколаїв",
    "одес", "крим", "севастопол", "маріупол", "бердянськ", "мелітопол", "керч",
)

# Regional anchors are deliberately broad.  They are for map aggregation, not
# exact facility geolocation.
REGION_POINTS = {
    "брянськ": (53.24, 34.36, "ru_bryansk_oblast"),
    "бєлгород": (50.60, 36.59, "ru_belgorod_oblast"),
    "курськ": (51.73, 36.19, "ru_kursk_oblast"),
    "ростов": (47.24, 39.70, "ru_rostov_oblast"),
    "новошахтинськ": (47.76, 39.93, "ru_rostov_oblast"),
    "воронеж": (51.67, 39.21, "ru_voronezh_oblast"),
    "краснодар": (45.04, 38.98, "ru_krasnodar_krai"),
    "туапсе": (44.10, 39.07, "ru_krasnodar_krai"),
    "сочі": (43.59, 39.73, "ru_krasnodar_krai"),
    "смоленськ": (54.78, 32.05, "ru_smolensk_oblast"),
    "ленінград": (59.90, 30.50, "ru_leningrad_oblast"),
    "санкт-петербург": (59.93, 30.33, "ru_saint_petersburg"),
    "нижньогород": (56.33, 44.00, "ru_nizhny_novgorod_oblast"),
    "дзержинськ": (56.24, 43.46, "ru_nizhny_novgorod_oblast"),
    "самар": (53.20, 50.15, "ru_samara_oblast"),
    "саратов": (51.53, 46.03, "ru_saratov_oblast"),
    "рязань": (54.63, 39.74, "ru_ryazan_oblast"),
    "тул": (54.20, 37.62, "ru_tula_oblast"),
    "псков": (57.82, 28.33, "ru_pskov_oblast"),
    "орлов": (52.97, 36.06, "ru_oryol_oblast"),
    "волгоград": (48.71, 44.51, "ru_volgograd_oblast"),
    "калуз": (54.51, 36.26, "ru_kaluga_oblast"),
    "липец": (52.61, 39.59, "ru_lipetsk_oblast"),
    "новгород": (58.52, 31.27, "ru_novgorod_oblast"),
    "ярослав": (57.63, 39.87, "ru_yaroslavl_oblast"),
    "тамбов": (52.72, 41.45, "ru_tambov_oblast"),
    "твер": (56.86, 35.91, "ru_tver_oblast"),
    "москва": (55.76, 37.62, "ru_moscow"),
    "татарстан": (55.80, 49.10, "ru_tatarstan"),
    "єлабуга": (55.76, 52.05, "ru_tatarstan"),
    "алабуга": (55.76, 52.05, "ru_tatarstan"),
    "нижньокамськ": (55.64, 51.82, "ru_tatarstan"),
    "казань": (55.79, 49.12, "ru_tatarstan"),
    "мордов": (54.18, 45.18, "ru_mordovia"),
    "екатеринбург": (56.84, 60.61, "ru_sverdlovsk_oblast"),
    "усть-луга": (59.67, 28.28, "ru_leningrad_oblast"),
    "новоросійськ": (44.72, 37.77, "ru_krasnodar_krai"),
    "керч": (45.36, 36.47, None),
    "севастопол": (44.62, 33.53, None),
    "чорне море": (43.50, 34.00, None),
    "азовське море": (46.00, 37.00, None),
}

SPECIFIC_POINT_KEYS = (
    "єлабуга",
    "алабуга",
)

REGION_FALLBACK_POINTS = {
    "\u0440\u044f\u0437\u0430\u043d\u0441\u044c\u043a\u0430": (54.63, 39.74, "ru_ryazan_oblast"),
    "\u0432\u043e\u0440\u043e\u043d\u0435\u0437\u044c\u043a\u0430": (51.67, 39.21, "ru_voronezh_oblast"),
    "\u0441\u0432\u0435\u0440\u0434\u043b\u043e\u0432\u0441\u044c\u043a\u0430": (56.84, 60.61, "ru_sverdlovsk_oblast"),
    "\u043c\u043e\u0441\u043a\u043e\u0432\u0441\u044c\u043a\u0430": (55.76, 37.62, "ru_moscow_oblast"),
    "\u043f\u0456\u0432\u043d\u0456\u0447\u043d\u0430 \u043e\u0441\u0435\u0442\u0456\u044f": (43.04, 44.68, "ru_north_ossetia_alania"),
    "\u0430\u0441\u0442\u0440\u0430\u0445\u0430\u043d\u0441\u044c\u043a\u0430": (46.35, 48.04, "ru_astrakhan_oblast"),
    "\u0441\u0442\u0430\u0432\u0440\u043e\u043f\u043e\u043b\u044c\u0441\u044c\u043a\u0438\u0439": (45.04, 41.97, "ru_stavropol_krai"),
    "\u043d\u0438\u0436\u0435\u0433\u043e\u0440\u043e\u0434\u0441\u044c\u043a\u0430": (56.33, 44.00, "ru_nizhny_novgorod_oblast"),
    "\u043a\u0456\u0440\u043e\u0432\u0441\u044c\u043a\u0430": (58.60, 49.67, "ru_kirov_oblast"),
    "\u0430\u0434\u0438\u0433\u0435\u044f": (44.69, 40.16, "ru_adigea"),
    "\u043f\u0435\u043d\u0437\u0435\u043d\u0441\u044c\u043a\u0430": (53.20, 45.00, "ru_penza_oblast"),
    "\u0443\u0434\u043c\u0443\u0440\u0442\u0456\u044f": (56.85, 53.20, "ru_udmurtia"),
    "\u043a\u0430\u043b\u0443\u0436\u0441\u044c\u043a\u0430": (54.51, 36.26, "ru_kaluga_oblast"),
    "\u0456\u0432\u0430\u043d\u043e\u0432\u0441\u044c\u043a\u0430": (57.00, 40.97, "ru_ivanovo_oblast"),
    "\u0432\u043b\u0430\u0434\u0438\u043c\u0438\u0440\u0441\u044c\u043a\u0430": (56.13, 40.41, "ru_vladimir_oblast"),
    "\u0456\u0440\u043a\u0443\u0442\u0441\u044c\u043a\u0430": (52.29, 104.28, "ru_irkutsk_oblast"),
    "\u043c\u0443\u0440\u043c\u0430\u043d\u0441\u044c\u043a\u0430": (68.97, 33.07, "ru_murmansk_oblast"),
    "\u043f\u0435\u0440\u043c\u0441\u044c\u043a\u0438\u0439": (58.01, 56.25, "ru_perm_krai"),
    "\u043e\u0440\u0435\u043d\u0431\u0443\u0440\u0437\u044c\u043a\u0430": (51.77, 55.10, "ru_orenburg_oblast"),
    "\u0440\u0435\u0441\u043f\u0443\u0431\u043b\u0456\u043a\u0430 \u043a\u0430\u0440\u0435\u043b\u0456\u044f": (61.79, 34.36, "ru_karelia"),
    "\u0443\u043b\u044c\u044f\u043d\u043e\u0432\u0441\u044c\u043a\u0430": (54.31, 48.40, "ru_ulyanovsk_oblast"),
    "\u0431\u0430\u0448\u043a\u043e\u0440\u0442\u043e\u0441\u0442\u0430\u043d": (54.74, 55.97, "ru_bashkortostan"),
    "\u043a\u043e\u0441\u0442\u0440\u043e\u043c\u0441\u044c\u043a\u0430": (57.77, 40.93, "ru_kostroma_oblast"),
    "\u0432\u043e\u043b\u043e\u0433\u043e\u0434\u0441\u044c\u043a\u0430": (59.22, 39.89, "ru_vologda_oblast"),
    "\u043a\u0430\u0441\u043f\u0456\u0439\u0441\u044c\u043a\u0435 \u043c\u043e\u0440\u0435": (43.00, 50.00, None),
    "\u0441\u0435\u0440\u0435\u0434\u0437\u0435\u043c\u043d\u0435 \u043c\u043e\u0440\u0435": (35.00, 18.00, None),
}

REGION_POINTS.update(REGION_FALLBACK_POINTS)
REGION_POINTS.update({
    "чувашія": (56.14, 47.25, "ru_chuvashia"),
    "чебоксари": (56.14, 47.25, "ru_chuvashia"),
    "дагестан": (42.98, 47.50, "ru_dagestan"),
    "каспійськ": (42.88, 47.64, "ru_dagestan"),
    "чечня": (43.32, 45.70, "ru_chechnya"),
    "грозний": (43.32, 45.70, "ru_chechnya"),
    "омськ": (54.99, 73.37, "ru_omsk_oblast"),
    "комі": (63.57, 53.69, "ru_komi"),
    "ухта": (63.57, 53.69, "ru_komi"),
    "челябінськ": (55.16, 61.40, "ru_chelyabinsk_oblast"),
    "шагол": (55.09, 61.39, "ru_chelyabinsk_oblast"),
    "тюменськ": (57.15, 65.53, "ru_tyumen_oblast"),
    "тюмень": (57.15, 65.53, "ru_tyumen_oblast"),
    "липецьк": (52.61, 39.59, "ru_lipetsk_oblast"),
})


def clean_text(value: str) -> str:
    value = re.sub(r"\[\s*\d+\s*\]", "", value or "")
    value = re.sub(r"\s+", " ", value)
    return value.strip(" \u00a0")


def normalize_key(value: str) -> str:
    value = repair_mojibake(value or "")
    value = unicodedata.normalize("NFKC", value).lower()
    value = value.replace("ё", "е").replace("ґ", "г")
    return re.sub(r"[^0-9\w]+", " ", value, flags=re.UNICODE).strip()


def repair_mojibake(value: str) -> str:
    """Repair the cp1251/UTF-8 corruption present in older app snapshots."""
    if not value or "Р" not in value and "С" not in value:
        return value
    try:
        repaired = value.encode("cp1251").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return value
    return repaired if repaired.count("Р") + repaired.count("С") < value.count("Р") + value.count("С") else value


def normalize_date(value: str, current_date: str) -> str:
    match = re.search(r"(\d{2})\.(\d{2})\.(\d{4})", value or "")
    if not match:
        return current_date
    day, month, year = match.groups()
    return f"{year}-{month}-{day}"


def expand_table(table: Any) -> list[list[Any]]:
    grid: list[list[Any]] = []
    spans: dict[tuple[int, int], Any] = {}
    for row_index, tr in enumerate(table.find_all("tr")):
        row: list[Any] = []
        col_index = 0
        cells = tr.find_all(["th", "td"], recursive=False)
        for cell in cells:
            while (row_index, col_index) in spans:
                row.append(spans.pop((row_index, col_index)))
                col_index += 1
            rowspan = int(cell.get("rowspan", 1) or 1)
            colspan = int(cell.get("colspan", 1) or 1)
            for offset in range(colspan):
                row.append(cell)
                for row_offset in range(1, rowspan):
                    spans[(row_index + row_offset, col_index + offset)] = cell
            col_index += colspan
        while (row_index, col_index) in spans:
            row.append(spans.pop((row_index, col_index)))
            col_index += 1
        grid.append(row)
    width = max((len(row) for row in grid), default=0)
    return [row + [None] * (width - len(row)) for row in grid]


def cell_text(cell: Any) -> str:
    return clean_text(cell.get_text(" ", strip=True) if cell else "")


def citation_from_anchor(soup: BeautifulSoup, anchor: Any) -> dict[str, Any]:
    href = anchor.get("href", "")
    note_id = href[1:] if href.startswith("#") else href
    note = soup.find(id=note_id)
    number_match = re.search(r"\d+", anchor.get_text(" ", strip=True))
    number = int(number_match.group(0)) if number_match else None
    links = []
    external_links = []
    if note:
        for link in note.select("a[href]"):
            url = urljoin(SOURCE_URL, link.get("href", ""))
            if url.startswith("http") and "wikipedia.org" not in url:
                if url not in links:
                    links.append(url)
                    external_links.append({
                        "url": url,
                        "title": clean_text(link.get_text(" ", strip=True)),
                    })
    return {
        "number": number,
        "noteId": note_id,
        "wikipediaUrl": urljoin(SOURCE_URL, href),
        "text": note.get_text(" ", strip=True) if note else "",
        "externalUrls": links,
        "externalLinks": external_links,
    }


def citations_for_cell(soup: BeautifulSoup, cell: Any) -> list[dict[str, Any]]:
    result = []
    seen = set()
    if not cell:
        return result
    for anchor in cell.select("a[href]"):
        href = anchor.get("href", "")
        if not href.startswith("#cite_note"):
            continue
        citation = citation_from_anchor(soup, anchor)
        if citation["noteId"] not in seen:
            seen.add(citation["noteId"])
            result.append(citation)
    return result


def normalized_source_url(url: str) -> str:
    parts = urlsplit(url.strip())
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(), parts.path, parts.query, ""))


def citation_source_id(url: str) -> str:
    digest = hashlib.sha1(normalized_source_url(url).encode("utf-8")).hexdigest()[:20]
    return f"source_wiki_reference_{digest}"


def citation_source_ids(citations: list[dict[str, Any]]) -> list[str]:
    result = []
    seen = set()
    for citation in citations:
        for url in citation.get("externalUrls", []):
            source_id = citation_source_id(url)
            if source_id not in seen:
                seen.add(source_id)
                result.append(source_id)
    return result


PUBLISHER_NAMES = {
    "militarnyi.com": "Militarnyi",
    "mil.in.ua": "Militarnyi",
    "www.pravda.com.ua": "Ukrainska Pravda",
    "pravda.com.ua": "Ukrainska Pravda",
    "nv.ua": "NV",
    "www.rbc.ua": "RBC-Ukraine",
    "rbc.ua": "RBC-Ukraine",
    "focus.ua": "Focus",
    "24tv.ua": "24 Channel",
    "www.slovoidilo.ua": "Slovo i Dilo",
    "slovoidilo.ua": "Slovo i Dilo",
    "www.radiosvoboda.org": "Radio Free Europe/Radio Liberty",
    "radiosvoboda.org": "Radio Free Europe/Radio Liberty",
    "armyinform.com.ua": "ArmyInform",
    "www.zsu.gov.ua": "Armed Forces of Ukraine",
    "zsu.gov.ua": "Armed Forces of Ukraine",
    "gur.gov.ua": "Defence Intelligence of Ukraine",
    "ssu.gov.ua": "Security Service of Ukraine",
}

OFFICIAL_SOURCE_HOSTS = {
    "armyinform.com.ua", "www.zsu.gov.ua", "zsu.gov.ua", "gur.gov.ua",
    "ssu.gov.ua", "president.gov.ua", "www.president.gov.ua", "mod.gov.ua",
    "www.mod.gov.ua", "sbs-group.army",
}


def source_title(citation: dict[str, Any], link: dict[str, str], publisher: str) -> str:
    title = clean_text(link.get("title", ""))
    if len(title) >= 8 and title.lower() not in {"архівовано", "archive", "джерело"}:
        return title[:240]
    citation_text = clean_text(citation.get("text", ""))
    citation_text = re.sub(r"^(?:↑|\d+\s*)+", "", citation_text).strip(" .")
    return (citation_text or f"Reference from {publisher}")[:240]


def source_language(citation: dict[str, Any], url: str) -> str:
    text = citation.get("text", "").lower()
    if "(англ.)" in text:
        return "en"
    if "(рос.)" in text:
        return "ru"
    if "(укр.)" in text or ".ua" in urlsplit(url).netloc or "/uk/" in url:
        return "uk"
    return "unknown"


def source_country(url: str) -> str:
    host = urlsplit(url).netloc.lower()
    if host.endswith(".ua") or host in OFFICIAL_SOURCE_HOSTS:
        return "Ukraine"
    return "Unknown"


def build_citation_source_registry(records: list[dict[str, Any]]) -> dict[str, Any]:
    source_details: dict[str, dict[str, Any]] = {}
    usage = Counter()
    for record in records:
        record_source_ids = set()
        for citation in record.get("citations", []):
            links = citation.get("externalLinks") or [
                {"url": url, "title": ""} for url in citation.get("externalUrls", [])
            ]
            for link in links:
                url = normalized_source_url(link.get("url", ""))
                if not url:
                    continue
                source_id = citation_source_id(url)
                host = urlsplit(url).netloc.lower()
                publisher = PUBLISHER_NAMES.get(host, host.removeprefix("www."))
                source_details.setdefault(source_id, {
                    "sourceId": source_id,
                    "sourceName": source_title(citation, link, publisher),
                    "publisher": publisher,
                    "sourceType": "OFFICIAL_REFERENCE" if host in OFFICIAL_SOURCE_HOSTS else "MEDIA_REFERENCE",
                    "country": source_country(url),
                    "language": source_language(citation, url),
                    "reliabilityScore": 4 if host in OFFICIAL_SOURCE_HOSTS else 3,
                    "lastChecked": datetime.now(timezone.utc).date().isoformat(),
                    "usedInRecordsCount": 0,
                    "sourceDescription": f"External source preserved from Wikipedia note #{citation.get('number') or '?' }.",
                    "allowedUse": "Public reference link; open the original article for full context.",
                    "reliabilityNote": "Imported from a Wikipedia citation. Reliability depends on the original publisher and article.",
                    "sourceUrl": url,
                })
                record_source_ids.add(source_id)
        usage.update(record_source_ids)

    for source_id, count in usage.items():
        source_details[source_id]["usedInRecordsCount"] = count
    sources = sorted(source_details.values(), key=lambda source: (source["publisher"], source["sourceName"], source["sourceId"]))
    return {"schemaVersion": 1, "recordCount": len(sources), "sources": sources}


def write_citation_source_registry() -> dict[str, Any]:
    records = []
    for path in WIKIPEDIA_SNAPSHOT_FILES:
        if path.exists():
            snapshot = json.loads(path.read_text(encoding="utf-8"))
            records.extend(snapshot.get("records", []))
    registry = build_citation_source_registry(records)
    serialized = json.dumps(registry, ensure_ascii=False, indent=2) + "\n"
    CITATION_SOURCES_FILE.write_text(serialized, encoding="utf-8")
    APP_CITATION_SOURCES_FILE.parent.mkdir(parents=True, exist_ok=True)
    APP_CITATION_SOURCES_FILE.write_text(serialized, encoding="utf-8")
    return registry


def category_for(region: str, place: str, target: str) -> str:
    text = f"{region} {place} {target}".lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(keyword in text for keyword in keywords):
            return category
    return "MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR"


GENERIC_MARITIME_OBJECTS = {
    "танкер",
    "суховантаж",
    "буксир",
    "пором",
    "судно",
    "корабель",
    "спеціалізований плавзасіб",
    "нафтовидобувна платформа",
}

# The July 6-14 SBS operation is listed vessel by vessel in the 2026 source.
# Its positions are deliberately generalized into a compact grid in the central
# Sea of Azov so individual records remain visible without implying vessel
# routes or exact strike coordinates.
SBS_SHADOW_FLEET_AZOV_START = "2026-07-06"
SBS_SHADOW_FLEET_AZOV_END = "2026-07-14"
SBS_SHADOW_FLEET_GRID_COLUMNS = 12


def apply_sbs_shadow_fleet_display_points(events: list[dict[str, Any]]) -> int:
    candidates = [
        event for event in events
        if event.get("theater") == "AZOV_SEA"
        and SBS_SHADOW_FLEET_AZOV_START <= str(event.get("date") or "") <= SBS_SHADOW_FLEET_AZOV_END
    ]

    for index, event in enumerate(sorted(candidates, key=lambda item: (item["date"], item["id"]))):
        row, column = divmod(index, SBS_SHADOW_FLEET_GRID_COLUMNS)
        event["category"] = "SHADOW_FLEET_DISRUPTION"
        event["lat"] = round(45.42 + row * 0.055 + (column % 2) * 0.006, 5)
        event["lng"] = round(36.05 + column * 0.14 + (row % 2) * 0.025, 5)
        event["radiusKm"] = 0
        event["impactTags"] = f"{event['impactTags']}, SBS_SHADOW_FLEET_OPERATION, GENERALIZED_SEA_POINT"
        event["safetyNotes"] = (
            "Wikipedia discovery-only record. This map point is a deliberately generalized "
            "display position in the central Sea of Azov, not an exact vessel or strike location."
        )

    return len(candidates)


def is_generic_maritime_object(value: str) -> bool:
    return normalize_key(value) in {normalize_key(item) for item in GENERIC_MARITIME_OBJECTS}


def assign_record_identities(records: list[dict[str, Any]]) -> None:
    ordinals: Counter[tuple[str, str, str]] = Counter()
    for record in records:
        object_name = record["object"]
        if is_generic_maritime_object(object_name):
            group = (
                normalize_key(record["date"]),
                normalize_key(object_name),
                normalize_key(record["region"]),
            )
            ordinals[group] += 1
            ordinal = ordinals[group]
            record["dedupeObject"] = f"{object_name} @ {record['region']} #{ordinal}"
            record["displayObject"] = f"{object_name} (без назви) #{ordinal} — {record['region']}"
        else:
            record["dedupeObject"] = object_name
            record["displayObject"] = object_name


def scope_for(region: str, place: str, target: str) -> str:
    # Attribution words in the hit column (for example, "українські дрони")
    # must not turn a Russian target into a Ukrainian-location record.
    text = f"{region} {place}".lower()
    if any(keyword in text for keyword in MARITIME_HINTS):
        return "MARITIME"
    # Krymsk is a Russian city in Krasnodar Krai, not occupied Crimea.
    text = text.replace("кримськ", "")
    if any(keyword in text for keyword in UKRAINE_HINTS):
        return "UKRAINE_OR_OCCUPIED_UKRAINIAN_TERRITORY"
    if any(keyword in text for keyword in RUSSIA_HINTS) or any(
        keyword in text
        for keyword in (
            "республіка",
            "башкортостан",
            "комі",
            "чувашія",
            "дагестан",
            "чечня",
            "омськ",
        )
    ):
        return "RUSSIA"
    return "UNKNOWN"


def point_for(region: str, place: str, target: str) -> dict[str, Any] | None:
    text = f"{region} {place} {target}".lower()
    # Prefer explicit city/facility anchors over a broader region name when
    # both occur in the same row (for example, Alabuga inside Tatarstan).
    point_keys = list(SPECIFIC_POINT_KEYS) + [
        key for key in sorted(REGION_POINTS, key=len, reverse=True)
        if key not in SPECIFIC_POINT_KEYS
    ]
    for key in point_keys:
        lat, lng, region_id = REGION_POINTS[key]
        if key in text:
            maritime = any(keyword in text for keyword in MARITIME_HINTS)
            if "азовське море" in text:
                return {"lat": lat, "lng": lng, "radiusKm": 120, "regionId": None, "maritimeAreaId": "azov_sea_general", "theater": "AZOV_SEA", "precision": "MARITIME_REGIONAL", "labelEn": "Sea of Azov", "labelUk": "Азовське море"}
            if "каспійське море" in text or "середземне море" in text:
                return {"lat": lat, "lng": lng, "radiusKm": 250, "regionId": None, "maritimeAreaId": "general_maritime_area", "theater": "MARITIME_GENERAL", "precision": "MARITIME_REGIONAL", "labelEn": "General maritime area", "labelUk": "Морська акваторія"}
            if "чорне море" in text or "керченськ" in text or "севастопол" in text:
                return {"lat": lat, "lng": lng, "radiusKm": 150 if maritime else 90, "regionId": region_id, "maritimeAreaId": "black_sea_general" if maritime else None, "theater": "BLACK_SEA" if maritime else "RUSSIA_INTERNAL", "precision": "MARITIME_REGIONAL" if maritime else "CITY_OR_REGION_ANCHOR", "labelEn": "Black Sea" if maritime else place, "labelUk": "Чорне море" if maritime else place}
            return {"lat": lat, "lng": lng, "radiusKm": 110, "regionId": region_id, "maritimeAreaId": None, "theater": "RUSSIA_INTERNAL", "precision": "REGION_LEVEL", "labelEn": place or region, "labelUk": place or region}
    if "чорне море" in text:
        return {"lat": 43.5, "lng": 34.0, "radiusKm": 300, "regionId": None, "maritimeAreaId": "black_sea_general", "theater": "BLACK_SEA", "precision": "MARITIME_REGIONAL", "labelEn": "Black Sea", "labelUk": "Чорне море"}
    if "азовське море" in text:
        return {"lat": 46.0, "lng": 37.0, "radiusKm": 120, "regionId": None, "maritimeAreaId": "azov_sea_general", "theater": "AZOV_SEA", "precision": "MARITIME_REGIONAL", "labelEn": "Sea of Azov", "labelUk": "Азовське море"}
    return None


def record_id(date: str, region: str, place: str, target: str, identity: str) -> str:
    digest = hashlib.sha1(f"{date}|{region}|{place}|{target}|{identity}".encode("utf-8")).hexdigest()[:12]
    return f"event_{date.replace('-', '') or 'unknown'}_wiki_uav_strike_{digest}"


def table_records(soup: BeautifulSoup, table: Any, table_index: int) -> list[dict[str, Any]]:
    grid = expand_table(table)
    if not grid or len(grid[0]) < 4:
        return []
    headers = [cell_text(cell).lower() for cell in grid[0][:4]]
    if headers != ["дата", "регіон", "об'єкт", "влучання"]:
        return []
    section = TABLE_SECTIONS.get(table_index, "Unknown table")
    current_date = ""
    records = []
    for row_number, row in enumerate(grid[1:], start=2):
        date_raw = cell_text(row[0])
        candidate_date = normalize_date(date_raw, current_date)
        if candidate_date:
            current_date = candidate_date
        region = cell_text(row[1])
        obj = cell_text(row[2])
        hit = cell_text(row[3])
        if not any((region, obj, hit)):
            continue
        citations = citations_for_cell(soup, row[3])
        if not citations:
            seen_note_ids = set()
            citations = []
            for cell in row:
                for citation in citations_for_cell(soup, cell):
                    if citation["noteId"] not in seen_note_ids:
                        seen_note_ids.add(citation["noteId"])
                        citations.append(citation)
        records.append({
            "rowNumber": row_number,
            "tableIndex": table_index,
            "section": section,
            "dateRaw": date_raw,
            "date": current_date,
            "region": region,
            "object": obj,
            "hit": hit,
            "citationNumbers": [c["number"] for c in citations if c["number"] is not None],
            "citations": citations,
        })
    return records


def is_wikipedia_generated_event(item: dict[str, Any]) -> bool:
    return "_wiki_uav_strike_" in str(item.get("id") or "")


def is_current_page_event(item: dict[str, Any]) -> bool:
    if not is_wikipedia_generated_event(item):
        return False
    event_year = str(item.get("date") or "")[:4]
    return event_year == "2026" if SOURCE_ID.endswith("_2026") else event_year in {"2022", "2023", "2024", "2025"}


def load_existing_keys() -> dict[tuple[str, str], list[dict[str, Any]]]:
    keys: dict[tuple[str, str], list[dict[str, Any]]] = {}
    # The registry is a derived, source-less index and can contain rows from
    # this same import.  Compare against the app event snapshot instead.
    files = [FINAL_EVENTS]
    for path in files:
        if not path.exists():
            continue
        root = json.loads(path.read_text(encoding="utf-8"))
        items = root.get("items", []) if "items" in root else root.get("events", [])
        for item in items:
            if is_wikipedia_generated_event(item):
                continue
            date = item.get("strikeDate") or item.get("date") or ""
            names = [
                item.get("objectName"), item.get("titleUk"), item.get("titleEn"),
            ]
            for name in names:
                if name:
                    key = (normalize_key(str(date)), normalize_key(str(name)))
                    keys.setdefault(key, []).append({"file": path.name, "id": item.get("id"), "name": name})
    return keys


def exact_duplicate(record: dict[str, Any], keys: dict[tuple[str, str], list[dict[str, Any]]]) -> list[dict[str, Any]]:
    date = normalize_key(record["date"])
    candidates = [record.get("dedupeObject") or record["object"]]
    matches = []
    for candidate in candidates:
        matches.extend(keys.get((date, normalize_key(candidate)), []))
    unique = {(m.get("file"), m.get("id"), m.get("name")): m for m in matches}
    return list(unique.values())


def make_app_event(record: dict[str, Any], point: dict[str, Any], citations: list[dict[str, Any]]) -> dict[str, Any]:
    source_ids = citation_source_ids(citations)
    category = category_for(record["region"], record["object"], record["hit"])
    return {
        "id": record_id(record["date"], record["region"], record["object"], record["hit"], record.get("dedupeObject") or record["object"]),
        "status": "DISCOVERY_DRAFT",
        "titleEn": record.get("displayObject") or record["object"] or record["place"],
        "titleUk": record.get("displayObject") or record["object"] or record["place"],
        "date": record["date"],
        "datePrecision": "DAY",
        "category": category,
        "eventScope": "MARITIME" if record["scope"] == "MARITIME" else "TERRITORIAL_RUSSIA",
        "theater": point["theater"],
        "regionId": point["regionId"],
        "federalDistrictId": None,
        "maritimeAreaId": point["maritimeAreaId"],
        "sanctionsJurisdictionId": None,
        "approximateLocationLabelEn": point["labelEn"],
        "approximateLocationLabelUk": point["labelUk"],
        "lat": point["lat"],
        "lng": point["lng"],
        "radiusKm": point["radiusKm"],
        "precision": point["precision"],
        "assetId": None,
        "actor": "UKRAINIAN_FORCES",
        "actorConfidence": "DISCOVERY_ONLY",
        "actorNote": "Attribution is based on the original references listed below.",
        "verificationStatus": "DISCOVERY_ONLY",
        "severity": "UNKNOWN",
        "summaryEn": record["hit"],
        "summaryUk": record["hit"],
        "impactTags": f"WIKIPEDIA_DISCOVERY, {category}, {record['section']}",
        "sources": ", ".join(source_ids or [SOURCE_ID]),
        "safetyNotes": "Map position is an approximate regional anchor; use the original references for event details.",
        "createdAt": f"{record['date']}T00:00:00Z",
        "updatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--year", choices=("2022_2025", "2026"), default="2022_2025")
    args = parser.parse_args()
    configure_source(args.year)

    FINAL_DIR.mkdir(parents=True, exist_ok=True)
    response = requests.get(SOURCE_URL, headers=HEADERS, timeout=60)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "lxml")

    records = []
    for table_index, table in enumerate(soup.find_all("table")):
        records.extend(table_records(soup, table, table_index))

    assign_record_identities(records)
    seen_page: dict[tuple[str, str], dict[str, Any]] = {}
    page_duplicates = []
    for record in records:
        key = (normalize_key(record["date"]), normalize_key(record["dedupeObject"]))
        if key in seen_page and key[0] and key[1]:
            page_duplicates.append({"duplicate": record, "first": seen_page[key]})
        else:
            seen_page[key] = record

    existing_keys = load_existing_keys()
    app_events = []
    duplicate_existing = []
    unmappable = []
    seen_import_keys = set()
    for record in records:
        record["category"] = category_for(record["region"], record["object"], record["hit"])
        record["scope"] = scope_for(record["region"], record["object"], record["hit"])
        record["mapPoint"] = point_for(record["region"], record["object"], record["hit"])
        record["dedupeKey"] = f"{normalize_key(record['dedupeObject'])}||{normalize_key(record['date'])}"
        matches = exact_duplicate(record, existing_keys)
        if matches:
            record["importStatus"] = "DUPLICATE_OF_CURRENT_KB"
            duplicate_existing.append({"record": record, "matches": matches})
            continue
        if record["scope"] not in {"RUSSIA", "MARITIME"}:
            record["importStatus"] = "RAW_ONLY_OUTSIDE_APP_SCOPE"
            continue
        if not record["date"] or not record["object"]:
            record["importStatus"] = "RAW_ONLY_MISSING_DEDUPE_FIELDS"
            continue
        point = record["mapPoint"]
        if not point:
            record["importStatus"] = "RAW_ONLY_UNMAPPABLE_LOCATION"
            unmappable.append(record)
            continue
        import_key = (normalize_key(record["date"]), normalize_key(record["dedupeObject"]))
        if import_key in seen_import_keys:
            record["importStatus"] = "DUPLICATE_WITHIN_IMPORT"
            continue
        seen_import_keys.add(import_key)
        record["importStatus"] = "NEW_APP_ADDITION"
        app_events.append(make_app_event(record, point, record["citations"]))

    sbs_shadow_fleet_count = apply_sbs_shadow_fleet_display_points(app_events)

    snapshot = {
        "schemaVersion": 1,
        "datasetId": SOURCE_ID.removeprefix("source_"),
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "source": {"id": SOURCE_ID, "title": "Перелік атак БпЛА по російських цілях (2022-2025)", "url": SOURCE_URL, "sourceType": "DISCOVERY_ONLY"},
        "records": records,
        "pageDuplicateGroups": page_duplicates,
        "summary": {
            "allTableRows": len(records),
            "newAppAdditions": len(app_events),
            "duplicatesWithCurrentKnowledgeBase": len(duplicate_existing),
            "duplicatesWithinPage": len(page_duplicates),
            "rawOnlyOutsideAppScope": sum(1 for r in records if r.get("importStatus") == "RAW_ONLY_OUTSIDE_APP_SCOPE"),
            "unmappableRussiaOrMaritime": len(unmappable),
        },
    }
    snapshot["source"]["title"] = SOURCE_TITLE
    SNAPSHOT_FILE.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
    citation_registry = write_citation_source_registry()

    existing_manual = {"schemaVersion": 1, "sourceFile": "manual_additions", "events": []}
    if MANUAL_EVENTS.exists():
        existing_manual = json.loads(MANUAL_EVENTS.read_text(encoding="utf-8"))
    by_id = {
        event.get("id"): event
        for event in existing_manual.get("events", [])
        if event.get("id") and not is_current_page_event(event)
    }
    for event in app_events:
        by_id[event["id"]] = event
    manual_payload = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "sourceFile": f"manual_additions_and_{SOURCE_ID}",
        "importTarget": "Room EventEntity",
        "recordCount": len(by_id),
        "skippedCount": 0,
        "events": sorted(by_id.values(), key=lambda event: (event.get("date", ""), event.get("id", "")), reverse=True),
    }
    MANUAL_EVENTS.write_text(json.dumps(manual_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        f"Generated at: {snapshot['generatedAt']}",
        f"Source: {SOURCE_URL}",
        f"All table rows: {len(records)}",
        f"New app additions: {len(app_events)}",
        f"Duplicates with current knowledge base: {len(duplicate_existing)}",
        f"Duplicates within Wikipedia page: {len(page_duplicates)}",
        f"Raw-only outside app scope: {snapshot['summary']['rawOnlyOutsideAppScope']}",
        f"Unmappable Russia/maritime rows: {len(unmappable)}",
        f"SBS shadow fleet display points: {sbs_shadow_fleet_count}",
        f"Concrete citation sources: {citation_registry['recordCount']}",
        "",
        "Duplicate candidates with current knowledge base:",
    ]
    if duplicate_existing:
        for item in duplicate_existing:
            record = item["record"]
            lines.append(f"- {record['date']} :: {record['object']} :: current={item['matches']}")
    else:
        lines.append("- none")
    lines.append("")
    lines.append("Duplicate rows within the Wikipedia page:")
    if page_duplicates:
        for item in page_duplicates:
            duplicate = item["duplicate"]
            first = item["first"]
            lines.append(
                f"- {duplicate['date']} :: {duplicate['object']} :: "
                f"first row {first['rowNumber']}, duplicate row {duplicate['rowNumber']}"
            )
    else:
        lines.append("- none")
    REPORT_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(json.dumps(snapshot["summary"], ensure_ascii=False, indent=2))
    print(f"Snapshot: {SNAPSHOT_FILE}")
    print(f"Manual additions: {MANUAL_EVENTS}")
    print(f"Report: {REPORT_FILE}")
    print(f"Citation sources: {CITATION_SOURCES_FILE}")


if __name__ == "__main__":
    main()
