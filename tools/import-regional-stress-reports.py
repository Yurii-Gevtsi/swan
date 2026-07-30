#!/usr/bin/env python3
"""Build offline regional fuel and fiscal map layers from the supplied XLSX reports."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import re
import time
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from xml.etree import ElementTree as ET


XML_NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

# The reports contain the same 83 regions in this order. These ISO 3166-2
# values match the bundled Natural Earth admin-1 geometry.
REGION_ISOS = [
    "RU-ALT", "RU-AMU", "RU-ARK", "RU-AST", "RU-BEL", "RU-BRY", "RU-VLA", "RU-VGG", "RU-VLG", "RU-VOR",
    "RU-YEV", "RU-ZAB", "RU-IVA", "RU-IRK", "RU-KB", "RU-KGD", "RU-KLU", "RU-KAM", "RU-KC", "RU-KEM",
    "RU-KIR", "RU-KOS", "RU-KDA", "RU-KYA", "RU-KGN", "RU-KRS", "RU-LEN", "RU-LIP", "RU-MAG", "RU-MOW",
    "RU-MOS", "RU-MUR", "RU-NEN", "RU-NIZ", "RU-NGR", "RU-NVS", "RU-OMS", "RU-ORE", "RU-ORL", "RU-PNZ",
    "RU-PER", "RU-PRI", "RU-PSK", "RU-AD", "RU-AL", "RU-BA", "RU-BU", "RU-DA", "RU-IN", "RU-KL",
    "RU-KR", "RU-KO", "RU-ME", "RU-MO", "RU-SE", "RU-SA", "RU-TA", "RU-TY", "RU-KK", "RU-ROS",
    "RU-RYA", "RU-SAM", "RU-SPE", "RU-SAR", "RU-SAK", "RU-SVE", "RU-SMO", "RU-STA", "RU-TAM", "RU-TVE",
    "RU-TOM", "RU-TUL", "RU-TYU", "RU-UD", "RU-ULY", "RU-KHA", "RU-KHM", "RU-CHE", "RU-CE", "RU-CU",
    "RU-CHU", "RU-YAN", "RU-YAR",
]
FALLBACK_CENTERS = {"RU-KAM": (56.15, 159.0, "Kamchatka Krai"), "RU-CHU": (65.63, 171.0, "Chukotka Autonomous Okrug")}
DEFAULT_TRANSLATION_CACHE = Path(__file__).resolve().with_name("regional-report-translation-cache.json")
TRANSLATION_OVERRIDES = {
    "Зафіксовані обмеження": "Reported restrictions",
    "Конкретних обмежень у зведенні не наведено": "No specific restriction reported",
    "11.07–01.08, черговість": "11 Jul-1 Aug, alternating schedule",
    "Автомобіль, лише бак": "Vehicle, fuel tank only",
    "Кілька мереж": "Multiple fuel-station networks",
    "Кілька районів; до 01.09": "Several districts, through 1 Sep",
    "Мережа Gazpromneft; лише бак": "Gazpromneft network, fuel tank only",
    "Мережі й тара": "Fuel-station networks and containers",
    "Мережі та черговість": "Fuel-station networks and alternating schedule",
    "Немає конкретної норми у зведенні": "No specific limit reported",
    "Одна заправка, лише бак": "One fuel station, fuel tank only",
    "Окремі АЗС": "Selected fuel stations",
    "Окремі АЗС і години": "Selected fuel stations and service hours",
    "Окремі АЗС і черговість": "Selected fuel stations and alternating schedule",
    "Окремі АЗС; лише бак": "Selected fuel stations, fuel tank only",
    "Окремі міста й години": "Selected cities and service hours",
    "Регіон і черговість": "Region-wide alternating schedule",
    "Регіон/мережі": "Region-wide and selected fuel-station networks",
    "Регіон/окремі АЗС": "Region-wide and selected fuel stations",
    "Регіон; лише бак": "Region-wide, fuel tank only",
    "Регіон; лише бак і черговість": "Region-wide, fuel tank only and alternating schedule",
    "Регіональна черговість": "Region-wide alternating schedule",
    "Рекомендований ліміт і черговість": "Recommended cap and alternating schedule",
    "Траси й міста; лише бак": "Highways and cities, fuel tank only",
    "Фізособи; лише бак": "Private motorists, fuel tank only",
    "Черговість до 25.07": "Alternating schedule through 25 Jul",
}


def column_index(reference: str) -> int:
    result = 0
    for character in re.match(r"([A-Z]+)", reference).group(1):
        result = result * 26 + ord(character) - 64
    return result - 1


def read_xlsx_sheet(path: Path, sheet_path: str) -> list[list[str]]:
    with zipfile.ZipFile(path) as archive:
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            shared = ["".join(text.text or "" for text in item.iter(XML_NS + "t")) for item in root.findall(XML_NS + "si")]
        root = ET.fromstring(archive.read(sheet_path))
        rows = []
        for row in root.findall(".//" + XML_NS + "row"):
            values: list[str] = []
            for cell in row.findall(XML_NS + "c"):
                index = column_index(cell.attrib["r"])
                while len(values) <= index:
                    values.append("")
                value_node = cell.find(XML_NS + "v")
                value = "" if value_node is None else value_node.text or ""
                if cell.attrib.get("t") == "s" and value:
                    value = shared[int(value)]
                elif cell.attrib.get("t") == "inlineStr":
                    value = "".join(text.text or "" for text in cell.iter(XML_NS + "t"))
                values[index] = value
            rows.append(values)
        return rows


def number(value: str) -> float | None:
    return float(value) if value else None


def basemap_centers(path: Path) -> dict[str, tuple[float, float, str]]:
    text = path.read_text(encoding="utf-8")
    payload = json.loads(re.search(r"(?:const|var) BASEMAP = (.*);\s*$", text, re.DOTALL).group(1))
    centers = {}
    for area in payload["admin1"]:
        if not area["iso"].startswith("RU-"):
            continue
        points = [point for polygon in area["polygons"] for ring in polygon for point in ring]
        centers[area["iso"]] = (sum(point[1] for point in points) / len(points), sum(point[0] for point in points) / len(points), area["name"])
    return centers


def padded(row: list[str], columns: int) -> list[str]:
    return row + [""] * max(0, columns - len(row))


def base_region(name_uk: str, iso: str, centers: dict[str, tuple[float, float, str]]) -> dict[str, object]:
    lat, lng, name_en = centers.get(iso) or FALLBACK_CENTERS[iso]
    return {"regionId": "indicator_" + iso.lower().replace("-", "_"), "regionNameUk": name_uk, "regionNameEn": name_en, "iso": iso, "lat": round(lat, 5), "lng": round(lng, 5)}


def translate_ukrainian(value: str) -> str:
    query = urllib.parse.urlencode({"client": "gtx", "sl": "uk", "tl": "en", "dt": "t", "q": value})
    request = urllib.request.Request(
        f"https://translate.googleapis.com/translate_a/single?{query}",
        headers={"User-Agent": "Mozilla/5.0"},
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                payload = json.loads(response.read().decode("utf-8"))
            return "".join(part[0] for part in payload[0] if part and part[0]).strip()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(1 + attempt)
    raise RuntimeError("Unreachable")


def build_translations(values: set[str], cache_path: Path, offline: bool) -> dict[str, str]:
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    missing = sorted(value for value in values if value and value not in cache and value not in TRANSLATION_OVERRIDES)
    if missing and offline:
        raise RuntimeError("Missing cached English translations: " + ", ".join(missing[:3]))
    if missing:
        with ThreadPoolExecutor(max_workers=6) as executor:
            futures = {executor.submit(translate_ukrainian, value): value for value in missing}
            for future in as_completed(futures):
                value = futures[future]
                cache[value] = future.result()
    cache.update(TRANSLATION_OVERRIDES)
    cache_path.write_text(json.dumps(dict(sorted(cache.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return cache


def format_number(value: float | None) -> str:
    return "-" if value is None else f"{value:,.1f}"


def build_budget_descriptions(
    is_deficit: bool,
    deficit: float | None,
    deficit_percent: float,
    debt_june: float | None,
    debt_change: float | None,
) -> tuple[str, str]:
    debt_en = f" Public debt was {format_number(debt_june)} billion RUB" if debt_june is not None else ""
    debt_uk = f" Державний борг становив {format_number(debt_june)} млрд руб." if debt_june is not None else ""
    change_en = f" ({debt_change:+.1f}% since January)." if debt_change is not None else "."
    change_uk = f" ({debt_change:+.1f}% від січня)." if debt_change is not None else "."
    if is_deficit:
        return (
            f"Станом на 1 червня 2026 року бюджет мав дефіцит {format_number(deficit)} млн руб., або {deficit_percent:.1f}% власних доходів.{debt_uk}{change_uk}",
            f"As of 1 June 2026, the regional budget recorded a deficit of {format_number(deficit)} million RUB, equal to {deficit_percent:.1f}% of own revenue.{debt_en}{change_en}",
        )
    return (
        f"Станом на 1 червня 2026 року бюджет мав профіцит.{debt_uk}{change_uk}",
        f"As of 1 June 2026, the regional budget reported a surplus.{debt_en}{change_en}",
    )


def build_fuel(rows: list[list[str]], centers: dict[str, tuple[float, float, str]], translations: dict[str, str]) -> dict[str, object]:
    # Fuel data is on the report sheet after its title and update note.
    data_rows = [padded(row, 8) for row in rows[3:] if row and row[0]]
    if len(data_rows) != len(REGION_ISOS):
        raise RuntimeError(f"Expected {len(REGION_ISOS)} fuel rows, got {len(data_rows)}")
    regions = []
    for row, iso in zip(data_rows, REGION_ISOS, strict=True):
        severity = int(row[1])
        region = base_region(row[0], iso, centers)
        region.update({
            "severity": severity,
            "statusUk": row[2],
            "statusEn": translations[row[2]],
            "gasolineLimitLiters": number(row[3]),
            "dieselLimitLiters": number(row[4]),
            "coverageUk": row[5],
            "coverageEn": translations[row[5]],
            "restrictionUk": row[6],
            "restrictionEn": translations[row[6]],
            "sourceUrl": row[7],
        })
        regions.append(region)
    return {
        "schemaVersion": 1, "datasetId": "fuel_shortage_regions_russia_2026", "generatedAt": datetime.now(timezone.utc).isoformat(),
        "asOf": "2026-07-17", "sourceName": "Sravni fuel restrictions report", "metric": "fuel_restriction_severity_0_to_4",
        "metricDescriptionEn": "Reported fuel restriction severity, where 0 means no specific restriction was described in the report.",
        "regionCount": len(regions), "regions": regions,
    }


def build_budget(rows: list[list[str]], centers: dict[str, tuple[float, float, str]]) -> dict[str, object]:
    data_rows = [padded(row, 10) for row in rows[1:] if row and row[0]]
    if len(data_rows) != len(REGION_ISOS):
        raise RuntimeError(f"Expected {len(REGION_ISOS)} budget rows, got {len(data_rows)}")
    regions = []
    for row, iso in zip(data_rows, REGION_ISOS, strict=True):
        deficit = number(row[3])
        debt_june = number(row[6])
        debt_january = number(row[7])
        is_deficit = bool(deficit)
        region = base_region(row[0], iso, centers)
        deficit_percent = number(row[4]) or 0.0
        debt_change = ((debt_june / debt_january - 1) * 100) if debt_june is not None and debt_january not in (None, 0) else None
        description_uk, description_en = build_budget_descriptions(is_deficit, deficit, deficit_percent, debt_june, debt_change)
        region.update({
            "statusUk": row[2], "statusEn": "Deficit" if is_deficit else "Surplus",
            "deficitMillionRub": deficit, "deficitPercentRevenue": deficit_percent,
            "ownRevenueBillionRub": number(row[5]), "publicDebtJuneBillionRub": debt_june,
            "publicDebtJanuaryBillionRub": debt_january,
            "debtChangePercent": debt_change,
            "descriptionUk": description_uk, "descriptionEn": description_en,
            "budgetSourceUrl": row[8], "debtSourceUrl": row[9],
        })
        regions.append(region)
    return {
        "schemaVersion": 1, "datasetId": "regional_budgets_debt_russia_2026_ytd", "generatedAt": datetime.now(timezone.utc).isoformat(),
        "asOf": "2026-06-01", "sourceName": "iMinfin regional budget and public debt data", "metric": "budget_deficit_percent_of_own_revenue",
        "metricDescriptionEn": "Budget deficit as a percentage of regional own revenues, January-May 2026 actuals.",
        "regionCount": len(regions), "regions": regions,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fuel", type=Path, required=True)
    parser.add_argument("--budget", type=Path, required=True)
    parser.add_argument("--basemap", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--translation-cache", type=Path, default=DEFAULT_TRANSLATION_CACHE)
    parser.add_argument("--offline", action="store_true", help="Require all translations to be present in the cache.")
    args = parser.parse_args()
    centers = basemap_centers(args.basemap)
    missing = sorted(set(REGION_ISOS) - set(centers) - set(FALLBACK_CENTERS))
    if missing:
        raise RuntimeError("Missing map centres for: " + ", ".join(missing))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    fuel_rows = read_xlsx_sheet(args.fuel, "xl/worksheets/sheet1.xml")
    fuel_values = {
        padded_row[index]
        for row in fuel_rows[3:]
        if row and row[0]
        for padded_row in [padded(row, 8)]
        for index in (2, 5, 6)
        if padded_row[index]
    }
    translations = build_translations(fuel_values, args.translation_cache, args.offline)
    fuel = build_fuel(fuel_rows, centers, translations)
    budget = build_budget(read_xlsx_sheet(args.budget, "xl/worksheets/sheet2.xml"), centers)
    (args.output_dir / "fuel_shortage_regions_2026.json").write_text(json.dumps(fuel, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output_dir / "regional_budget_stress_2026.json").write_text(json.dumps(budget, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Fuel regions: {fuel['regionCount']}; budget regions: {budget['regionCount']}")


if __name__ == "__main__":
    main()
