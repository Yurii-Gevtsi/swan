#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import json
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
EVENTS_PATH = REPO_ROOT / "data" / "final" / "osint_events.json"
OUTPUT_JSON = REPO_ROOT / "data" / "final" / "fuel_facilities_registry.json"
OUTPUT_MD = REPO_ROOT / "data" / "final" / "fuel_facilities_registry.md"

GENERIC_EXACT = {
    "",
    "oil base",
    "oil refinery",
    "railway tanks with pmm",
    "composition of pmm",
    "composition of petroleum products",
    "oil port infrastructure",
    "tanker, oil depot",
    "oil terminal, three tankers",
    "russia's fuel crisis: queues and limits at fuel stations",
    "~25% drop in russian gasoline output",
}

GENERIC_PREFIXES = (
    "russian federation,",
)

EXCLUDE_PATTERNS = (
    r"\btanker\b",
    r"\bdelta harmony\b",
    r"\bdelta supreme\b",
    r"\bfreud\b",
    r"\bmatilda\b",
    r"\bthree tankers\b",
)

CANONICAL_RULES: list[tuple[re.Pattern[str], str, str]] = [
    (re.compile(r"^petersburg oil terminal(?: jsc)?$", re.I), "Petersburg Oil Terminal", "Петербурзький нафтовий термінал"),
    (re.compile(r"^nevsky mazut plant$", re.I), "Nevsky Mazut Plant", "завод «Невський мазут»"),
    (re.compile(r"^refinery \"gazprom neftekhim salavat\"$|^salavat refinery$", re.I), "Gazprom Neftekhim Salavat Refinery", "НПЗ «Газпром нефтехим Салават»"),
    (re.compile(r"^novoshakhty refinery$|^novoshakhty oil products plant$", re.I), "Novoshakhty Oil Products Plant", "Новошахтинський завод нафтопродуктів"),
    (re.compile(r"^tuapsyn oil refinery$", re.I), "Tuapse Oil Refinery", "Туапсинський НПЗ"),
    (re.compile(r"^tuapse port oil terminal$", re.I), "Tuapse Port Oil Terminal", "Туапсинський портовий нафтовий термінал"),
    (re.compile(r"^refinery \"slovyansk eco\"$", re.I), "Slovyansk Eco Refinery", "НПЗ «Слов'янськ Еко»"),
    (re.compile(r"^refinery \"slavnafta-yanos\"$", re.I), "Slavneft-YANOS Refinery", "НПЗ «Славнефть-ЯНОС»"),
    (re.compile(r"^jsc \"syzran oil refinery\"$|^syzran oil refinery \(samara oblast\)$", re.I), "Syzran Oil Refinery", "Сизранський НПЗ"),
    (re.compile(r"^afip refinery$", re.I), "Afip Oil Refinery", "Афіпський НПЗ"),
    (re.compile(r"^ilya refinery$", re.I), "Ilsky Oil Refinery", "Ільський НПЗ"),
    (re.compile(r"^volgograd oil refinery$", re.I), "Volgograd Oil Refinery", "Волгоградський НПЗ"),
    (re.compile(r"^ryazan oil refinery$", re.I), "Ryazan Oil Refinery", "Рязанський НПЗ"),
    (re.compile(r"^moscow refinery$", re.I), "Moscow Oil Refinery", "Московський НПЗ"),
    (re.compile(r"^saratov refinery$", re.I), "Saratov Oil Refinery", "Саратовський НПЗ"),
    (re.compile(r"^perm oil refinery$", re.I), "Perm Oil Refinery", "Пермський НПЗ"),
    (re.compile(r"^tyumen refinery$", re.I), "Tyumen Oil Refinery", "Тюменський НПЗ"),
    (re.compile(r"^kuibyshevsky refinery$", re.I), "Kuibyshev Oil Refinery", "Куйбишевський НПЗ"),
    (re.compile(r"^novokuibyshevsky refinery$", re.I), "Novokuibyshev Oil Refinery", "Новокуйбишевський НПЗ"),
    (re.compile(r"^kstovsky refinery$", re.I), "Kstovo Oil Refinery", "Кстовський НПЗ"),
    (re.compile(r"^refinery \"kirishinefteorgsintez\"$", re.I), "Kirishinefteorgsintez Refinery", "НПЗ «Киришинефтеоргсинтез»"),
    (re.compile(r"^refinery \"lukoil-ukhtaneftepiererabotka\"$", re.I), "Lukoil-Ukhtaneftepiererabotka Refinery", "НПЗ «Лукойл-Ухтанефтепереработка»"),
    (re.compile(r"^refinery \"orsknaftoorgsintez\"$", re.I), "Orsknefteorgsintez Refinery", "НПЗ «Орскнефтеоргсинтез»"),
    (re.compile(r"^refinery \"first plant\"$", re.I), "First Plant Refinery", "НПЗ «Первый завод»"),
    (re.compile(r"^sea\s*terminal \"tamanneftegaz\"$", re.I), "Tamanneftegaz Sea Terminal", "Морський термінал «Таманьнефтегаз»"),
    (re.compile(r"^gas terminal of the port \"temryuk\"$", re.I), "Temryuk Port Gas Terminal", "Газовий термінал порту «Темрюк»"),
    (re.compile(r"^oil terminal \"sheskharis\"$", re.I), "Sheskharis Oil Terminal", "Нафтовий термінал «Шесхаріс»"),
    (re.compile(r"^oil terminal \"hrushova\"$", re.I), "Hrushova Oil Terminal", "Нафтовий термінал «Грушева»"),
    (re.compile(r"^oil terminal \"kurgannefteprodukt\"$", re.I), "Kurgannefteprodukt Oil Terminal", "Нафтовий термінал «Курганнефтепродукт»"),
    (re.compile(r"^oil depot \"tikhoretsk-nafta\"$", re.I), "Tikhoretsk-Nafta Oil Depot", "Нафтобаза «Тихорєцк-Нафта»"),
    (re.compile(r"^oil depot \"oskolneftesnab\"$", re.I), "Oskolneftesnab Oil Depot", "Нафтобаза «Осколнефтеснаб»"),
    (re.compile(r"^oil depot \"lukoil - yugnefteprodukt\"$|^oil depot \"yugnefteprodukt\"$", re.I), "Yugnefteprodukt Oil Depot", "Нафтобаза «Югнефтепродукт»"),
    (re.compile(r"^rosrezerva \"temp\" oil depot$", re.I), "Rosrezerv Temp Oil Depot", "Нафтобаза «Темп» Росрезерву"),
    (re.compile(r"^oil base of the rosrezerva combine \"krystal\"$", re.I), "Rosrezerv Krystal Oil Depot", "Нафтобаза комбінату «Кристал» Росрезерву"),
    (re.compile(r"^the oil base of the \"atlas\" plant of rosrezerv$", re.I), "Rosrezerv Atlas Oil Depot", "Нафтобаза комбінату «Атлас» Росрезерву"),
    (re.compile(r"^oil depot \"donterminal\"$", re.I), "DonTerminal Oil Depot", "Нафтобаза «DonTerminal»"),
    (re.compile(r"^oil depot \"port\"$", re.I), "Port Oil Depot", "Нафтобаза «Порт»"),
    (re.compile(r"^oil depot no\. 3$", re.I), "Oil Depot No. 3", "Нафтобаза №3"),
    (re.compile(r"^oil depot no\. 4$", re.I), "Oil Depot No. 4", "Нафтобаза №4"),
    (re.compile(r"^oil depot \"tvernefteprodukt\"$", re.I), "Tvernefteprodukt Oil Depot", "Нафтобаза «Твернефтепродукт»"),
    (re.compile(r"^ojsc \"poltava oil base\"$", re.I), "Poltava Oil Base", "Полтавська нафтобаза"),
    (re.compile(r"^\"agroproduct\" llc oil depot$", re.I), "Agroproduct Oil Depot", "Нафтобаза ТОВ «Агропродукт»"),
    (re.compile(r"^nps \"yaroslavl-3\"$", re.I), "Yaroslavl-3 Pumping Station", "НПС «Ярославль-3»"),
    (re.compile(r"^\"pskovnefteprodukt\" llc oil depot$", re.I), "Pskovnefteprodukt Oil Depot", "Нафтобаза «Псковнефтепродукт»"),
    (re.compile(r"^\"khokholska\" oil depot$", re.I), "Khokholska Oil Depot", "Нафтобаза «Хохольська»"),
    (re.compile(r"^oil depot \"pyenzanefteprodukt\"$", re.I), "Penzanefteprodukt Oil Depot", "Нафтобаза «Пензанефтепродукт»"),
    (re.compile(r"^\"zhutovska\" oil depot$", re.I), "Zhutovska Oil Depot", "Нафтобаза «Жутовська»"),
    (re.compile(r"^\"gerkon plus\" oil depot$", re.I), "Gerkon Plus Oil Depot", "Нафтобаза «Геркон Плюс»"),
    (re.compile(r"^main gas pipeline \"srto - torzhok\"$", re.I), "SRTO-Torzhok Main Gas Pipeline", "Магістральний газопровід «СРТО - Торжок»"),
    (re.compile(r"^affected \"gazpromneft-onpz\"$", re.I), "Gazpromneft-ONPZ Refinery", "НПЗ «Газпромнефть-ОНПЗ»"),
    (re.compile(r"^jsc \"taneco\" oil refinery$", re.I), "TANECO Oil Refinery", "НПЗ «ТАНЕКО»"),
    (re.compile(r"^oil refinery \"taif-nk\"$", re.I), "TAIF-NK Oil Refinery", "НПЗ «ТАИФ-НК»"),
    (re.compile(r"^oil refinery \"albashneft\"$", re.I), "Albashneft Oil Refinery", "НПЗ «Албашнефть»"),
    (re.compile(r"^ufa oil refinery complex$", re.I), "Ufa Oil Refinery Complex", "Уфимський нафтопереробний комплекс"),
    (re.compile(r"^\"bashneft-ufaneftekhim\" unpz$", re.I), "Bashneft-Ufaneftekhim Refinery", "УНПЗ «Башнефть-Уфанефтехим»"),
    (re.compile(r"^\"bashneft-novoil\" refinery$", re.I), "Bashneft-Novoil Refinery", "НПЗ «Башнефть-Новойл»"),
    (re.compile(r"^\"nizhnyokamsknaftokhim\"$", re.I), "Nizhnekamskneftekhim Fuel Complex", "Паливний комплекс «Нижньокамськнафтохім»"),
]


