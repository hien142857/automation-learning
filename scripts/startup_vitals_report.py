#!/usr/bin/env python3
"""Summarize this project's Macrobenchmark TTID runs, never ActivityManager/UI times."""
import argparse
import csv
import json
import math
import re
from pathlib import Path

THRESHOLDS = {"COLD": 5000, "WARM": 2000, "HOT": 1500}
CLASS = "vn.startupbench.macrobenchmark.StartupVitalsBenchmark"


def summarize(data):
    rows = {}
    for test in data.get("benchmarks", []):
        if test.get("className") != CLASS:
            continue
        mode = test.get("params", {}).get("mode")
        if mode not in THRESHOLDS or mode in rows:
            raise ValueError("Unknown/duplicate startup mode; use one current report from this runner")
        metric = test.get("metrics", {}).get("timeToInitialDisplayMs", {})
        runs = metric.get("runs", [])
        expected = test.get("repeatIterations")
        if type(expected) is not int or expected < 1 or not isinstance(runs, list) or len(runs) > expected:
            raise ValueError("Invalid iteration count/TTID runs")
        values = []
        for value in runs:
            if value is None:
                continue
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
                raise ValueError("Invalid TTID duration; refusing a misleading percentage")
            values.append(value)
        slow = sum(value > THRESHOLDS[mode] for value in values)
        rows[mode] = dict(mode=mode, thresholdMs=THRESHOLDS[mode], planned=expected,
                          measured=len(values), slow=slow, missing=expected-len(values),
                          slowPercent=slow*100/len(values) if values else None,
                          ttidMs=values)
    if not rows:
        raise ValueError("No StartupVitalsBenchmark TTID results found; zero tests is not a pass")
    return {"source": "androidx-macrobenchmark-perfetto-ttid", "scope": "local-lab-not-play-console",
            "compilationProtocol": "CompilationMode.Ignore; installed state preserved",
            "context": data.get("context", {}),
            "unrunModes": [mode for mode in THRESHOLDS if mode not in rows],
            "results": [rows[mode] for mode in THRESHOLDS if mode in rows]}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("report", type=Path)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--csv", type=Path)
    p.add_argument("--package", required=True, help="Measured target package (record from the run command)")
    p.add_argument("--version", required=True, help="Measured target version/build, recorded by tester")
    args = p.parse_args()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", args.package) or not args.version.strip():
        p.error("Provide the measured app's valid package and nonempty version/build")
    paths = [args.report.resolve(), args.output.resolve()] + ([args.csv.resolve()] if args.csv else [])
    if len(set(paths)) != len(paths):
        p.error("Input evidence, JSON output and CSV output must be different files")
    try:
        result = summarize(json.loads(args.report.read_text()))
    except (ValueError, TypeError, AttributeError, OSError) as exc:
        p.error(str(exc))
    result.update(targetPackage=args.package, targetVersion=args.version)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
    if args.csv:
        with args.csv.open("w", newline="", encoding="utf-8") as stream:
            fields = ["targetPackage", "targetVersion", "source", "mode", "thresholdMs", "planned", "measured", "slow", "missing", "slowPercent"]
            writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(dict(row, targetPackage=args.package, targetVersion=args.version, source=result["source"]) for row in result["results"])
    print(json.dumps({"results": result["results"], "unrunModes": result["unrunModes"]}, ensure_ascii=False))
    # Keep the report, but fail incomplete runs so automation cannot mark them ready.
    return 2 if result["unrunModes"] or any(r["missing"] for r in result["results"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
