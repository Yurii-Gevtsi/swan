#!/usr/bin/env python3
"""Scatter piled-up approximate events and normalize radiusKm.

Most events carry area-level coordinates (oblast centre, sea centre), so
hundreds of markers stack on the exact same point and the map grouping (which
keys on rounded lat/lng) collapses them into a single crowded circle.

This step:
  1. Deterministically scatters events that share identical coordinates with
     several siblings (hash of the event id -> stable offset inside the area,
     so repeated pipeline runs never move a marker).
  2. Shrinks oversized radiusKm values: the radius should express local
     uncertainty of one event, not the whole oblast/sea.

Aggregate "operation total" events stay centred (they represent the whole
area by design) and keep a large radius.

Runs inside finalize-data.ps1 BEFORE map-group building, so groups form
around the scattered coordinates.
"""

import argparse
import hashlib
import json
import math
from collections import Counter
from pathlib import Path

from geo_ukraine import in_ukraine

# Precisions eligible for scattering (area-level, no exact object coords).
SCATTER_PRECISIONS = {"MARITIME_REGIONAL", "REGION_LEVEL", "CITY_OR_REGION_ANCHOR"}
MIN_PILE = 3  # scatter only when >=3 events share the exact same point

# Hand-tuned scatter ellipses (half-extent in km, lat x lng) for known water
# centres - keeps ships on water instead of drifting onto the coastline.
WATER_ELLIPSES = {
    (43.50, 34.00): (90, 200),   # Black Sea centre
    (46.00, 37.00): (40, 60),    # Sea of Azov
    (56.00, 19.00): (90, 120),   # Baltic Sea
    (55.00, 6.00): (90, 120),    # North Sea
    (44.70, 37.80): (18, 25),    # Novorossiysk anchorage
    (44.72, 37.77): (18, 25),    # Novorossiysk (alt centre)
}
DEFAULT_MARITIME_ELLIPSE = (40, 60)
DEFAULT_LAND_ELLIPSE = (30, 40)

# New radius rule (km): a single event's uncertainty, not the whole region.
RADIUS_SCATTERED_MARITIME = 30
RADIUS_SCATTERED_LAND = 35
RADIUS_UNIQUE_CAP = 40
RADIUS_MIN = 20
RADIUS_AGGREGATE_CAP = 150


def is_aggregate(event):
    event_id = str(event.get("id") or "")
    return "_operation_total" in event_id or "_group_" in event_id


def stable_unit_pair(event_id):
    """Two deterministic floats in [0,1) derived from the event id."""
    digest = hashlib.md5(event_id.encode("utf-8")).digest()
    a = int.from_bytes(digest[0:8], "big") / 2**64
    b = int.from_bytes(digest[8:16], "big") / 2**64
    return a, b


def scatter(event, lat_km, lng_km):
    """Move the event inside its area; never across the border into Ukraine.

    A Russian target whose generalized coordinate sits near the border can be
    pushed onto Ukrainian territory by a full-size offset, which would both
    misplace it and trip the Russia-only rule. Shrink the offset until the
    point stays on the correct side, and keep the original if it never does.
    """
    a, b = stable_unit_pair(str(event["id"]))
    angle = a * 2 * math.pi
    lat = float(event["lat"])
    lng = float(event["lng"])
    origin_in_ua = in_ukraine(lat, lng)

    for shrink in (1.0, 0.6, 0.35, 0.15):
        dist = math.sqrt(b) * shrink
        d_lat_km = math.sin(angle) * dist * lat_km
        d_lng_km = math.cos(angle) * dist * lng_km
        new_lat = round(lat + d_lat_km / 111.32, 4)
        new_lng = round(lng + d_lng_km / (111.32 * max(0.2, math.cos(math.radians(lat)))), 4)
        if in_ukraine(new_lat, new_lng) == origin_in_ua:
            event["lat"] = new_lat
            event["lng"] = new_lng
            return True
    return False  # kept at the original point


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--app-output", type=Path, required=True)
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    events = payload["events"]

    piles = Counter((round(float(e["lat"]), 4), round(float(e["lng"]), 4)) for e in events)
    scattered = radius_shrunk = kept_aggregates = border_held = 0

    for event in events:
        original_radius = int(event.get("radiusKm") or 0)
        key = (round(float(event["lat"]), 4), round(float(event["lng"]), 4))
        precision = str(event.get("precision") or "")

        if is_aggregate(event):
            new_radius = min(original_radius or RADIUS_AGGREGATE_CAP, RADIUS_AGGREGATE_CAP)
            kept_aggregates += 1
        elif piles[key] >= MIN_PILE and precision in SCATTER_PRECISIONS:
            if precision == "MARITIME_REGIONAL":
                lat_km, lng_km = WATER_ELLIPSES.get(key, DEFAULT_MARITIME_ELLIPSE)
                new_radius = RADIUS_SCATTERED_MARITIME
            else:
                lat_km, lng_km = DEFAULT_LAND_ELLIPSE
                new_radius = RADIUS_SCATTERED_LAND
            if scatter(event, lat_km, lng_km):
                scattered += 1
            else:
                border_held += 1
        else:
            new_radius = min(original_radius or RADIUS_MIN, RADIUS_UNIQUE_CAP)
            new_radius = max(new_radius, RADIUS_MIN)

        if new_radius != original_radius:
            event["radiusKm"] = new_radius
            radius_shrunk += 1

    serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    args.app_output.write_text(serialized, encoding="utf-8")

    remaining = Counter((e["lat"], e["lng"]) for e in events)
    worst = remaining.most_common(1)[0][1] if remaining else 0
    print(f"Scattered {scattered} piled events; adjusted radius on {radius_shrunk}; "
          f"aggregates kept centred: {kept_aggregates}; kept at origin to respect the "
          f"UA border: {border_held}; worst remaining pile: {worst}")


if __name__ == "__main__":
    main()
