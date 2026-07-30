#!/usr/bin/env python3
"""Keep only events located on internationally recognized Russian territory.

Project rule: the map documents strikes on Russia proper. Temporarily occupied
territories of Ukraine -- Crimea, Sevastopol and the occupied parts of Donetsk,
Luhansk, Zaporizhzhia and Kherson oblasts -- are Ukrainian territory and must
not appear as "Russian" targets.

Enforcement is geographic (point-in-polygon against Ukraine's internationally
recognized borders from Natural Earth), never keyword-based: place names like
"Krymsk" or LPDS "Krymska" are in Russia's Krasnodar Krai and must be kept.

Maritime events (Black Sea, Azov, Baltic) stay: the polygon covers land only.

This filters the published output; the source snapshots keep every record, so
the rule can be relaxed later by removing this step.
"""

import argparse
import json
from collections import Counter
from pathlib import Path

from geo_ukraine import in_ukraine

# The rule is about *land targets*. A vessel struck in the Kerch Strait or off
# the Crimean coast is a ship, not occupied territory, so maritime events are
# exempt even when their generalized coordinate falls over the peninsula.
LAND_PRECISIONS = {"REGION_LEVEL", "CITY_OR_REGION_ANCHOR"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--app-output", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--report-only", action="store_true",
                        help="Report what would be excluded without writing files")
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    events = payload["events"]

    kept, excluded, maritime_exempt = [], [], []
    for event in events:
        try:
            lat, lng = float(event["lat"]), float(event["lng"])
        except (KeyError, TypeError, ValueError):
            kept.append(event)
            continue
        if not in_ukraine(lat, lng):
            kept.append(event)
        elif str(event.get("precision") or "") in LAND_PRECISIONS:
            excluded.append(event)
        else:
            maritime_exempt.append(event)
            kept.append(event)

    lines = [
        f"Events total:    {len(events)}",
        f"Kept (Russia):   {len(kept)}",
        f"Excluded (UA occupied territory / Crimea, land targets): {len(excluded)}",
        f"Maritime over UA waters/land, kept as vessels: {len(maritime_exempt)}",
        "",
        "Excluded by year: " + str(dict(sorted(Counter(e.get("date", "?")[:4] for e in excluded).items()))),
        "Excluded by category: " + str(dict(Counter(e.get("category") for e in excluded).most_common())),
        "",
        "Excluded events:",
    ]
    for e in sorted(excluded, key=lambda x: x.get("date", ""), reverse=True):
        lines.append(f"  {e.get('date')}  ({e.get('lat')},{e.get('lng')})  {e.get('titleEn') or e.get('titleUk')}")
    if maritime_exempt:
        lines.append("")
        lines.append("Maritime events kept (vessels, not territory):")
        for e in sorted(maritime_exempt, key=lambda x: x.get("date", ""), reverse=True):
            lines.append(f"  {e.get('date')}  ({e.get('lat')},{e.get('lng')})  {e.get('titleEn') or e.get('titleUk')}")
    report = "\n".join(lines) + "\n"

    if args.report:
        args.report.write_text(report, encoding="utf-8")
    print("\n".join(lines[:7]))

    if args.report_only:
        print("(report-only: nothing written)")
        return

    payload["events"] = kept
    payload["recordCount"] = len(kept)
    serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    if args.app_output:
        args.app_output.write_text(serialized, encoding="utf-8")
    print(f"Excluded {len(excluded)} events located on Ukrainian territory.")


if __name__ == "__main__":
    main()
