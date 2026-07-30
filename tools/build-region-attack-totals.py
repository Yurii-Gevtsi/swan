#!/usr/bin/env python3
"""Build the aggregate attack-total layer from the combined Wikipedia list."""

from __future__ import annotations

import argparse
import json
import re
from collections import OrderedDict, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

import requests
from bs4 import BeautifulSoup


SOURCE_URL = (
    "https://uk.wikipedia.org/wiki/"
    "%D0%9F%D0%B5%D1%80%D0%B5%D0%BB%D1%96%D0%BA_%D0%B9%D0%BC%D0%BE%D0%B2%D1%96%D1%80%D0%BD%D0%B8%D1%85_"
    "%D1%83%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D1%81%D1%8C%D0%BA%D0%B8%D1%85_%D0%B0%D1%82%D0%B0%D0%BA_%D0%BD%D0%B0_"
    "%D1%80%D0%BE%D1%81%D1%96%D0%B9%D1%81%D1%8C%D0%BA%D1%96%D0%B9_%D1%82%D0%B0_"
    "%D0%B1%D1%96%D0%BB%D0%BE%D1%80%D1%83%D1%81%D1%8C%D0%BA%D1%96%D0%B9_%D1%82%D0%B5%D1%80%D0%B8%D1%82%D0%BE%D1%80%D1%96%D1%97"
)

