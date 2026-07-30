#!/usr/bin/env python3
"""Repair geocoding artefacts in the bundled city-label dataset.

major_cities_ru.js / .json came from a geocoder and carry two defect classes:

  1. English labels that kept the geocoder's full record instead of the place
     name -- e.g. "Vladikavkaz Research Center of the Russian Academy of
     Sciences" or "Znamenka, Orlovsky District, Oryol Oblast".
  2. Ukrainian labels left in Latin script (or holding a different place's
     name), so the Ukrainian map showed Latin city captions.

Fixes are explicit, never transliterated blindly, and verified against the
current value before writing, so re-running is a no-op.
"""

import json
import re
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
JS_FILE = ASSETS / "major_cities_ru.js"
JSON_FILE = ASSETS / "major_cities_ru.json"

# geonameId -> corrected English label (place name only).
EN_FIXES = {
    "Ezhvinsky District": "Ezhva",
    "Komsomolsky, Republic of Mordovia": "Komsomolsky",
    "Novo-Peredelkino District": "Novo-Peredelkino",
    "Vostochnoye Degunino District": "Vostochnoye Degunino",
    "Znamenka, Orlovsky District, Oryol Oblast": "Znamenka",
    "Vladikavkaz Research Center of the Russian Academy of Sciences": "Vladikavkaz",
    "Sheksna, Sheksninsky District, Vologda Oblast": "Sheksna",
    "Rossosh, Repyovsky District, Voronezh Oblast": "Rossosh",
}

# Latin Ukrainian label -> proper Ukrainian Cyrillic, keyed by the English name
# so the pair stays consistent (some records had a different place in `uk`).
UK_FIXES = {
    "Ulan-Ude": "Улан-Уде",
    "Stary Malgobek": "Старий Малгобек",
    "Burul": "Бурул",
    "Troitskoe": "Троїцьке",
    "Ezhva": "Єжва",
    "Novo-Peredelkino": "Ново-Переделкіно",
    "Cheremushki": "Черемушки",
    "Narian-Mar": "Нар'ян-Мар",
    "Nes'": "Несь",
    "Kalachinsk": "Калачинськ",
    "Znamenskoye": "Знаменське",
    "Krasnaya Glinka": "Красна Глинка",
    "Znamenka": "Знаменка",
    "Bolkhov": "Болхов",
    "Zarechny": "Зарічний",
    "Nakhodka": "Находка",
    "Sasovo": "Сасово",
    "Vladikavkaz": "Владикавказ",
    "Mil'kovo": "Мільково",
}

CYRILLIC = re.compile("[А-Яа-яЇїІіЄєҐґЁё]")


def main():
    raw = JS_FILE.read_text(encoding="utf-8")
    prefix, payload = raw.split("=", 1)
    cities = json.loads(payload.strip().rstrip(";"))

    en_changed = uk_changed = 0
    for city in cities:
        english = city.get("en", "")
        if english in EN_FIXES:
            city["en"] = EN_FIXES[english]
            en_changed += 1
            english = city["en"]
        ukrainian = city.get("uk", "")
        if ukrainian and not CYRILLIC.search(ukrainian):
            replacement = UK_FIXES.get(english)
            if replacement:
                city["uk"] = replacement
                uk_changed += 1

    serialized = json.dumps(cities, ensure_ascii=False, separators=(",", ":"))
    JS_FILE.write_text(f"{prefix.rstrip()} = {serialized};\n", encoding="utf-8")

    if JSON_FILE.is_file():
        doc = json.loads(JSON_FILE.read_text(encoding="utf-8-sig"))
        doc["cities"] = cities
        JSON_FILE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    leftover_latin = [c for c in cities if c.get("uk") and not CYRILLIC.search(c["uk"])]
    leftover_long = [c for c in cities if len(c.get("en", "")) > 24 or "," in c.get("en", "")]
    print(f"English labels fixed: {en_changed}; Ukrainian labels fixed: {uk_changed}")
    print(f"Remaining Latin Ukrainian labels: {len(leftover_latin)}")
    for c in leftover_latin:
        print(f"   {c.get('en')!r} / {c.get('uk')!r}")
    print(f"Remaining long English labels: {len(leftover_long)}")
    for c in leftover_long:
        print(f"   {c.get('en')!r}")


if __name__ == "__main__":
    main()