@dataclass
class FacilityAggregate:
    canonical_name_en: str
    canonical_name_uk: str
    facility_type: str
    aliases_en: set[str] = field(default_factory=set)
    aliases_uk: set[str] = field(default_factory=set)
    region_ids: set[str] = field(default_factory=set)
    event_ids: list[str] = field(default_factory=list)
    dates: list[str] = field(default_factory=list)


def normalize_space(value: str) -> str:
    value = unicodedata.normalize("NFKC", value or "")
    value = value.replace("\u200b", "").replace("\xa0", " ")
    value = re.sub(r"\s+", " ", value).strip()
    return value


def is_excluded(name: str) -> bool:
    lower = normalize_space(name).lower()
    if lower in GENERIC_EXACT:
        return True
    if any(lower.startswith(prefix) for prefix in GENERIC_PREFIXES):
        return True
    return any(re.search(pattern, lower) for pattern in EXCLUDE_PATTERNS)


def infer_type(name: str) -> str:
    lower = name.lower()
    if "pipeline" in lower:
        return "pipeline"
    if "pumping station" in lower or lower.startswith("nps "):
        return "pumping_station"
    if "gas terminal" in lower:
        return "gas_terminal"
    if "terminal" in lower:
        return "terminal"
    if "depot" in lower or "oil base" in lower:
        return "depot"
    if "refinery" in lower or "npz" in lower:
        return "refinery"
    if "fuel complex" in lower or "oil refinery complex" in lower:
        return "complex"
    return "fuel_facility"


