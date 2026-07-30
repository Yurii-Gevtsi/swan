#!/usr/bin/env python3
"""Register narrative special operations and keep bundled assets in sync."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path


SPECIAL_SOURCE_PREFIX = "source_wiki_special_events_"
SPECIAL_DATASET_ID = "special_events_2022_2025"

SOURCES = {
    "crimean_bridge_2022": "https://uk.wikipedia.org/wiki/%D0%92%D0%B8%D0%B1%D1%83%D1%85_%D0%BD%D0%B0_%D0%9A%D1%80%D0%B8%D0%BC%D1%81%D1%8C%D0%BA%D0%BE%D0%BC%D1%83_%D0%BC%D0%BE%D1%81%D1%82%D1%83_%282022%29",
    "crimean_bridge_2023": "https://uk.wikipedia.org/wiki/%D0%92%D0%B8%D0%B1%D1%83%D1%85_%D0%BD%D0%B0_%D0%9A%D1%80%D0%B8%D0%BC%D1%81%D1%8C%D0%BA%D0%BE%D0%BC%D1%83_%D0%BC%D0%BE%D1%81%D1%82%D1%83_%282023%29",
    "crimean_bridge_2025": "https://uk.wikipedia.org/wiki/%D0%92%D0%B8%D0%B1%D1%83%D1%85_%D0%BD%D0%B0_%D0%9A%D1%80%D0%B8%D0%BC%D1%81%D1%8C%D0%BA%D0%BE%D0%BC%D1%83_%D0%BC%D0%BE%D1%81%D1%82%D1%83_%282025%29",
    "a50_azov_2024": "https://uk.wikipedia.org/wiki/%D0%97%D0%BD%D0%B8%D1%89%D0%B5%D0%BD%D0%BD%D1%8F_%D0%90-50_%D0%BD%D0%B0%D0%B4_%D0%90%D0%B7%D0%BE%D0%B2%D1%81%D1%8C%D0%BA%D0%B8%D0%BC_%D0%BC%D0%BE%D1%80%D0%B5%D0%BC_%28%D1%81%D1%96%D1%87%D0%B5%D0%BD%D1%8C_2024%29",
    "russian_bridges_2025": "https://uk.wikipedia.org/wiki/%D0%9F%D1%96%D0%B4%D1%80%D0%B8%D0%B2%D0%B8_%D0%BC%D0%BE%D1%81%D1%82%D1%96%D0%B2_%D1%83_%D0%A0%D0%BE%D1%81%D1%96%D1%97_%28%D1%87%D0%B5%D1%80%D0%B2%D0%B5%D0%BD%D1%8C_2025%29",
    "operation_spiderweb_2025": "https://uk.wikipedia.org/wiki/%D0%9E%D0%BF%D0%B5%D1%80%D0%B0%D1%86%D1%96%D1%8F_%C2%AB%D0%9F%D0%B0%D0%B2%D1%83%D1%82%D0%B8%D0%BD%D0%B0%C2%BB",
    "moskva_cruiser_2022": "https://ru.wikipedia.org/wiki/%D0%9F%D0%BE%D1%82%D0%BE%D0%BF%D0%BB%D0%B5%D0%BD%D0%B8%D0%B5_%D0%BA%D1%80%D0%B5%D0%B9%D1%81%D0%B5%D1%80%D0%B0_%C2%AB%D0%9C%D0%BE%D1%81%D0%BA%D0%B2%D0%B0%C2%BB",
}

ADDITIONAL_OPERATION_SOURCES = {
    "operation_molochka": [
        "https://sbs-group.army/subdivision/usf_grouping/?period=monthly_7",
        "https://24tv.ua/sbs-prodovzhuyut-nishhiti-flot-voroga-yakiy-ulov-za-ostannyu_n3107252",
    ],
    "novorossiysk_submarine_2025": [
        "https://www.ukrinform.ua/rubric-ato/4069918-u-novorosijsku-morski-droni-sbu-vrazili-rosijskij-pidvodnij-coven-nosijkalibriv.html",
        "https://nv.ua/ukr/ukraine/events/sbu-vpershe-v-istoriji-vrazila-rosiyskiy-pidvodniy-choven-dronami-sea-baby-50568684.html",
    ],
    "omsk_refinery_deep_strike_2026": [
        "https://thepage.ua/ua/news/omskij-npz-atakuvali-ukrayinski-droni-6-lipnya-2026-sho-vidomo-foto",
        "https://fakty.com.ua/ua/svit/20260707-omskyj-npz-de-znahodytsya-odyn-iz-najbilshyh-naftopererobnyh-zavodiv-rosiyi/",
    ],
    "toropets_grau_arsenal_2024": [
        "https://ru.wikipedia.org/wiki/%D0%90%D1%82%D0%B0%D0%BA%D0%B0_%D1%81%D0%BA%D0%BB%D0%B0%D0%B4%D0%B0_%D0%B1%D0%BE%D0%B5%D0%BF%D1%80%D0%B8%D0%BF%D0%B0%D1%81%D0%BE%D0%B2_%D0%B2_%D0%A2%D0%BE%D1%80%D0%BE%D0%BF%D1%86%D0%B5",
        "https://news.liga.net/ua/war/news/polovyna-120-mm-min-i-10-kabiv-boiets-rozkryv-detali-udaru-po-arsenalu-hrau-v-2024-mu",
        "https://defence-ua.com/photo/udar_sbu_po_arsenalu_gru_u_toroptsi_buv_z_teritoriji_rf_kudi_spetspriznachentsi_pronesli_droni_osint_po_zirkah-392.html",
    ],
    "mordovia_container_radar_2024": [
        "https://lb.ua/society/2024/04/17/608871_up_gur_atakuvalo_rosiyskiy_rlk.html",
        "https://zaxid.net/droni_gur_atakuvali_rosiysku_rls_konteyneru_mordoviyi_n1583828",
    ],
}


def metric(label_uk: str, label_en: str, value_uk: str, value_en: str | None = None) -> dict:
    return {
        "labelUk": label_uk,
        "labelEn": label_en,
        "valueUk": value_uk,
        "valueEn": value_en or value_uk,
    }


def operation(
    *,
    id: str,
    title_uk: str,
    title_en: str,
    date_uk: str,
    date_en: str,
    target_uk: str,
    target_en: str,
    impact_uk: str,
    impact_en: str,
    source_urls: list[str],
    metrics: list[dict] | None = None,
    details_uk: list[str] | None = None,
    details_en: list[str] | None = None,
) -> dict:
    return {
        "id": id,
        "titleUk": title_uk,
        "titleEn": title_en,
        "dateLabelUk": date_uk,
        "dateLabelEn": date_en,
        "targetUk": target_uk,
        "targetEn": target_en,
        "impactUk": impact_uk,
        "impactEn": impact_en,
        "metrics": metrics or [],
        "detailsUk": details_uk or [],
        "detailsEn": details_en or [],
        "sourceUrls": source_urls,
    }


def event(
    *,
    id: str,
    title_uk: str,
    title_en: str,
    date: str,
    category: str,
    scope: str,
    theater: str,
    label_uk: str,
    label_en: str,
    lat: float,
    lng: float,
    radius_km: int,
    summary_uk: str,
    summary_en: str,
    source_key: str,
    region_id: str | None = None,
    maritime_area_id: str | None = None,
    tags: str = "",
    precision: str = "REGION_LEVEL",
    actor: str = "UKRAINIAN_DEFENSE_FORCES",
    verification_status: str = "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
) -> dict:
    source_id = SPECIAL_SOURCE_PREFIX + source_key
    return {
        "id": id,
        "status": "PUBLISHED",
        "titleEn": title_en,
        "titleUk": title_uk,
        "date": date,
        "datePrecision": "DAY",
        "category": category,
        "eventScope": scope,
        "theater": theater,
        "regionId": region_id,
        "federalDistrictId": None,
        "maritimeAreaId": maritime_area_id,
        "sanctionsJurisdictionId": None,
        "approximateLocationLabelEn": label_en,
        "approximateLocationLabelUk": label_uk,
        "lat": lat,
        "lng": lng,
        "radiusKm": radius_km,
        "precision": precision,
        "assetId": None,
        "actor": actor,
        "actorConfidence": "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
        "actorNote": "Narrative special operation imported from a curated source set; verify against the cited notes.",
        "verificationStatus": verification_status,
        "severity": "HIGH",
        "summaryEn": summary_en,
        "summaryUk": summary_uk,
        "impactTags": tags,
        "sources": source_id,
        "safetyNotes": "Approximate regional anchor for map display; this is not an exact operational coordinate.",
        "createdAt": "2026-07-18T00:00:00Z",
        "updatedAt": "2026-07-18T00:00:00Z",
    }


def build_events() -> tuple[list[dict], list[dict]]:
    events: list[dict] = []
    groups: list[dict] = []

    events.append(
        event(
            id="event_20220414_moskva_cruiser_sunk",
            title_uk="Потоплення ракетного крейсера «Москва»",
            title_en='Sinking of the missile cruiser "Moskva"',
            date="2022-04-14",
            category="MARITIME_ASSET_DISRUPTION",
            scope="MILITARY_ASSET",
            theater="BLACK_SEA",
            label_uk="Північно-західна частина Чорного моря",
            label_en="Northwestern Black Sea",
            lat=45.20,
            lng=30.90,
            radius_km=100,
            maritime_area_id="black_sea_general",
            summary_uk="Російський ракетний крейсер «Москва» був уражений українськими протикорабельними ракетами «Нептун» 13 квітня 2022 року та затонув 14 квітня під час буксирування.",
            summary_en='The Russian missile cruiser "Moskva" was hit by Ukrainian Neptune anti-ship missiles on 13 April 2022 and sank on 14 April while under tow.',
            source_key="moskva_cruiser_2022",
            tags="Moskva, missile cruiser, warship, sunk, Black Sea",
            precision="MARITIME_REGIONAL",
            actor="UKRAINIAN_NAVY",
            verification_status="OFFICIAL_CONFIRMED",
        )
    )

    bridge_events = [
        event(
            id="event_20221008_crimean_bridge_explosion",
            title_uk="Перше ураження Кримського мосту",
            title_en="First strike on the Crimean Bridge",
            date="2022-10-08",
            category="INFRASTRUCTURE_DISRUPTION",
            scope="PORT_INFRASTRUCTURE",
            theater="AZOV_SEA",
            label_uk="Кримський міст",
            label_en="Crimean Bridge",
            lat=45.3011,
            lng=36.5125,
            radius_km=20,
            maritime_area_id="kerch_strait_maritime_area",
            summary_uk="8 жовтня 2022 року вибух пошкодив автомобільні прольоти Кримського мосту; на залізничній частині загорівся потяг із паливом.",
            summary_en="On 8 October 2022, an explosion damaged road spans of the Crimean Bridge and a fuel train caught fire on the railway section.",
            source_key="crimean_bridge_2022",
            tags="Crimean Bridge, bridge strike, infrastructure",
            precision="MARITIME_REGIONAL",
        ),
        event(
            id="event_20230717_crimean_bridge_explosion",
            title_uk="Друге ураження Кримського мосту",
            title_en="Second strike on the Crimean Bridge",
            date="2023-07-17",
            category="INFRASTRUCTURE_DISRUPTION",
            scope="PORT_INFRASTRUCTURE",
            theater="AZOV_SEA",
            label_uk="Кримський міст",
            label_en="Crimean Bridge",
            lat=45.24562,
            lng=36.58829,
            radius_km=20,
            maritime_area_id="kerch_strait_maritime_area",
            summary_uk="17 липня 2023 року вибух пошкодив два прольоти Кримського мосту; один автодорожній проліт був зруйнований, інший пошкоджений.",
            summary_en="On 17 July 2023, an explosion damaged two spans of the Crimean Bridge; one road span was destroyed and another damaged.",
            source_key="crimean_bridge_2023",
            tags="Crimean Bridge, bridge strike, infrastructure",
            precision="MARITIME_REGIONAL",
        ),
        event(
            id="event_20250603_crimean_bridge_explosion",
            title_uk="Третє ураження Кримського мосту",
            title_en="Third strike on the Crimean Bridge",
            date="2025-06-03",
            category="INFRASTRUCTURE_DISRUPTION",
            scope="PORT_INFRASTRUCTURE",
            theater="AZOV_SEA",
            label_uk="Кримський міст",
            label_en="Crimean Bridge",
            lat=45.30,
            lng=36.51,
            radius_km=20,
            maritime_area_id="kerch_strait_maritime_area",
            summary_uk="3 червня 2025 року вибух підводного заряду сильно пошкодив опори Кримського мосту на рівні морського дна.",
            summary_en="On 3 June 2025, an underwater explosive severely damaged the Crimean Bridge supports at seabed level.",
            source_key="crimean_bridge_2025",
            tags="Crimean Bridge, bridge strike, infrastructure",
            precision="MARITIME_REGIONAL",
        ),
    ]
    events.extend(bridge_events)
    groups.append(
        {
            "id": "crimean_bridge_strikes",
            "titleUk": "Ураження Кримського мосту",
            "titleEn": "Crimean Bridge strikes",
            "eventCount": 3,
            "countUnit": "events",
            "events": [{"date": entry["date"], "eventId": entry["id"]} for entry in bridge_events],
            "sourceIds": [
                SPECIAL_SOURCE_PREFIX + "crimean_bridge_2022",
                SPECIAL_SOURCE_PREFIX + "crimean_bridge_2023",
                SPECIAL_SOURCE_PREFIX + "crimean_bridge_2025",
            ],
        }
    )

    a50_event = event(
        id="event_20240114_a50_il22_azov",
        title_uk="Ураження А-50У та Іл-22М над Азовським морем",
        title_en="A-50U and Il-22M hit over the Sea of Azov",
        date="2024-01-14",
        category="MILITARY_ASSET_DISRUPTION",
        scope="MILITARY_ASSET",
        theater="AZOV_SEA",
        label_uk="Азовське море",
        label_en="Sea of Azov",
        lat=46.25,
        lng=35.35,
        radius_km=120,
        maritime_area_id="azov_sea_general",
        summary_uk="Уражено два літаки: російський літак ДРЛВ А-50У було знищено, а повітряний командний пункт Іл-22М важко пошкоджено; він сів в Анапі й, за повідомленнями, відновленню не підлягав.",
        summary_en="Two aircraft were hit: a Russian A-50U was destroyed and the Il-22M airborne command post was heavily damaged; it landed at Anapa and was reported beyond repair.",
        source_key="a50_azov_2024",
        tags="A-50U, Il-22M, aircraft, Sea of Azov",
        precision="MARITIME_REGIONAL",
        actor="UKRAINIAN_MILITARY_INTELLIGENCE",
    )
    events.append(a50_event)
    groups.append(
        {
            "id": "a50_azov_aircraft_losses",
            "titleUk": "Ураження літаків над Азовським морем",
            "titleEn": "Aircraft hit over the Sea of Azov",
            "affectedAircraftCount": 2,
            "destroyedCount": 1,
            "damagedCount": 1,
            "aircraft": [
                {"type": "A-50U", "status": "destroyed"},
                {"type": "Il-22M", "status": "heavily_damaged"},
            ],
            "eventId": a50_event["id"],
            "sourceIds": [SPECIAL_SOURCE_PREFIX + "a50_azov_2024"],
        }
    )

    bridge_damage = [
        event(
            id="event_20250531_bryansk_vygonichi_bridge",
            title_uk="Підірвано автомобільний міст біля Вигончів",
            title_en="Road bridge destroyed near Vygonichi",
            date="2025-05-31",
            category="INFRASTRUCTURE_DISRUPTION",
            scope="PORT_INFRASTRUCTURE",
            theater="RUSSIA_INTERNAL",
            label_uk="Вигончі, Брянська область",
            label_en="Vygonichi, Bryansk Oblast",
            lat=52.96,
            lng=34.42,
            radius_km=35,
            region_id="ru_bryansk_oblast",
            summary_uk="У ніч з 31 травня на 1 червня 2025 року біля Вигончів обвалився автомобільний міст; потяг врізався в уламки та зійшов з рейок.",
            summary_en="On the night of 31 May to 1 June 2025, a road bridge near Vygonichi collapsed; a train hit the debris and derailed.",
            source_key="russian_bridges_2025",
            tags="bridge, Bryansk Oblast, rail disruption",
        ),
        event(
            id="event_20250601_kursk_zheleznogorsk_bridge",
            title_uk="Підірвано залізничний міст у Курській області",
            title_en="Rail bridge destroyed in Kursk Oblast",
            date="2025-06-01",
            category="INFRASTRUCTURE_DISRUPTION",
            scope="PORT_INFRASTRUCTURE",
            theater="RUSSIA_INTERNAL",
            label_uk="Залізногірський район, Курська область",
            label_en="Zheleznogorsk District, Kursk Oblast",
            lat=52.98,
            lng=36.05,
            radius_km=45,
            region_id="ru_kursk_oblast",
            summary_uk="У Залізногірському районі Курської області залізничний міст зруйнувався під час руху вантажного поїзда; були постраждалі.",
            summary_en="In Zheleznogorsk District of Kursk Oblast, a rail bridge collapsed as a freight train crossed it; injuries were reported.",
            source_key="russian_bridges_2025",
            tags="bridge, Kursk Oblast, rail disruption",
        ),
    ]
    events.extend(bridge_damage)
    groups.append(
        {
            "id": "russian_bridges_june_2025",
            "titleUk": "Знищені мости в Росії, червень 2025",
            "titleEn": "Bridges destroyed in Russia, June 2025",
            "destroyedBridgeCount": 2,
            "countUnit": "bridges",
            "events": [
                {"date": entry["date"], "eventId": entry["id"], "regionId": entry["regionId"]}
                for entry in bridge_damage
            ],
            "sourceIds": [SPECIAL_SOURCE_PREFIX + "russian_bridges_2025"],
        }
    )

    airfields = [
        ("bila", "Біла", "Belaya", "Іркутська область", "Irkutsk Oblast", 52.91, 103.55, "ru_irkutsk_oblast", "4 Ту-95МС; 6–7 Ту-22М3; разом 10–11 підтверджених літаків.", "4 Tu-95MS; 6-7 Tu-22M3; 10-11 confirmed aircraft in total."),
        ("olenya", "Оленья", "Olenya", "Мурманська область", "Murmansk Oblast", 68.1454, 33.4504, "ru_murmansk_oblast", "4 Ту-95МС; 1–2 Ту-22М3; 1 Ан-12; разом 6–7 підтверджених літаків.", "4 Tu-95MS; 1-2 Tu-22M3; 1 An-12; 6-7 confirmed aircraft in total."),
        ("ivanovo", "Іваново-Північний", "Ivanovo-Severny", "Івановська область", "Ivanovo Oblast", 56.95, 40.98, "ru_ivanovo_oblast", "2 А-50; розподіл підтверджених втрат за базою.", "2 A-50 aircraft; confirmed losses were allocated to this airfield."),
        ("dyagilevo", "Дягілєво", "Dyagilevo", "Рязанська область", "Ryazan Oblast", 54.65, 39.58, "ru_ryazan_oblast", "0–3 Ту-22М3; діапазон, наведений у розподілі підтверджених втрат.", "0-3 Tu-22M3; the range reported in the confirmed loss breakdown."),
        ("ukrainka", "Українка", "Ukrainka", "Амурська область", "Amur Oblast", 51.17, 128.45, "ru_amur_oblast", "Аеродром був серед цілей операції; окремий розподіл підтверджених літаків у джерелі не наведений.", "The airfield was among the targets, but the source did not provide a separate breakdown of confirmed aircraft losses."),
    ]

    spider_events = []
    for slug, name_uk, name_en, region_uk, region_en, lat, lng, region_id, detail_uk, detail_en in airfields:
        spider_events.append(
            event(
                id=f"event_20250601_spiderweb_{slug}",
                title_uk=f"Операція «Павутина»: аеродром {name_uk}",
                title_en=f"Operation Spiderweb: {name_en} airfield",
                date="2025-06-01",
                category="MILITARY_ASSET_DISRUPTION",
                scope="MILITARY_ASSET",
                theater="RUSSIA_INTERNAL",
                label_uk=f"{name_uk}, {region_uk}",
                label_en=f"{name_en}, {region_en}",
                lat=lat,
                lng=lng,
                radius_km=90,
                region_id=region_id,
                summary_uk=f"У межах операції «Павутина» аеродром {name_uk} був серед цілей. {detail_uk} Стаття окремо наводить заяву СБУ про 41 уражений літак загалом.",
                summary_en=f"As part of Operation Spiderweb, the {name_en} airfield was among the targets. {detail_en} The article separately records the SSU claim of 41 aircraft hit overall.",
                source_key="operation_spiderweb_2025",
                tags="Operation Spiderweb, airfield, aircraft",
                actor="SECURITY_SERVICE_OF_UKRAINE",
            )
        )
    events.extend(spider_events)
    spider_summary = event(
        id="event_20250601_operation_spiderweb_summary",
        title_uk="Операція «Павутина»: 5 аеродромів та авіаційні втрати",
        title_en="Operation Spiderweb: 5 airfields and aircraft losses",
        date="2025-06-01",
        category="MILITARY_ASSET_DISRUPTION",
        scope="MILITARY_ASSET",
        theater="RUSSIA_INTERNAL",
        label_uk="Росія, операція «Павутина»",
        label_en="Russia, Operation Spiderweb",
        lat=56.0,
        lng=70.0,
        radius_km=700,
        precision="REGION_LEVEL",
        summary_uk="Операція охопила 5 аеродромів: «Біла», «Дягілєво», «Іваново-Північний», «Оленья» та «Українка». Підтверджено 15–20 стратегічних бомбардувальників, 1 Ан-12 і 2 А-50; окремо заявлено 41 уражений літак.",
        summary_en="The operation covered 5 airfields: Belaya, Dyagilevo, Ivanovo-Severny, Olenya and Ukrainka. Confirmed losses include 15-20 strategic bombers, 1 An-12 and 2 A-50 aircraft; a total of 41 aircraft hit was claimed separately.",
        source_key="operation_spiderweb_2025",
        tags="Operation Spiderweb, 5 airfields, 15-20 bombers, 41 claimed aircraft",
        actor="SECURITY_SERVICE_OF_UKRAINE",
    )
    events.append(spider_summary)
    groups.append(
        {
            "id": "operation_spiderweb",
            "titleUk": "Операція «Павутина»",
            "titleEn": "Operation Spiderweb",
            "date": "2025-06-01",
            "airfieldCount": 5,
            "confirmedStrategicBombers": {"min": 15, "max": 20},
            "confirmedAircraft": {"strategicBombers": "15–20", "transport": 1, "a50": 2},
            "claimedAircraftCount": 41,
            "airfields": [
                {
                    "nameUk": item[1],
                    "nameEn": item[2],
                    "regionUk": item[3],
                    "regionEn": item[4],
                    "eventId": event_entry["id"],
                    "detailUk": item[8],
                }
                for item, event_entry in zip(airfields, spider_events)
            ],
            "summaryEventId": spider_summary["id"],
            "sourceIds": [SPECIAL_SOURCE_PREFIX + "operation_spiderweb_2025"],
        }
    )

    return events, groups


def build_operations() -> list[dict]:
    return [
        operation(
            id="operation_molochka",
            title_uk="Операція «МоЛоЧКа»",
            title_en="Operation MoLoCHKa",
            date_uk="6-17 липня 2026 року",
            date_en="6-17 July 2026",
            target_uk="Судна тіньового флоту РФ в Азовському та Чорному морях.",
            target_en="Russian shadow fleet vessels in the Sea of Azov and the Black Sea.",
            impact_uk="Уражено 159 суден: 117 в Азовському морі та 42 у Чорному морі. Лише 17 липня було додано ще 12 чорноморських суден.",
            impact_en="A total of 159 vessels were hit: 117 in the Sea of Azov and 42 in the Black Sea. Twelve more Black Sea vessels were added on 17 July alone.",
            metrics=[
                metric("Уражено суден", "Vessels hit", "159"),
                metric("Азовська фаза", "Sea of Azov phase", "117"),
                metric("Чорноморська фаза", "Black Sea phase", "42"),
            ],
            details_uk=[
                "Операція розвивалася двома фазами: спершу Азовське море, далі Чорне море.",
                "17 липня повідомлялося про 12 нових уражених суден: 9 суховантажів, танкер, танкер-газовоз і буксир.",
            ],
            details_en=[
                "The operation unfolded in two phases: first the Sea of Azov, then the Black Sea.",
                "On 17 July, 12 additional vessels were reported hit: 9 dry cargo ships, 1 tanker, 1 gas carrier tanker, and 1 tug.",
            ],
            source_urls=ADDITIONAL_OPERATION_SOURCES["operation_molochka"],
        ),
        operation(
            id="operation_spiderweb",
            title_uk="Операція «Павутина»",
            title_en="Operation Spiderweb",
            date_uk="1 червня 2025 року",
            date_en="1 June 2025",
            target_uk="П'ять російських аеродромів стратегічної авіації: «Біла», «Дягілєво», «Іваново-Північний», «Оленья» та «Українка».",
            target_en="Five Russian strategic aviation airfields: Belaya, Dyagilevo, Ivanovo-Severny, Olenya, and Ukrainka.",
            impact_uk="Підтверджено 15-20 стратегічних бомбардувальників, 1 Ан-12 та 2 літаки А-50. Окремо СБУ заявляла про 41 уражений літак загалом.",
            impact_en="Confirmed losses include 15-20 strategic bombers, 1 An-12, and 2 A-50 aircraft. The SSU separately claimed 41 aircraft hit in total.",
            metrics=[
                metric("Уражені аеродроми", "Targeted airfields", "5"),
                metric("Підтверджені стратегічні бомбардувальники", "Confirmed strategic bombers", "15-20"),
                metric("Ан-12", "An-12 transport aircraft", "1"),
                metric("А-50", "A-50 AEW&C aircraft", "2"),
                metric("Заявлено уражених літаків", "Claimed aircraft hit", "41"),
            ],
            details_uk=[
                "Біла: 4 Ту-95МС, 6-7 Ту-22М3.",
                "Оленья: 4 Ту-95МС, 1-2 Ту-22М3, 1 Ан-12.",
                "Іваново-Північний: 2 А-50.",
                "Дягілєво: 0-3 Ту-22М3.",
                "Українка: аеродром був серед цілей, але окремий розподіл не наведено.",
            ],
            details_en=[
                "Belaya: 4 Tu-95MS, 6-7 Tu-22M3.",
                "Olenya: 4 Tu-95MS, 1-2 Tu-22M3, 1 An-12.",
                "Ivanovo-Severny: 2 A-50.",
                "Dyagilevo: 0-3 Tu-22M3.",
                "Ukrainka: the airfield was among the targets, but no separate allocation was published.",
            ],
            source_urls=[SOURCES["operation_spiderweb_2025"]],
        ),
        operation(
            id="novorossiysk_submarine_2025",
            title_uk="Ураження підводного човна в Новоросійську",
            title_en="Strike on the submarine in Novorossiysk",
            date_uk="15 грудня 2025 року",
            date_en="15 December 2025",
            target_uk="Військово-морська база у порту Новоросійська, Краснодарський край.",
            target_en="The naval base in the port of Novorossiysk, Krasnodar Krai.",
            impact_uk="Підводні дрони «Sub Sea Baby» вперше уразили російський підводний човен проєкту 636.3 «Варшавянка» та завдали критичних пошкоджень носію ракет «Калібр» вартістю близько 400 млн доларів.",
            impact_en="Sub Sea Baby underwater drones reportedly struck a Russian Project 636.3 Kilo-class submarine for the first time, critically damaging a Kalibr missile carrier valued at about $400 million.",
            source_urls=ADDITIONAL_OPERATION_SOURCES["novorossiysk_submarine_2025"],
        ),
        operation(
            id="omsk_refinery_deep_strike_2026",
            title_uk="Спецоперація Deep Strike по Омському НПЗ",
            title_en="Deep Strike operation against the Omsk refinery",
            date_uk="6 липня 2026 року",
            date_en="6 July 2026",
            target_uk="Омський нафтопереробний завод у Сибіру, приблизно за 2500-3000 км від українського кордону.",
            target_en="The Omsk oil refinery in Siberia, roughly 2,500-3,000 km from the Ukrainian border.",
            impact_uk="Ударні дрони вразили установку первинної переробки нафти ЕЛОУ-АВТ-11. Найбільший НПЗ РФ призупинив роботу та припинив продаж пального на біржі.",
            impact_en="Strike drones hit the ELOU-AVT-11 primary oil processing unit. Russia's largest refinery reportedly halted operations and suspended fuel sales on the exchange.",
            source_urls=ADDITIONAL_OPERATION_SOURCES["omsk_refinery_deep_strike_2026"],
        ),
        operation(
            id="toropets_grau_arsenal_2024",
            title_uk="Знищення арсеналів ГРАУ в Торопці",
            title_en="Destruction of the GRAU arsenals in Toropets",
            date_uk="18 вересня 2024 року",
            date_en="18 September 2024",
            target_uk="107-й арсенал Головного ракетно-артилерійського управління поблизу Торопця, Тверська область.",
            target_en="The 107th Main Missile and Artillery Directorate arsenal near Toropets, Tver Oblast.",
            impact_uk="Ураження спричинило масштабну детонацію на складах із приблизно 30 000 тоннами боєприпасів. Повідомлялося про знищення ракет «Іскандер» і «Точка-У», керованих авіабомб та артилерійських снарядів.",
            impact_en="The strike triggered a massive detonation at depots storing roughly 30,000 tons of ammunition. Reports described the destruction of Iskander and Tochka-U missiles, guided aerial bombs, and artillery shells.",
            source_urls=ADDITIONAL_OPERATION_SOURCES["toropets_grau_arsenal_2024"],
        ),
        operation(
            id="mordovia_container_radar_2024",
            title_uk="Знищення стратегічної РЛС у Мордовії",
            title_en="Destruction of the strategic radar in Mordovia",
            date_uk="17 квітня 2024 року",
            date_en="17 April 2024",
            target_uk="590-й окремий радіотехнічний вузол у Ковилкіному, Мордовія, більш ніж за 600 км від кордону.",
            target_en="The 590th independent radio-technical node in Kovylkino, Mordovia, more than 600 km from the border.",
            impact_uk="Удар безпілотниками ГУР призвів до ураження загоризонтного радіолокатора 29Б6 «Контейнер» із радіусом виявлення до 3000 км, який входив до системи попередження про повітряно-космічний напад.",
            impact_en="A strike attributed to Ukrainian military intelligence reportedly hit the 29B6 Container over-the-horizon radar, which had a detection range of up to 3,000 km and formed part of Russia's aerospace early-warning network.",
            source_urls=ADDITIONAL_OPERATION_SOURCES["mordovia_container_radar_2024"],
        ),
        operation(
            id="crimean_bridge_strikes",
            title_uk="Ураження Кримського мосту",
            title_en="Crimean Bridge strikes",
            date_uk="8 жовтня 2022, 17 липня 2023, 3 червня 2025",
            date_en="8 October 2022, 17 July 2023, 3 June 2025",
            target_uk="Кримський міст через Керченську протоку.",
            target_en="The Crimean Bridge across the Kerch Strait.",
            impact_uk="Зафіксовано три окремі ураження: два з пошкодженням дорожніх прольотів та одне з важким підривом опор на рівні морського дна.",
            impact_en="Three separate strikes were recorded: two that damaged road spans and one that severely damaged the supports at seabed level.",
            metrics=[metric("Кількість уражень", "Strike count", "3")],
            source_urls=[
                SOURCES["crimean_bridge_2022"],
                SOURCES["crimean_bridge_2023"],
                SOURCES["crimean_bridge_2025"],
            ],
        ),
        operation(
            id="a50_azov_aircraft_losses",
            title_uk="Ураження А-50У та Іл-22М над Азовським морем",
            title_en="A-50U and Il-22M hit over the Sea of Azov",
            date_uk="14 січня 2024 року",
            date_en="14 January 2024",
            target_uk="Російські літаки ДРЛВ А-50У та повітряний командний пункт Іл-22М над Азовським морем.",
            target_en="A Russian A-50U AEW&C aircraft and an Il-22M airborne command post over the Sea of Azov.",
            impact_uk="Один літак було знищено, другий - важко пошкоджено. Цей епізод став одним із найпомітніших ударів по російській системі повітряного управління.",
            impact_en="One aircraft was destroyed and the other was heavily damaged. The episode became one of the most consequential strikes against Russia's airborne command-and-control layer.",
            metrics=[
                metric("Уражено літаків", "Aircraft hit", "2"),
                metric("Знищено", "Destroyed", "1"),
                metric("Важко пошкоджено", "Heavily damaged", "1"),
            ],
            source_urls=[SOURCES["a50_azov_2024"]],
        ),
        operation(
            id="russian_bridges_june_2025",
            title_uk="Знищені мости в Росії, червень 2025",
            title_en="Bridges destroyed in Russia, June 2025",
            date_uk="31 травня - 1 червня 2025 року",
            date_en="31 May - 1 June 2025",
            target_uk="Автомобільний міст біля Вигончів у Брянській області та залізничний міст у Курській області.",
            target_en="A road bridge near Vygonichi in Bryansk Oblast and a rail bridge in Kursk Oblast.",
            impact_uk="Обидва епізоди призвели до руйнування мостів і порушення руху поїздів, включно з аваріями рухомого складу.",
            impact_en="Both episodes led to bridge destruction and railway disruption, including derailments and service interruptions.",
            metrics=[metric("Зруйновано мостів", "Destroyed bridges", "2")],
            source_urls=[SOURCES["russian_bridges_2025"]],
        ),
    ]


def build_payload() -> tuple[list[dict], dict]:
    events, groups = build_events()
    operations = build_operations()

    all_source_urls = list(dict.fromkeys(
        list(SOURCES.values()) +
        [url for entry in operations for url in entry["sourceUrls"]]
    ))

    payload = {
        "schemaVersion": 2,
        "datasetId": SPECIAL_DATASET_ID,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "sourceType": "narrative_event_register",
        "descriptionUk": "Окремий реєстр спеціальних операцій і наративних подій з чистими українськими та англійськими полями для застосунку.",
        "descriptionEn": "A standalone register of special operations and narrative events with clean Ukrainian and English fields for the app.",
        "sourceUrls": all_source_urls,
        "groups": groups,
        "operations": operations,
        "appEventIds": [entry["id"] for entry in events],
        "eventCount": len(events),
        "operationCount": len(operations),
    }
    return events, payload


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    manual_path = root / "data" / "final" / "osint_events_manual_additions.json"
    special_path = root / "data" / "final" / "special_events_2022_2025.json"
    special_asset_path = root / "app" / "src" / "main" / "assets" / "special_events_2022_2025.json"

    manual = (
        json.loads(manual_path.read_text(encoding="utf-8"))
        if manual_path.exists()
        else {
            "schemaVersion": 1,
            "sourceFile": "manual_additions",
            "importTarget": "Room EventEntity",
            "skippedCount": 0,
            "events": [],
        }
    )

    kept = [
        entry
        for entry in manual.get("events", [])
        if not any(source.strip().startswith(SPECIAL_SOURCE_PREFIX) for source in str(entry.get("sources", "")).split(","))
    ]

    new_events, special = build_payload()
    manual["generatedAt"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    manual["sourceFile"] = "manual_additions_and_special_narrative_events"
    manual["events"] = kept + new_events
    manual["recordCount"] = len(manual["events"])

    manual_path.write_text(json.dumps(manual, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    encoded_special = json.dumps(special, ensure_ascii=False, indent=2) + "\n"
    special_path.write_text(encoded_special, encoding="utf-8")
    special_asset_path.parent.mkdir(parents=True, exist_ok=True)
    special_asset_path.write_text(encoded_special, encoding="utf-8")

    print(
        json.dumps(
            {
                "specialEventCount": len(new_events),
                "groupCount": len(special["groups"]),
                "operationCount": len(special["operations"]),
                "manualEventCount": len(manual["events"]),
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
