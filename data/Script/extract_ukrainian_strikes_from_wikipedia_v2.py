#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Robust extractor for Ukrainian-attributed strike rows from Ukrainian Wikipedia seasonal pages.

What it does:
- Visits all seasonal Ukrainian Wikipedia pages.
- Parses wikitable HTML directly with rowspan/colspan support.
- Keeps only the table side titled "Удари завдані Україною".
- Produces one JSON file with target categorization.
- Marks every record as DISCOVERY_ONLY and requiresManualReview=true.

Install:
    python -m pip install requests beautifulsoup4 lxml

Run:
    python extract_ukrainian_strikes_from_wikipedia_v2.py

Output:
    wiki_ukrainian_strikes_all_years_extracted_v0_2.json
"""

import re
import json
import hashlib
from datetime import datetime, timezone
from urllib.parse import quote

import requests
from bs4 import BeautifulSoup


TITLES = [
    "Перелік ракетних ударів під час російського вторгнення (лютий-весна 2022)",
    "Перелік ракетних ударів під час російського вторгнення (літо 2022)",
    "Перелік ракетних ударів під час російського вторгнення (осінь 2022)",
    "Перелік ракетних ударів під час російського вторгнення (зима 2022/2023)",
    "Перелік ракетних ударів під час російського вторгнення (весна 2023)",
    "Перелік ракетних ударів під час російського вторгнення (літо 2023)",
    "Перелік ракетних ударів під час російського вторгнення (осінь 2023)",
    "Перелік ракетних ударів під час російського вторгнення (зима 2023/2024)",
    "Перелік ракетних ударів під час російського вторгнення (весна 2024)",
    "Перелік ракетних ударів під час російського вторгнення (літо 2024)",
    "Перелік ракетних ударів під час російського вторгнення (осінь 2024)",
    "Перелік ракетних ударів під час російського вторгнення (зима 2024/2025)",
    "Перелік ракетних ударів під час російського вторгнення (весна 2025)",
    "Перелік ракетних ударів під час російського вторгнення (літо 2025)",
    "Перелік ракетних ударів під час російського вторгнення (осінь 2025)",
    "Перелік ракетних ударів під час російського вторгнення (зима 2025/2026)",
    "Перелік ракетних ударів під час російського вторгнення (весна 2026)",
    "Перелік ракетних ударів під час російського вторгнення (літо 2026)",
]


CATEGORY_KEYWORDS = {
    "AIRFIELD_OR_AVIATION_BASE": [
        "аеродром", "авіабаза", "літак", "літаки", "вертоліт", "авіаційн",
        "вкс", "су-", "ту-", "іл-", "міг-", "бомбардувальник"
    ],
    "AMMUNITION_DEPOT_OR_GRAU": [
        "склад боєприпас", "боєприпас", "арсенал", "грау", "детонац",
        "склад бк", "бк", "вибухи на складі"
    ],
    "MILITARY_BASE_OR_COMMAND": [
        "військова база", "військова частина", "в/ч", "штаб", "пункт управління",
        "казарм", "полігон", "військова інфраструктура", "військовий об'єкт",
        "військовий об’єкт", "ппо", "рлс", "радар"
    ],
    "FUEL_OR_ENERGY_LOGISTICS": [
        "нафтобаза", "нпз", "нафтоперероб", "резервуар", "палив", "пального",
        "пмм", "енергет", "електропідстанц", "підстанц", "газопровід", "нафтопровід"
    ],
    "RAIL_OR_BRIDGE_LOGISTICS": [
        "залізнич", "міст", "пором", "логіст", "станція", "ешелон",
        "транспортна інфраструктура", "портова інфраструктура"
    ],
    "NAVAL_OR_MARITIME": [
        "кораб", "катер", "буксир", "судно", "порт", "акватор", "чорне море",
        "азовське море", "флот", "крейсер", "вдк", "морськ"
    ],
    "INDUSTRIAL_DEFENSE": [
        "завод", "виробництво", "вибухівк", "оборонн", "впк", "ремонтний завод",
        "машинобуд", "авіазавод", "порох"
    ],
}


RUSSIA_HINTS = [
    "рф", "росія", "російська федерація",
    "бєлгород", "белгород", "брянськ", "курськ", "ростов", "таганрог",
    "міллерово", "міллєрово", "ейськ", "єйськ", "краснодар", "воронеж",
    "твер", "торопець", "москва", "москов", "ленінград", "санкт-петербург",
    "орел", "липец", "липецк", "волгоград", "саратов", "тамбов", "калуг",
    "рязань", "нижньогород", "псков", "новгород", "ставрополь", "мурманськ",
    "іркут", "амур", "красноярськ", "татарстан", "башкортостан", "удмурт",
    "смоленськ", "тула", "костром", "ярослав", "самара", "пенза", "ульянов",
    "астрахан", "дагестан", "адиге", "мордов", "чуваш", "марій ел", "карел",
]

UKRAINE_OR_OCCUPIED_HINTS = [
    "донецьк", "луганськ", "крим", "севастоп", "керч", "феодос", "джанкой",
    "бердянськ", "мелітоп", "маріуп", "херсон", "нова каховка", "енергодар",
    "токмак", "скадовськ", "генічеськ", "запорізька обл", "донецька обл",
    "луганська обл", "херсонська обл", "харківська обл", "миколаївська обл",
    "одеська обл", "київська обл", "львівська обл", "чернігівська обл",
    "сумська обл", "житомирська обл", "полтавська обл", "дніпропетровська обл",
    "рівненська обл", "тернопільська обл", "івано-франківська обл", "хмельницька обл",
    "черкаська обл", "вінницька обл", "волинська обл", "україна"
]

MARITIME_HINTS = [
    "чорне море", "азовське море", "керчен", "акватор", "крейсер", "кораб", "судно", "буксир", "катер"
]


def wiki_url(title: str) -> str:
    return "https://uk.wikipedia.org/wiki/" + quote(title.replace(" ", "_"), safe="/()_")


def clean_text(text) -> str:
    if text is None:
        return ""
    text = str(text)
    text = re.sub(r"\[[^\]]+\]", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def normalize_date(raw: str) -> str:
    raw = clean_text(raw)
    m = re.search(r"(\d{2})\.(\d{2})\.(\d{4})", raw)
    if m:
        d, mo, y = m.groups()
        return f"{y}-{mo}-{d}"
    return raw


def categorize(region: str, place: str, hit: str) -> str:
    text = f"{region} {place} {hit}".lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(k in text for k in keywords):
            return category
    return "UNCLEAR_OR_OTHER"


def territory_scope(region: str, place: str, hit: str) -> str:
    text = f"{region} {place} {hit}".lower()
    if any(k in text for k in MARITIME_HINTS):
        return "MARITIME"
    if any(k in text for k in RUSSIA_HINTS):
        return "RUSSIA"
    if any(k in text for k in UKRAINE_OR_OCCUPIED_HINTS):
        return "UKRAINE_OR_OCCUPIED_UKRAINIAN_TERRITORY"
    return "UNKNOWN"


def app_status(scope: str) -> str:
    if scope == "RUSSIA":
        return "CANDIDATE_FOR_RUSSIA_BASELINE"
    if scope == "MARITIME":
        return "CANDIDATE_FOR_MARITIME_BASELINE"
    if scope == "UKRAINE_OR_OCCUPIED_UKRAINIAN_TERRITORY":
        return "EXCLUDE_FOR_CURRENT_APP"
    return "MANUAL_REVIEW"


def record_id(date_iso: str, source_id: str, region: str, place: str, hit: str) -> str:
    raw = f"{date_iso}|{source_id}|{region}|{place}|{hit}"
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:10]
    date_part = re.sub(r"[^0-9]", "", date_iso) or "unknown"
    return f"event_{date_part}_wiki_ukraine_strike_{digest}"


def expand_html_table(table):
    """
    Convert HTML table into a rectangular grid while respecting rowspan and colspan.
    Each cell has:
      text, tag, is_header
    """
    grid = []
    spans = {}

    rows = table.find_all("tr")
    for r_idx, tr in enumerate(rows):
        row = []
        c_idx = 0

        # Fill cells carried from rowspans.
        while (r_idx, c_idx) in spans:
            row.append(spans.pop((r_idx, c_idx)))
            c_idx += 1

        cells = tr.find_all(["th", "td"], recursive=False)
        for cell in cells:
            while (r_idx, c_idx) in spans:
                row.append(spans.pop((r_idx, c_idx)))
                c_idx += 1

            rowspan = int(cell.get("rowspan", 1) or 1)
            colspan = int(cell.get("colspan", 1) or 1)
            value = {
                "text": clean_text(cell.get_text(" ", strip=True)),
                "tag": cell.name,
                "is_header": cell.name == "th",
            }

            for dc in range(colspan):
                row.append(value)

            for dr in range(1, rowspan):
                for dc in range(colspan):
                    spans[(r_idx + dr, c_idx + dc)] = value

            c_idx += colspan

        # Fill any remaining carried cells.
        while (r_idx, c_idx) in spans:
            row.append(spans.pop((r_idx, c_idx)))
            c_idx += 1

        grid.append(row)

    max_cols = max((len(r) for r in grid), default=0)
    for r in grid:
        while len(r) < max_cols:
            r.append({"text": "", "tag": "", "is_header": False})
    return grid


def find_ukraine_columns(grid):
    """
    Finds columns belonging to the "Удари завдані Україною" section and date column.
    Returns:
      uk_cols: list of column indexes, usually 4 columns [region, place, weapons, hit]
      date_col: middle date column index
      header_end_row: last header row index
    """
    if not grid:
        return None, None, None

    for r_idx, row in enumerate(grid[:6]):
        texts = [c["text"].lower() for c in row]
        if any("удари завдані україною" in t for t in texts):
            uk_cols = [i for i, t in enumerate(texts) if "удари завдані україною" in t]
            date_candidates = [i for i, t in enumerate(texts) if t == "дата" or " дата" in t or t.endswith("дата")]
            if date_candidates:
                # Prefer the first date column after Ukraine columns.
                after_uk = [i for i in date_candidates if i > max(uk_cols)]
                date_col = after_uk[0] if after_uk else date_candidates[0]
            else:
                date_col = max(uk_cols) + 1

            # Header usually occupies this row and next row.
            header_end_row = min(r_idx + 1, len(grid) - 1)
            return uk_cols, date_col, header_end_row

    # Fallback for flattened tables where header text is absent:
    # Search a row that looks like: регіон місце ракети влучання дата ...
    for r_idx, row in enumerate(grid[:8]):
        texts = [c["text"].lower() for c in row]
        try:
            date_col = texts.index("дата")
        except ValueError:
            continue
        if date_col >= 4:
            return list(range(0, date_col)), date_col, r_idx

    return None, None, None


def looks_empty_or_non_event(region: str, place: str, weapons: str, hit: str) -> bool:
    joined = clean_text(" ".join([region, place, weapons, hit]))
    if not joined:
        return True
    if joined in {"—", "-", "–"}:
        return True
    if joined.lower() in {"регіон місце ракети влучання", "удари завдані україною"}:
        return True
    # Avoid rows that are only Russian-side artifacts or PПО entries from the opposite side.
    if hit.lower().startswith("перехоплено українською ппо") and not place:
        return True
    return False


def extract_from_table(table, source_id: str, source_url: str):
    grid = expand_html_table(table)
    uk_cols, date_col, header_end_row = find_ukraine_columns(grid)
    if uk_cols is None or date_col is None:
        return []

    records = []
    current_date = ""

    for row in grid[header_end_row + 1:]:
        date_raw = row[date_col]["text"] if date_col < len(row) else ""
        if re.search(r"\d{2}\.\d{2}\.\d{4}", date_raw):
            current_date = date_raw

        values = [row[i]["text"] if i < len(row) else "" for i in uk_cols]

        # Normalize to four fields: region, place, weapons, hit.
        if len(values) >= 4:
            region, place, weapons, hit = values[0], values[1], values[2], values[3]
        elif len(values) == 3:
            region, place, weapons, hit = values[0], values[1], "", values[2]
        elif len(values) == 2:
            region, place, weapons, hit = values[0], "", "", values[1]
        elif len(values) == 1:
            region, place, weapons, hit = "", "", "", values[0]
        else:
            continue

        region = clean_text(region)
        place = clean_text(place)
        weapons = clean_text(weapons)
        hit = clean_text(hit)

        if looks_empty_or_non_event(region, place, weapons, hit):
            continue

        # If date is empty due to table complexity, still keep as manual-review.
        date_iso = normalize_date(current_date or date_raw)

        category = categorize(region, place, hit)
        scope = territory_scope(region, place, hit)

        records.append({
            "id": record_id(date_iso, source_id, region, place, hit),
            "status": "DISCOVERY_DRAFT",
            "date": date_iso,
            "datePrecision": "DAY" if re.fullmatch(r"\d{4}-\d{2}-\d{2}", date_iso) else "UNKNOWN_OR_TABLE_INHERITED",
            "sourcePageId": source_id,
            "sourceUrl": source_url,
            "sourceUse": "DISCOVERY_ONLY",
            "attributedActor": "UKRAINIAN_FORCES",
            "territoryScope": scope,
            "regionRaw": region,
            "placeRaw": place,
            "weaponsRaw": weapons,
            "targetSummaryRaw": hit,
            "targetCategory": category,
            "category": {
                "AIRFIELD_OR_AVIATION_BASE": "AIRFIELD_OR_MILITARY_INFRASTRUCTURE_DISRUPTION",
                "AMMUNITION_DEPOT_OR_GRAU": "AMMUNITION_DEPOT_DISRUPTION",
                "MILITARY_BASE_OR_COMMAND": "MILITARY_INFRASTRUCTURE_DISRUPTION",
                "FUEL_OR_ENERGY_LOGISTICS": "FUEL_SUPPLY_DISRUPTION",
                "RAIL_OR_BRIDGE_LOGISTICS": "LOGISTICS_PRESSURE",
                "NAVAL_OR_MARITIME": "MARITIME_ASSET_DISRUPTION",
                "INDUSTRIAL_DEFENSE": "INDUSTRIAL_DISRUPTION",
                "UNCLEAR_OR_OTHER": "MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR",
            }.get(category, "MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR"),
            "appInclusionStatus": app_status(scope),
            "requiresManualReview": True,
            "safetyNotes": "Wikipedia discovery-only record. No exact coordinates. Confirm through allowed sources before production import."
        })

    return records


def fetch_page(url: str) -> str:
    headers = {
        "User-Agent": "Mozilla/5.0 compatible OSINT-baseline-research/0.2; discovery-only; manual review"
    }
    response = requests.get(url, headers=headers, timeout=45)
    response.raise_for_status()
    return response.text


def main():
    all_records = []
    sources = []

    for idx, title in enumerate(TITLES, start=1):
        source_id = f"source_wiki_uk_missile_strikes_{idx:02d}"
        url = wiki_url(title)
        sources.append({
            "id": source_id,
            "title": title,
            "url": url,
            "sourceType": "DISCOVERY_ONLY"
        })

        print(f"[{idx:02d}/{len(TITLES):02d}] Fetching: {title}")

        try:
            html = fetch_page(url)
            soup = BeautifulSoup(html, "lxml")
            tables = soup.find_all("table")
        except Exception as exc:
            print(f"  ERROR fetch/parse: {exc}")
            continue

        before = len(all_records)
        for table in tables:
            table_text = table.get_text(" ", strip=True).lower()
            if "удари завдані україною" not in table_text:
                continue
            try:
                rows = extract_from_table(table, source_id, url)
                all_records.extend(rows)
            except Exception as exc:
                print(f"  ERROR table extraction: {exc}")

        print(f"  extracted records: {len(all_records) - before}")

    # Deduplicate by raw content.
    seen = set()
    deduped = []
    for r in all_records:
        key = (r.get("date"), r.get("sourcePageId"), r.get("regionRaw"), r.get("placeRaw"), r.get("targetSummaryRaw"))
        if key in seen:
            continue
        seen.add(key)
        deduped.append(r)

    by_category = {}
    for r in deduped:
        by_category.setdefault(r["targetCategory"], []).append(r["id"])

    output = {
        "datasetId": "wiki_ukrainian_strikes_all_years_extracted_v0_2",
        "status": "DISCOVERY_DRAFT",
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "sourcePolicy": "Wikipedia is discovery-only. Every record requires confirmation from official, institutional, or reputable media sources before production import.",
        "sources": sources,
        "records": deduped,
        "recordsByTargetCategory": by_category,
        "summary": {
            "totalRecords": len(deduped),
            "russiaCandidates": sum(1 for r in deduped if r["territoryScope"] == "RUSSIA"),
            "maritimeCandidates": sum(1 for r in deduped if r["territoryScope"] == "MARITIME"),
            "excludedUkraineOrOccupied": sum(1 for r in deduped if r["appInclusionStatus"] == "EXCLUDE_FOR_CURRENT_APP"),
            "manualReview": sum(1 for r in deduped if r["requiresManualReview"]),
            "byTargetCategory": {k: len(v) for k, v in by_category.items()}
        }
    }

    out_path = "wiki_ukrainian_strikes_all_years_extracted_v0_2.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print(f"\nSaved: {out_path}")
    print(json.dumps(output["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
