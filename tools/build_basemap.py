# Builds app/src/main/assets/basemap.js from Natural Earth GeoJSON sources.
#
# Sources (download into the directory passed as --src, default ./basemap_src):
#   ne_10m_admin_0_ukr.geojson  <- https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_countries_ukr.geojson
#   ne_10m_admin_1.geojson      <- https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson
#
# The "_ukr" point-of-view variant keeps Crimea as part of Ukraine.
# Output: a single JS file declaring `var BASEMAP = {countries: [...], admin1: [...]}`
# clipped to the app's map bounds and simplified for zoom levels 5-6.

import argparse
import json
import math
import os

# Covers Iberia through the Russian Far East while keeping the offline map focused
# on the Europe--Russia operational area.
BBOX = (-12.0, 33.0, 180.0, 75.0)  # lon_min, lat_min, lon_max, lat_max
ADMIN1_COUNTRIES = {"Ukraine", "Russia", "Belarus"}
# NE attributes occupied Crimea/Sevastopol to Russia in admin-1; their ISO codes stay UA-*.
UA_ISO_OVERRIDES = {"UA-43", "UA-40"}
COUNTRY_TOL = 0.035
ADMIN1_TOL = 0.045
MIN_RING_AREA = 0.02  # square degrees; drops islet noise
PRECISION = 2


def dp_simplify(points, tol):
    """Iterative Douglas-Peucker on [lon, lat] pairs."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        seg_len2 = dx * dx + dy * dy
        max_d2, max_i = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            if seg_len2 == 0:
                d2 = (px - ax) ** 2 + (py - ay) ** 2
            else:
                t = ((px - ax) * dx + (py - ay) * dy) / seg_len2
                t = max(0.0, min(1.0, t))
                d2 = (px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2
            if d2 > max_d2:
                max_d2, max_i = d2, i
        if max_d2 > tol * tol:
            keep[max_i] = True
            stack.append((a, max_i))
            stack.append((max_i, b))
    return [p for p, k in zip(points, keep) if k]


def clip_ring(ring, bbox):
    """Sutherland-Hodgman clip of one ring against the bbox rectangle."""
    lon_min, lat_min, lon_max, lat_max = bbox
    edges = [
        (lambda p: p[0] >= lon_min, 0, lon_min),
        (lambda p: p[0] <= lon_max, 0, lon_max),
        (lambda p: p[1] >= lat_min, 1, lat_min),
        (lambda p: p[1] <= lat_max, 1, lat_max),
    ]
    pts = ring
    for inside, axis, value in edges:
        if not pts:
            return []
        out = []
        prev = pts[-1]
        prev_in = inside(prev)
        for cur in pts:
            cur_in = inside(cur)
            if cur_in != prev_in:
                # intersection with the clip line axis=value
                if axis == 0:
                    t = (value - prev[0]) / (cur[0] - prev[0])
                    out.append([value, prev[1] + t * (cur[1] - prev[1])])
                else:
                    t = (value - prev[1]) / (cur[1] - prev[1])
                    out.append([prev[0] + t * (cur[0] - prev[0]), value])
            if cur_in:
                out.append(cur)
            prev, prev_in = cur, cur_in
        pts = out
    return pts


def ring_area(ring):
    s = 0.0
    for i in range(len(ring)):
        x1, y1 = ring[i]
        x2, y2 = ring[(i + 1) % len(ring)]
        s += x1 * y2 - x2 * y1
    return abs(s) / 2.0


def process_geometry(geom, tol):
    """Clip + simplify a (Multi)Polygon; returns list of polygons (list of rings)."""
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    result = []
    for poly in polys:
        rings = []
        for ring in poly:
            clipped = clip_ring(ring, BBOX)
            if len(clipped) < 4 or ring_area(clipped) < MIN_RING_AREA:
                continue
            simplified = dp_simplify(clipped, tol)
            if len(simplified) < 4:
                continue
            rings.append([[round(x, PRECISION), round(y, PRECISION)] for x, y in simplified])
        if rings:
            result.append(rings)
    return result


def prop(props, *names):
    for n in names:
        for key in (n, n.upper(), n.lower()):
            if key in props and props[key] is not None:
                return props[key]
    return None


def main():
    parser = argparse.ArgumentParser()
    default_src = os.path.join(os.path.dirname(__file__), "basemap_src")
    parser.add_argument("--src", default=default_src)
    parser.add_argument(
        "--out",
        default=os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "basemap.js"),
    )
    args = parser.parse_args()

    with open(os.path.join(args.src, "ne_10m_admin_0_ukr.geojson"), encoding="utf-8") as f:
        admin0 = json.load(f)
    with open(os.path.join(args.src, "ne_10m_admin_1.geojson"), encoding="utf-8") as f:
        admin1 = json.load(f)

    countries = []
    for feat in admin0["features"]:
        name = prop(feat["properties"], "admin", "name")
        polys = process_geometry(feat["geometry"], COUNTRY_TOL)
        if polys:
            countries.append({"name": name, "polygons": polys})

    admin1_out = []
    for feat in admin1["features"]:
        props = feat["properties"]
        country = prop(props, "admin")
        iso = prop(props, "iso_3166_2") or ""
        if iso in UA_ISO_OVERRIDES:
            country = "Ukraine"
        if country not in ADMIN1_COUNTRIES:
            continue
        polys = process_geometry(feat["geometry"], ADMIN1_TOL)
        if not polys:
            continue
        admin1_out.append({
            "name": prop(props, "name_en", "name"),
            "iso": iso,
            "country": country,
            "polygons": polys,
        })

    basemap = {"countries": countries, "admin1": admin1_out}
    payload = "var BASEMAP = " + json.dumps(basemap, separators=(",", ":"), ensure_ascii=False) + ";\n"
    out_path = os.path.abspath(args.out)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(payload)
    print(f"countries: {len(countries)}, admin1: {len(admin1_out)}")
    print(f"wrote {out_path} ({os.path.getsize(out_path) / 1024:.0f} KiB)")


if __name__ == "__main__":
    main()
