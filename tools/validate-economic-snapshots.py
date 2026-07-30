#!/usr/bin/env python3
"""Validate the two regional economic map layers before publishing.

Checks app/src/main/assets/fuel_shortage_regions_2026.json and
regional_budget_stress_2026.json: region count, unique ISO codes, required
fields, value ranges. Exit code 0 = OK, 1 = problems found.
"""

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets"
EXPECTED_REGIONS = 83

errors = []


def check(condition, message):
    if not condition:
        errors.append(message)


def load(name):
    path = ASSETS / name
    if not path.is_file():
        errors.append(f"{name}: file not found at {path}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        errors.append(f"{name}: invalid JSON - {exc}")
        return None


def check_common(name, snapshot):
    regions = snapshot.get("regions") or []
    check(snapshot.get("schemaVersion") == 1, f"{name}: schemaVersion must be 1")
    check(len(regions) == EXPECTED_REGIONS, f"{name}: expected {EXPECTED_REGIONS} regions, got {len(regions)}")
    check(snapshot.get("regionCount") == len(regions), f"{name}: regionCount != len(regions)")
    check(bool(snapshot.get("generatedAt")), f"{name}: generatedAt is empty")
    check(bool(snapshot.get("asOf")), f"{name}: asOf is empty")
    isos = [r.get("iso") for r in regions]
    check(len(set(isos)) == len(isos), f"{name}: duplicate ISO codes")
    for r in regions:
        iso = r.get("iso", "?")
        for field in ("regionId", "regionNameUk", "regionNameEn", "iso"):
            check(bool(r.get(field)), f"{name}/{iso}: missing {field}")
        check(isinstance(r.get("lat"), (int, float)) and isinstance(r.get("lng"), (int, float)),
              f"{name}/{iso}: lat/lng must be numbers")
    return regions


fuel = load("fuel_shortage_regions_2026.json")
if fuel is not None:
    for r in check_common("fuel", fuel):
        iso = r.get("iso", "?")
        check(isinstance(r.get("severity"), int) and 0 <= r["severity"] <= 4,
              f"fuel/{iso}: severity must be int 0..4")
        for field in ("statusUk", "statusEn", "coverageUk", "coverageEn", "restrictionUk", "restrictionEn"):
            check(isinstance(r.get(field), str) and r[field].strip() != "",
                  f"fuel/{iso}: {field} must be a non-empty string")
        for field in ("gasolineLimitLiters", "dieselLimitLiters"):
            value = r.get(field)
            check(value is None or isinstance(value, (int, float)), f"fuel/{iso}: {field} must be null or number")
        check(bool(r.get("sourceUrl")), f"fuel/{iso}: sourceUrl is empty")

budget = load("regional_budget_stress_2026.json")
if budget is not None:
    for r in check_common("budget", budget):
        iso = r.get("iso", "?")
        check(r.get("statusEn") in ("Deficit", "Surplus"), f"budget/{iso}: statusEn must be Deficit or Surplus")
        check(isinstance(r.get("deficitPercentRevenue"), (int, float)),
              f"budget/{iso}: deficitPercentRevenue must be a number")
        for field in ("deficitMillionRub", "ownRevenueBillionRub", "publicDebtJuneBillionRub",
                      "publicDebtJanuaryBillionRub", "debtChangePercent"):
            value = r.get(field)
            check(value is None or isinstance(value, (int, float)), f"budget/{iso}: {field} must be null or number")
        for field in ("descriptionUk", "descriptionEn"):
            check(isinstance(r.get(field), str) and r[field].strip() != "",
                  f"budget/{iso}: {field} must be a non-empty string")
        check(bool(r.get("budgetSourceUrl")), f"budget/{iso}: budgetSourceUrl is empty")

if errors:
    print(f"VALIDATION FAILED: {len(errors)} problem(s)")
    for line in errors:
        print(f"  ! {line}")
    sys.exit(1)

print("Both economic snapshots are valid (83 regions each).")
