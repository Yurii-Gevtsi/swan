#!/usr/bin/env python3
"""Collapse fine-grained event categories into the six canonical map categories.

Runs in finalize-data.ps1 before scatter/grouping so both the whole current
dataset and every future weekly batch end up with categories that map 1:1 to
the map filter chips. Keyword-aware (see category_taxonomy.canonical_category).
"""

import argparse
import json
from collections import Counter
from pathlib import Path

import category_taxonomy as tax


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--app-output", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    payload = json.loads(args.input.read_text(encoding="utf-8-sig"))
    events = payload["events"]

    changes = Counter()
    before = Counter(e.get("category") for e in events)
    for event in events:
        old = str(event.get("category") or "")
        new = tax.canonical_category(old, event=event)
        if new != old:
            changes[f"{old} -> {new}"] += 1
            event["category"] = new
    after = Counter(e.get("category") for e in events)

    serialized = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    args.input.write_text(serialized, encoding="utf-8")
    args.app_output.write_text(serialized, encoding="utf-8")

    lines = ["CANONICAL CATEGORY DISTRIBUTION (after):"]
    for cat, n in after.most_common():
        lines.append(f"  {n:>5}  {cat}")
    lines.append("")
    lines.append(f"Reclassified {sum(changes.values())} events:")
    for change, n in changes.most_common():
        lines.append(f"  {n:>5}  {change}")
    report = "\n".join(lines) + "\n"
    if args.report:
        args.report.write_text(report, encoding="utf-8")
    print(report)

    stray = [c for c in after if c not in tax.CANONICAL]
    if stray:
        raise SystemExit(f"Non-canonical categories still present: {stray}")


if __name__ == "__main__":
    main()
