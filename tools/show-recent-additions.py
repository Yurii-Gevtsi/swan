#!/usr/bin/env python3
"""Show which events a recent pipeline run added.

Examples:
  python tools/show-recent-additions.py              # events added today
  python tools/show-recent-additions.py --days 3     # last 3 days
  python tools/show-recent-additions.py --uk         # Ukrainian titles
"""

import argparse
import datetime as dt
import json
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
EVENTS = REPO_ROOT / "app" / "src" / "main" / "assets" / "osint_events.json"
WEEKLY_PROCESSED = REPO_ROOT / "data" / "weekly" / "processed"
PIPELINE_PROCESSED = Path(r"C:\Projects\OSINT\swan-data-pipeline\processed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--days", type=int, default=0,
                        help="How many days back to include (0 = today only)")
    parser.add_argument("--uk", action="store_true", help="Show Ukrainian titles")
    args = parser.parse_args()

    cutoff = (dt.datetime.now(dt.timezone.utc).date() - dt.timedelta(days=args.days)).isoformat()
    payload = json.loads(EVENTS.read_text(encoding="utf-8-sig"))
    events = payload["events"]
    added = [e for e in events if str(e.get("createdAt", ""))[:10] >= cutoff]
    added.sort(key=lambda e: (e.get("date", ""), e.get("id", "")), reverse=True)

    print(f"Dataset: {len(events)} events total, generated {payload.get('generatedAt', '-')[:19]}")
    print(f"Added since {cutoff}: {len(added)}")
    if added:
        print("By category:", dict(Counter(e["category"] for e in added)))
        print()
        for e in added:
            title = e.get("titleUk") if args.uk else e.get("titleEn")
            print(f"  {e.get('date')}  [{e['category'][:24]:<24}] {(title or '')[:60]}")
            print(f"      sources: {e.get('sources', '')[:80]}")

    print()
    print("Raw source reports (what the search agent found):")
    for folder in (PIPELINE_PROCESSED, WEEKLY_PROCESSED):
        if folder.is_dir():
            newest = sorted(folder.glob("*"), key=lambda p: p.stat().st_mtime, reverse=True)[:3]
            for p in newest:
                stamp = dt.datetime.fromtimestamp(p.stat().st_mtime).strftime("%Y-%m-%d %H:%M")
                print(f"  {stamp}  {p}")


if __name__ == "__main__":
    main()
