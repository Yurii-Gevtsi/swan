#!/usr/bin/env python3
"""Repair a small set of legacy records with corrupted text fields."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = REPO_ROOT / "data" / "final" / "osint_events.json"
DEFAULT_APP_OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "osint_events.json"

OVERRIDES: dict[str, dict[str, str]] = {
    "event_20250212_grau_kotluban_followup_001": {
        "titleUk": "Арсенал ГРАУ біля Котлубані",
        "titleEn": "GRAU arsenal near Kotluban",
        "approximateLocationLabelUk": "район Котлубані, Волгоградська область",
        "approximateLocationLabelEn": "Kotluban area, Volgograd Oblast",
        "summaryUk": "Повідомлялося про повторний інцидент на арсеналі біля Котлубані; через слабку джерельну базу запис потребує ручної перевірки.",
        "summaryEn": "A repeat incident at the arsenal near Kotluban was reported; due to weak source coverage, the record needs manual verification.",
    },
    "event_20250601_airfield_dyagilevo_spiderweb_001": {
        "titleUk": "Військовий аеродром \"Дягілєво\"",
        "titleEn": "Military airfield \"Dyagilevo\"",
        "approximateLocationLabelUk": "Дягілєво, Рязанська область",
        "approximateLocationLabelEn": "Dyagilevo, Ryazan Oblast",
        "summaryUk": "Аеродром Дягілєво був серед цілей операції 1 червня 2025 року; масштаб пошкоджень слід звіряти з джерелами.",
        "summaryEn": "Dyagilevo airfield was listed among the targets of the 1 June 2025 operation; the extent of damage should be cross-checked against the source set.",
    },
    "event_20250601_airfield_ivanovo_damage_001": {
        "titleUk": "Військовий аеродром \"Іваново-Північний\"",
        "titleEn": "Military airfield \"Ivanovo-Severny\"",
        "approximateLocationLabelUk": "Іваново, Івановська область",
        "approximateLocationLabelEn": "Ivanovo, Ivanovo Oblast",
        "summaryUk": "Повідомлялося про ураження аеродрому Іваново-Північний у межах операції 1 червня 2025 року; окремі джерела пов'язують епізод із літаками ДРЛВ А-50.",
        "summaryEn": "Reports indicated a strike on Ivanovo-Severny airfield as part of the 1 June 2025 operation; some sources link the episode to A-50 AEW&C aircraft.",
    },
    "event_20241119_grau_karachev_followup_001": {
        "titleUk": "Арсенал ГРАУ біля Карачева",
        "titleEn": "GRAU arsenal near Karachev",
        "approximateLocationLabelUk": "район Карачева, Брянська область",
        "approximateLocationLabelEn": "Karachev area, Bryansk Oblast",
        "summaryUk": "Повідомлялося про черговий інцидент на арсеналі біля Карачева; запис збережено як повторне ураження і потребує ручної звірки.",
        "summaryEn": "A further incident at the arsenal near Karachev was reported; the record is retained as a repeat strike and needs manual cross-checking.",
    },
    "event_20241020_defense_industry_dzerzhinsk_explosives_001": {
        "titleUk": "Підприємство з виробництва вибухівки у Дзержинську",
        "titleEn": "Explosives production facility in Dzerzhinsk",
        "approximateLocationLabelUk": "Дзержинськ, Нижньогородська область",
        "approximateLocationLabelEn": "Dzerzhinsk, Nizhny Novgorod Oblast",
        "summaryUk": "Повідомлялося про удар по підприємству військово-промислового профілю у Дзержинську, пов'язаному з виробництвом вибухових речовин.",
        "summaryEn": "Reports indicated a strike on a defence-industrial facility in Dzerzhinsk associated with explosives production.",
    },
    "event_20241009_grau_karachev_arsenal_001": {
        "titleUk": "Арсенал ГРАУ біля Карачева",
        "titleEn": "GRAU arsenal near Karachev",
        "approximateLocationLabelUk": "район Карачева, Брянська область",
        "approximateLocationLabelEn": "Karachev area, Bryansk Oblast",
        "summaryUk": "Повідомлялося про ураження арсеналу ГРАУ біля Карачева.",
        "summaryEn": "Reports indicated a strike on the GRAU arsenal near Karachev.",
    },
    "event_20240929_grau_kotluban_arsenal_001": {
        "titleUk": "Арсенал ГРАУ біля Котлубані",
        "titleEn": "GRAU arsenal near Kotluban",
        "approximateLocationLabelUk": "район Котлубані, Волгоградська область",
        "approximateLocationLabelEn": "Kotluban area, Volgograd Oblast",
        "summaryUk": "Повідомлялося про ураження арсеналу боєприпасів у районі Котлубані.",
        "summaryEn": "Reports indicated a strike on the ammunition arsenal in the Kotluban area.",
    },
    "event_20240921_ammo_tikhoretsk_001": {
        "titleUk": "Склад боєприпасів біля Тихорецька",
        "titleEn": "Ammunition depot near Tikhoretsk",
        "approximateLocationLabelUk": "Тихорецьк, Краснодарський край",
        "approximateLocationLabelEn": "Tikhoretsk, Krasnodar Krai",
        "summaryUk": "Повідомлялося про ураження складу боєприпасів у районі Тихорецька.",
        "summaryEn": "Reports indicated a strike on an ammunition depot in the Tikhoretsk area.",
    },
    "event_20240918_grau_toropets_arsenal_001": {
        "titleUk": "Арсенал ГРАУ біля Торопця",
        "titleEn": "GRAU arsenal near Toropets",
        "approximateLocationLabelUk": "Торопець, Тверська область",
        "approximateLocationLabelEn": "Toropets, Tver Oblast",
        "summaryUk": "Повідомлялося про удар по великому арсеналу ГРАУ біля Торопця.",
        "summaryEn": "Reports indicated a strike on a major GRAU arsenal near Toropets.",
    },
    "event_20240814_airfield_savasleyka_attack_001": {
        "titleUk": "Військовий аеродром \"Саваслейка\"",
        "titleEn": "Military airfield \"Savasleyka\"",
        "approximateLocationLabelUk": "район Саваслейки, Нижньогородська область",
        "approximateLocationLabelEn": "Savasleyka area, Nizhny Novgorod Oblast",
        "summaryUk": "Повідомлялося про масовану атаку на район аеродрому Саваслейка; наслідки для об'єктів бази потребують ручної перевірки.",
        "summaryEn": "Reports indicated a massed attack on the Savasleyka airfield area; the consequences for facilities at the base require manual verification.",
    },
    "event_20240803_airfield_morozovsk_ammo_depot_001": {
        "titleUk": "Склад боєприпасів на аеродромі \"Морозовськ\"",
        "titleEn": "Ammunition depot at Morozovsk airfield",
        "approximateLocationLabelUk": "Морозовськ, Ростовська область",
        "approximateLocationLabelEn": "Morozovsk, Rostov Oblast",
        "summaryUk": "Повідомлялося про ураження складу боєприпасів біля аеродрому Морозовськ.",
        "summaryEn": "Reports indicated a strike on an ammunition depot near Morozovsk airfield.",
    },
    "event_20240608_airfield_akhtubinsk_su57_damage_001": {
        "titleUk": "Військовий аеродром \"Ахтубінськ\"",
        "titleEn": "Military airfield \"Akhtubinsk\"",
        "approximateLocationLabelUk": "Ахтубінськ, Астраханська область",
        "approximateLocationLabelEn": "Akhtubinsk, Astrakhan Oblast",
        "summaryUk": "Повідомлялося про пошкодження щонайменше одного Су-57 на аеродромі Ахтубінськ.",
        "summaryEn": "Reports indicated damage to at least one Su-57 at Akhtubinsk airfield.",
    },
    "event_20240608_airfield_mozdok_attempt_001": {
        "titleUk": "Військовий аеродром \"Моздок\"",
        "titleEn": "Military airfield \"Mozdok\"",
        "approximateLocationLabelUk": "Моздок, Північна Осетія - Аланія",
        "approximateLocationLabelEn": "Mozdok, North Ossetia-Alania",
        "summaryUk": "Повідомлялося про спробу атаки на військовий аеродром Моздок.",
        "summaryEn": "Reports indicated an attempted strike on Mozdok military airfield.",
    },
    "event_20240427_airfield_kushchevskaya_attack_001": {
        "titleUk": "Військовий аеродром \"Кущевська\"",
        "titleEn": "Military airfield \"Kushchevskaya\"",
        "approximateLocationLabelUk": "Кущевська, Краснодарський край",
        "approximateLocationLabelEn": "Kushchevskaya, Krasnodar Krai",
        "summaryUk": "Повідомлялося про атаку на аеродром Кущевська.",
        "summaryEn": "Reports indicated a strike on Kushchevskaya airfield.",
    },
    "event_20240405_airfield_morozovsk_attack_001": {
        "titleUk": "Військовий аеродром \"Морозовськ\"",
        "titleEn": "Military airfield \"Morozovsk\"",
        "approximateLocationLabelUk": "Морозовськ, Ростовська область",
        "approximateLocationLabelEn": "Morozovsk, Rostov Oblast",
        "summaryUk": "Повідомлялося про атаку на військовий аеродром Морозовськ.",
        "summaryEn": "Reports indicated a strike on Morozovsk military airfield.",
    },
    "event_20240320_airfield_engels_attack_001": {
        "titleUk": "Військовий аеродром \"Енгельс\"",
        "titleEn": "Military airfield \"Engels\"",
        "approximateLocationLabelUk": "Енгельс, Саратовська область",
        "approximateLocationLabelEn": "Engels, Saratov Oblast",
        "summaryUk": "Повідомлялося про атаку на військовий аеродром Енгельс.",
        "summaryEn": "Reports indicated a strike on Engels military airfield.",
    },
    "event_20230830_airfield_pskov_il76_damage_001": {
        "titleUk": "Військовий аеродром \"Псков\"",
        "titleEn": "Military airfield \"Pskov\"",
        "approximateLocationLabelUk": "Псков, Псковська область",
        "approximateLocationLabelEn": "Pskov, Pskov Oblast",
        "summaryUk": "Повідомлялося про пошкодження літаків Іл-76 на аеродромі Псков.",
        "summaryEn": "Reports indicated damage to Il-76 aircraft at Pskov airfield.",
    },
    "event_20230819_airfield_soltsy_tu22_loss_001": {
        "titleUk": "Військовий аеродром \"Сольці\"",
        "titleEn": "Military airfield \"Soltsy\"",
        "approximateLocationLabelUk": "Сольці, Новгородська область",
        "approximateLocationLabelEn": "Soltsy, Novgorod Oblast",
        "summaryUk": "Повідомлялося про знищення щонайменше одного Ту-22М3 на аеродромі Сольці.",
        "summaryEn": "Reports indicated the destruction of at least one Tu-22M3 at Soltsy airfield.",
    },
    "event_20221226_airfield_engels_followup_001": {
        "titleUk": "Військовий аеродром \"Енгельс\"",
        "titleEn": "Military airfield \"Engels\"",
        "approximateLocationLabelUk": "Енгельс, Саратовська область",
        "approximateLocationLabelEn": "Engels, Saratov Oblast",
        "summaryUk": "Повідомлялося про повторний удар по аеродрому Енгельс.",
        "summaryEn": "Reports indicated a follow-up strike on Engels airfield.",
    },
    "event_20221206_airfield_kursk_khalino_fuel_fire_001": {
        "titleUk": "Військовий аеродром \"Халіно\"",
        "titleEn": "Military airfield \"Khalino\"",
        "approximateLocationLabelUk": "Курськ, Курська область",
        "approximateLocationLabelEn": "Kursk, Kursk Oblast",
        "summaryUk": "Повідомлялося про пожежу резервуара з пальним, пов'язану з районом аеродрому Халіно.",
        "summaryEn": "Reports indicated a fuel tank fire associated with the Khalino airfield area.",
    },
    "event_20221205_airfield_dyagilevo_damage_001": {
        "titleUk": "Військовий аеродром \"Дягілєво\"",
        "titleEn": "Military airfield \"Dyagilevo\"",
        "approximateLocationLabelUk": "Дягілєво, Рязанська область",
        "approximateLocationLabelEn": "Dyagilevo, Ryazan Oblast",
        "summaryUk": "Повідомлялося про пошкодження на аеродромі Дягілєво.",
        "summaryEn": "Reports indicated damage at Dyagilevo airfield.",
    },
    "event_20221205_airfield_engels_damage_001": {
        "titleUk": "Військовий аеродром \"Енгельс\"",
        "titleEn": "Military airfield \"Engels\"",
        "approximateLocationLabelUk": "Енгельс, Саратовська область",
        "approximateLocationLabelEn": "Engels, Saratov Oblast",
        "summaryUk": "Повідомлялося про пошкодження на аеродромі Енгельс.",
        "summaryEn": "Reports indicated damage at Engels airfield.",
    },
    "event_20220225_airfield_millerovo_damage_001": {
        "titleUk": "Військовий аеродром \"Міллерово\"",
        "titleEn": "Military airfield \"Millerovo\"",
        "approximateLocationLabelUk": "Міллерово, Ростовська область",
        "approximateLocationLabelEn": "Millerovo, Rostov Oblast",
        "summaryUk": "Повідомлялося про пошкодження на аеродромі Міллерово.",
        "summaryEn": "Reports indicated damage at Millerovo airfield.",
    },
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--app-output", type=Path, default=DEFAULT_APP_OUTPUT)
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    changed = 0
    for event in payload.get("events", []):
        override = OVERRIDES.get(str(event.get("id") or ""))
        if not override:
            continue
        for key, value in override.items():
            if event.get(key) != value:
                event[key] = value
                changed += 1

    serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    args.app_output.parent.mkdir(parents=True, exist_ok=True)
    args.app_output.write_text(serialized, encoding="utf-8")
    print(f"Repaired text fields: {changed}")


if __name__ == "__main__":
    main()