REGION_ALIASES: dict[str, tuple[str, str]] = {
    "адигея": ("RU-AD", "Адигея"),
    "алтайський край": ("RU-ALT", "Алтайський край"),
    "амурська обл": ("RU-AMU", "Амурська область"),
    "амурська область": ("RU-AMU", "Амурська область"),
    "архангельська обл": ("RU-ARK", "Архангельська область"),
    "астраханська обл": ("RU-AST", "Астраханська область"),
    "астраханська область": ("RU-AST", "Астраханська область"),
    "башкортостан": ("RU-BA", "Башкортостан"),
    "бєлгородська обл": ("RU-BEL", "Бєлгородська область"),
    "бєлгородська область": ("RU-BEL", "Бєлгородська область"),
    "брянська обл": ("RU-BRY", "Брянська область"),
    "брянська область": ("RU-BRY", "Брянська область"),
    "бурятія": ("RU-BU", "Бурятія"),
    "чечня": ("RU-CE", "Чечня"),
    "челябінська обл": ("RU-CHE", "Челябінська область"),
    "чувашія": ("RU-CU", "Чувашія"),
    "дагестан": ("RU-DA", "Дагестан"),
    "інгушетія": ("RU-IN", "Інгушетія"),
    "івановська обл": ("RU-IVA", "Івановська область"),
    "іркутська обл": ("RU-IRK", "Іркутська область"),
    "калузька обл": ("RU-KLU", "Калузька область"),
    "калужська обл": ("RU-KLU", "Калузька область"),
    "калузька область": ("RU-KLU", "Калузька область"),
    "калінінградська обл": ("RU-KGD", "Калінінградська область"),
    "калмикія": ("RU-KL", "Калмикія"),
    "камчатський край": ("RU-KAM", "Камчатський край"),
    "камчатський край": ("RU-KAM", "Камчатський край"),
    "камчатська край": ("RU-KAM", "Камчатський край"),
    "карелія": ("RU-KR", "Карелія"),
    "кабардино-балкарія": ("RU-KB", "Кабардино-Балкарія"),
    "карачаєво-черкесія": ("RU-KC", "Карачаєво-Черкесія"),
    "кемеровська обл": ("RU-KEM", "Кемеровська область"),
    "хабаровський край": ("RU-KHA", "Хабаровський край"),
    "хабаровський кр": ("RU-KHA", "Хабаровський край"),
    "ханти-мансійський ао": ("RU-KHM", "Ханти-Мансійський АО"),
    "кіровська обл": ("RU-KIR", "Кіровська область"),
    "комі": ("RU-KO", "Республіка Комі"),
    "республіка комі": ("RU-KO", "Республіка Комі"),
    "костромська обл": ("RU-KOS", "Костромська область"),
    "краснодарський край": ("RU-KDA", "Краснодарський край"),
    "красноярський край": ("RU-KYA", "Красноярський край"),
    "курганська обл": ("RU-KGN", "Курганська область"),
    "курська обл": ("RU-KRS", "Курська область"),
    "курська область": ("RU-KRS", "Курська область"),
    "ленінградська обл": ("RU-LEN", "Ленінградська область"),
    "ленінградська область": ("RU-LEN", "Ленінградська область"),
    "ленінгградська обл": ("RU-LEN", "Ленінградська область"),
    "липецка обл": ("RU-LIP", "Липецька область"),
    "липецька обл": ("RU-LIP", "Липецька область"),
    "липецька область": ("RU-LIP", "Липецька область"),
    "магаданська обл": ("RU-MAG", "Магаданська область"),
    "марій ел": ("RU-ME", "Марій Ел"),
    "мордовія": ("RU-MO", "Мордовія"),
    "москва": ("RU-MOW", "Москва"),
    "московська обл": ("RU-MOS", "Московська область"),
    "московська обла": ("RU-MOS", "Московська область"),
    "московська область": ("RU-MOS", "Московська область"),
    "мурманська обл": ("RU-MUR", "Мурманська область"),
    "ненецький автономний округ": ("RU-NEN", "Ненецький автономний округ"),
    "нижньогородська обл": ("RU-NIZ", "Нижньогородська область"),
    "нижегородська обл": ("RU-NIZ", "Нижньогородська область"),
    "новгородська обл": ("RU-NGR", "Новгородська область"),
    "новосибірська обл": ("RU-NVS", "Новосибірська область"),
    "омська обл": ("RU-OMS", "Омська область"),
    "оренбурзька обл": ("RU-ORE", "Оренбурзька область"),
    "орловська обл": ("RU-ORL", "Орловська область"),
    "пензенська обл": ("RU-PNZ", "Пензенська область"),
    "пензенська область": ("RU-PNZ", "Пензенська область"),
    "пермський край": ("RU-PER", "Пермський край"),
    "приморський край": ("RU-PRI", "Приморський край"),
    "псковська обл": ("RU-PSK", "Псковська область"),
    "ростовська обл": ("RU-ROS", "Ростовська область"),
    "ростовська область": ("RU-ROS", "Ростовська область"),
    "рязанська обл": ("RU-RYA", "Рязанська область"),
    "республіка алтай": ("RU-AL", "Республіка Алтай"),
    "самарська обл": ("RU-SAM", "Самарська область"),
    "санкт-петербург": ("RU-SPE", "Санкт-Петербург"),
    "саратовська обл": ("RU-SAR", "Саратовська область"),
    "саха": ("RU-SA", "Саха"),
    "сахалінська обл": ("RU-SAK", "Сахалінська область"),
    "свердловська обл": ("RU-SVE", "Свердловська область"),
    "смоленська обл": ("RU-SMO", "Смоленська область"),
    "смоленьска обл": ("RU-SMO", "Смоленська область"),
    "північна осетія": ("RU-SE", "Північна Осетія"),
    "північна осетія-аланія": ("RU-SE", "Північна Осетія"),
    "північна осетія — аланія": ("RU-SE", "Північна Осетія"),
    "ставропольський край": ("RU-STA", "Ставропольський край"),
    "ставропольский край": ("RU-STA", "Ставропольський край"),
    "тамбовська обл": ("RU-TAM", "Тамбовська область"),
    "татарстан": ("RU-TA", "Татарстан"),
    "томська обл": ("RU-TOM", "Томська область"),
    "тульська обл": ("RU-TUL", "Тульська область"),
    "тверська обл": ("RU-TVE", "Тверська область"),
    "тюменська обл": ("RU-TYU", "Тюменська область"),
    "тува": ("RU-TY", "Тува"),
    "удмуртія": ("RU-UD", "Удмуртія"),
    "ульяновська обл": ("RU-ULY", "Ульяновська область"),
    "владимирська обл": ("RU-VLA", "Владимирська область"),
    "владімірська обл": ("RU-VLA", "Владимирська область"),
    "володимирська обл": ("RU-VLA", "Владимирська область"),
    "вологодська обл": ("RU-VLG", "Вологодська область"),
    "волгоградська обл": ("RU-VGG", "Волгоградська область"),
    "волгоградська область": ("RU-VGG", "Волгоградська область"),
    "воронезька обл": ("RU-VOR", "Воронезька область"),
    "ярославська обл": ("RU-YAR", "Ярославська область"),
    "ярославльська обл": ("RU-YAR", "Ярославська область"),
    "єврейська ао": ("RU-YEV", "Єврейська АО"),
    "чукотський ао": ("RU-CHU", "Чукотський АО"),
    "чукотськиий ао": ("RU-CHU", "Чукотський АО"),
    "ямало-ненецький ао": ("RU-YAN", "Ямало-Ненецький АО"),
    "ямало-ненецкий ао": ("RU-YAN", "Ямало-Ненецький АО"),
    "ямало-нененцький ао": ("RU-YAN", "Ямало-Ненецький АО"),
    "ямало-ненеццький ао": ("RU-YAN", "Ямало-Ненецький АО"),
    "забайкальський край": ("RU-ZAB", "Забайкальський край"),
    "башкирія": ("RU-BA", "Башкортостан"),
    "якутія": ("RU-SA", "Саха"),
    "мінськ": ("BY-HM", "Мінськ"),
    "гомельська обл": ("BY-HO", "Гомельська область"),
    "білорусь, вітебськ": ("BY-VI", "Вітебська область"),
}