def canonicalize(event: dict[str, Any]) -> tuple[str, str]:
    title_en = normalize_space(event.get("titleEn") or "")
    title_uk = normalize_space(event.get("titleUk") or "")
    for pattern, canonical_en, canonical_uk in CANONICAL_RULES:
        if pattern.match(title_en):
            return canonical_en, canonical_uk
    return title_en, title_uk


def load_events() -> list[dict[str, Any]]:
    payload = json.loads(EVENTS_PATH.read_text(encoding="utf-8"))
    return payload.get("events") or []


def build_registry(events: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    aggregates: dict[str, FacilityAggregate] = {}
    excluded: list[dict[str, Any]] = []

    for event in events:
        if event.get("category") != "FUEL_SUPPLY_DISRUPTION":
            continue
        title_en = normalize_space(event.get("titleEn") or "")
        title_uk = normalize_space(event.get("titleUk") or "")
        if is_excluded(title_en):
            excluded.append({
                "eventId": event.get("id"),
                "date": event.get("date"),
                "titleEn": title_en,
                "titleUk": title_uk,
                "reason": "generic_or_non_stationary_name",
            })
            continue

        canonical_en, canonical_uk = canonicalize(event)
        key = canonical_en.lower()
        aggregate = aggregates.get(key)
        if aggregate is None:
            aggregate = FacilityAggregate(
                canonical_name_en=canonical_en,
                canonical_name_uk=canonical_uk or title_uk,
                facility_type=infer_type(canonical_en),
            )
            aggregates[key] = aggregate

        if title_en:
            aggregate.aliases_en.add(title_en)
        if title_uk:
            aggregate.aliases_uk.add(title_uk)
        if event.get("approximateLocationLabelEn"):
            aggregate.aliases_en.add(normalize_space(event["approximateLocationLabelEn"]))
        if event.get("approximateLocationLabelUk"):
            aggregate.aliases_uk.add(normalize_space(event["approximateLocationLabelUk"]))
        if event.get("regionId"):
            aggregate.region_ids.add(event["regionId"])
        if event.get("id"):
            aggregate.event_ids.append(event["id"])
        if event.get("date"):
            aggregate.dates.append(event["date"])

    records: list[dict[str, Any]] = []
    for item in aggregates.values():
        dates = sorted(set(item.dates))
        records.append({
            "canonicalNameEn": item.canonical_name_en,
            "canonicalNameUk": item.canonical_name_uk,
            "facilityType": item.facility_type,
            "eventCount": len(item.event_ids),
            "firstDate": dates[0] if dates else None,
            "lastDate": dates[-1] if dates else None,
            "regionIds": sorted(item.region_ids),
            "aliasesEn": sorted(a for a in item.aliases_en if a and a != item.canonical_name_en),
            "aliasesUk": sorted(a for a in item.aliases_uk if a and a != item.canonical_name_uk),
            "eventIds": sorted(item.event_ids),
        })

    records.sort(key=lambda x: (x["facilityType"], x["canonicalNameEn"].lower()))
    excluded.sort(key=lambda x: ((x["titleEn"] or "").lower(), x["date"] or ""))
    return records, excluded


def build_markdown(records: list[dict[str, Any]], excluded: list[dict[str, Any]]) -> str:
    counts = Counter(record["facilityType"] for record in records)
    lines = [
        "# Fuel Facilities Registry",
        "",
        f"Unique facilities: {len(records)}",
        f"Excluded generic/non-stationary fuel entries: {len(excluded)}",
        "",
        "## By Type",
        "",
    ]
    for facility_type, count in sorted(counts.items()):
        lines.append(f"- {facility_type}: {count}")
    lines.extend(["", "## Facilities", ""])
    for record in records:
        lines.append(
            f"- {record['canonicalNameEn']} | {record['canonicalNameUk']} | "
            f"{record['facilityType']} | events={record['eventCount']} | "
            f"{record['firstDate']} -> {record['lastDate']}"
        )
    lines.extend(["", "## Excluded Generic Entries", ""])
    for item in excluded:
        lines.append(f"- {item['date']} | {item['titleEn']} | {item['reason']}")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    events = load_events()
    records, excluded = build_registry(events)
    payload = {
        "schemaVersion": 1,
        "generatedFrom": str(EVENTS_PATH.relative_to(REPO_ROOT)).replace("\\", "/"),
        "generatedAt": json.loads(EVENTS_PATH.read_text(encoding="utf-8")).get("generatedAt"),
        "recordCount": len(records),
        "excludedCount": len(excluded),
        "records": records,
        "excluded": excluded,
    }
    OUTPUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    OUTPUT_MD.write_text(build_markdown(records, excluded), encoding="utf-8")
    print(json.dumps({
        "recordCount": len(records),
        "excludedCount": len(excluded),
        "outputJson": str(OUTPUT_JSON),
        "outputMd": str(OUTPUT_MD),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
