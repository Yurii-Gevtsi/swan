"""Point-in-polygon test against Ukraine's internationally recognized borders.

Used to keep the map honest in two places:
  - scatter-approximate-events.py : never scatter a Russian target across the
    border into Ukraine
  - enforce-russia-only.py        : drop land targets located on temporarily
    occupied Ukrainian territory (Crimea, parts of Donetsk/Luhansk/
    Zaporizhzhia/Kherson oblasts)

Geometry comes from the bundled Natural Earth admin-0 "UA view" dataset, where
Crimea belongs to Ukraine. Always geographic, never name-based: places such as
"Krymsk" or LPDS "Krymska" are in Russia's Krasnodar Krai.
"""

import json
from functools import lru_cache
from pathlib import Path

UKRAINE_GEOJSON = Path(__file__).resolve().parent / "basemap_src" / "ne_10m_admin_0_ukr.geojson"


@lru_cache(maxsize=1)
def ukraine_polygons():
    data = json.loads(UKRAINE_GEOJSON.read_text(encoding="utf-8"))
    feature = next(f for f in data["features"] if f["properties"].get("ADM0_A3_UA") == "UKR")
    geom = feature["geometry"]
    polygons = [geom["coordinates"]] if geom["type"] == "Polygon" else geom["coordinates"]
    # tuples so the result stays hashable/cacheable and cheap to reuse
    return tuple(tuple(tuple(tuple(pt) for pt in ring) for ring in poly) for poly in polygons)


def _point_in_ring(x, y, ring):
    inside = False
    n = len(ring)
    j = n - 1
    for i in range(n):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-15) + xi):
            inside = not inside
        j = i
    return inside


def in_ukraine(lat, lng):
    """True when the point lies inside Ukraine's recognized land borders."""
    for poly in ukraine_polygons():
        if _point_in_ring(lng, lat, poly[0]):
            if not any(_point_in_ring(lng, lat, hole) for hole in poly[1:]):
                return True
    return False