def clean_text(value: str) -> str:
    value = re.sub(r"\[\s*\d+\s*\]", "", value)
    value = re.sub(r"\s+", " ", value.replace("\xa0", " ")).strip()
    return value.strip(" ;,")


def normalize_key(value: str) -> str:
    value = clean_text(value).lower().replace("ё", "е")
    value = value.replace("обл.", "обл").replace("респ.", "республіка")
    value = re.sub(r"\s+", " ", value)
    return value.strip(" .;:,")


def parse_date(raw: str, current_year: str, previous: str) -> str:
    raw = clean_text(raw)
    if not raw:
        return previous
    match = re.search(r"(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?", raw)
    if not match:
        return previous
    day, month, year = match.groups()
    return f"{year or current_year}-{int(month):02d}-{int(day):02d}"


def expand_table(table: Any) -> list[list[Any]]:
    grid: list[list[Any]] = []
    rowspans: dict[int, tuple[Any, int]] = {}
    for tr in table.find_all("tr"):
        row: list[Any] = []
        col = 0
        cells = tr.find_all(["th", "td"], recursive=False)
        if not cells:
            continue
        for cell in cells:
            while col in rowspans:
                span_cell, remaining = rowspans[col]
                row.append(span_cell)
                if remaining <= 1:
                    del rowspans[col]
                else:
                    rowspans[col] = (span_cell, remaining - 1)
                col += 1
            colspan = int(cell.get("colspan", 1) or 1)
            rowspan = int(cell.get("rowspan", 1) or 1)
            for _ in range(colspan):
                row.append(cell)
                if rowspan > 1:
                    rowspans[col] = (cell, rowspan - 1)
                col += 1
        while col in rowspans:
            span_cell, remaining = rowspans[col]
            row.append(span_cell)
            if remaining <= 1:
                del rowspans[col]
            else:
                rowspans[col] = (span_cell, remaining - 1)
            col += 1
        grid.append(row)
    return grid


def extract_basemap_centers(path: Path) -> dict[str, tuple[float, float, str]]:
    text = path.read_text(encoding="utf-8")
    payload = json.loads(text.removeprefix("var BASEMAP = ").rstrip(";\n"))
    centers: dict[str, tuple[float, float, str]] = {}
    for area in payload.get("admin1", []):
        iso = area.get("iso")
        if not iso:
            continue
        points = [
            point
            for polygon in area.get("polygons", [])
            for ring in polygon
            for point in ring
            if isinstance(point, list) and len(point) >= 2
        ]
        if not points:
            continue
        lng = sum(point[0] for point in points) / len(points)
        lat = sum(point[1] for point in points) / len(points)
        centers[iso] = (round(lat, 5), round(lng, 5), area.get("name") or iso)
    return centers


def section_year(table: Any) -> str:
    heading = table.find_previous(["h2", "h3"])
    while heading:
        text = clean_text(heading.get_text(" ", strip=True))
        match = re.search(r"20\d{2}", text)
        if match:
            return match.group(0)
        heading = heading.find_previous(["h2", "h3"])
    return str(datetime.now().year)


def table_section(table: Any) -> str:
    heading = table.find_previous(["h2", "h3"])
    return clean_text(heading.get_text(" ", strip=True)) if heading else "Wikipedia table"


def citation_numbers(row: list[Any]) -> list[int]:
    numbers: set[int] = set()
    for cell in row:
        for ref in cell.select("sup.reference"):
            text = ref.get_text(" ", strip=True)
            for number in re.findall(r"\d+", text):
                numbers.add(int(number))
    return sorted(numbers)


def table_records(soup: BeautifulSoup) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    records: list[dict[str, Any]] = []
    skipped: list[dict[str, Any]] = []
    for table_index, table in enumerate(soup.select("table.wikitable"), start=1):
        grid = expand_table(table)
        if not grid:
            continue
        headers = [normalize_key(cell.get_text(" ", strip=True)) for cell in grid[0]]
        if "дата" not in headers or "регіон" not in headers:
            continue
        date_col = headers.index("дата")
        region_col = headers.index("регіон")
        place_col = headers.index("місце") if "місце" in headers else None
        attack_col = headers.index("тип атаки") if "тип атаки" in headers else None
        hit_col = headers.index("влучання") if "влучання" in headers else None
        year = section_year(table)
        section = table_section(table)
        current_date = ""
        for row_number, row in enumerate(grid[1:], start=2):
            if len(row) <= max(date_col, region_col):
                continue
            current_date = parse_date(row[date_col].get_text(" ", strip=True), year, current_date)
            region_raw = clean_text(row[region_col].get_text(" ", strip=True))
            if not current_date or not region_raw:
                continue
            region_key = normalize_key(region_raw)
            mapped = REGION_ALIASES.get(region_key)
            if not mapped:
                skipped.append({"table": table_index, "row": row_number, "date": current_date, "region": region_raw})
                continue
            iso, region_name_uk = mapped
            place = clean_text(row[place_col].get_text(" ", strip=True)) if place_col is not None and len(row) > place_col else ""
            attack_type = clean_text(row[attack_col].get_text(" ", strip=True)) if attack_col is not None and len(row) > attack_col else ""
            hit = clean_text(row[hit_col].get_text(" ", strip=True)) if hit_col is not None and len(row) > hit_col else ""
            citations = citation_numbers(row)
            source_rows = [table_index * 10000 + row_number]
            label_parts = [part for part in (place, attack_type, hit) if part and part != "—"]
            records.append(
                {
                    "date": current_date,
                    "iso": iso,
                    "regionNameUk": region_name_uk,
                    "place": place,
                    "attackType": attack_type,
                    "hit": hit,
                    "section": section,
                    "objects": label_parts[:2] if label_parts else [region_raw],
                    "citationNumbers": citations,
                    "sourceRows": source_rows,
                }
            )
    return records, skipped


def build(source_url: str, basemap_path: Path) -> dict[str, Any]:
    response = requests.get(source_url, timeout=60, headers={"User-Agent": "BlackSwanDataBuilder/1.0"})
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    centers = extract_basemap_centers(basemap_path)
    records, skipped = table_records(soup)

    by_region: OrderedDict[str, list[dict[str, Any]]] = OrderedDict()
    for record in records:
        if record["iso"] not in centers:
            skipped.append({"date": record["date"], "region": record["regionNameUk"], "reason": "missing basemap iso", "iso": record["iso"]})
            continue
        by_region.setdefault(record["iso"], []).append(record)

    regions = []
    total_attack_rows = 0
    for iso, rows in by_region.items():
        lat, lng, region_name_en = centers[iso]
        dates: list[dict[str, Any]] = []
        rows_by_date: dict[str, list[dict[str, Any]]] = defaultdict(list)
        for row in rows:
            rows_by_date[row["date"]].append(row)
        for date in sorted(rows_by_date):
            date_rows = rows_by_date[date]
            objects: list[str] = []
            citations: set[int] = set()
            source_rows: list[str] = []
            for row in date_rows:
                objects.extend(row["objects"])
                citations.update(row["citationNumbers"])
                source_rows.extend(row["sourceRows"])
            unique_objects = list(OrderedDict.fromkeys([item for item in objects if item]))
            dates.append(
                {
                    "date": date,
                    "targetCount": len(date_rows),
                    "objects": unique_objects[:8],
                    "citationNumbers": sorted(citations),
                    "sourceRows": source_rows,
                }
            )
        total_attack_rows += len(rows)
        region_name_uk = rows[0]["regionNameUk"]
        regions.append(
            {
                "regionId": f"attack_total_{iso.lower().replace('-', '_')}",
                "regionNameUk": region_name_uk,
                "regionNameEn": region_name_en,
                "iso": iso,
                "lat": lat,
                "lng": lng,
                "attackCount": len(rows),
                "targetCount": len(rows),
                "dates": dates,
            }
        )

    regions.sort(key=lambda item: (-item["attackCount"], item["regionNameEn"]))
    return {
        "schemaVersion": 1,
        "datasetId": "region_attack_totals_all_wikipedia",
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "sourceId": "source_wikipedia_probable_ukrainian_attacks_ru_by",
        "sourceUrl": source_url,
        "metric": "attack_rows_by_region_from_combined_wikipedia_list",
        "metricDescriptionUk": "Кількість рядків атак за регіоном з єдиної зведеної Wikipedia-сторінки; включає атаки по містах регіону, відбиті атаки ППО та рядки без підтвердженого влучання.",
        "metricDescriptionEn": "Number of attack rows by region from a single consolidated Wikipedia list; includes attacks on regional cities, intercepted air-defense attacks, and rows without a confirmed hit.",
        "regionCount": len(regions),
        "attackDayCount": sum(len(region["dates"]) for region in regions),
        "targetRowCount": total_attack_rows,
        "skippedRowsWithoutRussianRegion": len(skipped),
        "skippedRows": skipped[:200],
        "regions": regions,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-url", default=SOURCE_URL)
    parser.add_argument("--output", default="data/final/region_attack_totals_2026.json")
    parser.add_argument("--asset-output", default="app/src/main/assets/region_attack_totals_2026.json")
    parser.add_argument("--basemap", default="app/src/main/assets/basemap.js")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    payload = build(args.source_url, repo_root / args.basemap)
    encoded = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    for target in (repo_root / args.output, repo_root / args.asset_output):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(encoded, encoding="utf-8")
    print(json.dumps({
        "regionCount": payload["regionCount"],
        "attackDayCount": payload["attackDayCount"],
        "targetRowCount": payload["targetRowCount"],
        "skippedRowsWithoutRussianRegion": payload["skippedRowsWithoutRussianRegion"],
        "sourceUrl": payload["sourceUrl"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
