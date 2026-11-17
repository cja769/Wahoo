"""Run matched-dice Wahoo benchmarks for a bounded lookahead policy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
from time import perf_counter

import numpy as np

from .benchmark import fixtures, summarize
from .bridge import CLASSES, STATS
from .checkpoints import write_json

PRESETS = {
    "base": (0, 1, 0, 0, "heuristic"),
    "short": (4, 8, 0.50, 0.025, "heuristic"),
    "medium": (8, 10, 0.75, 0.025, "heuristic"),
    "ppo": (6, 8, 0.60, 0.025, "ppo"),
    "wide": (8, 12, 1.50, 0.04, "heuristic"),
    "deep": (16, 12, 1.0, 0.025, "heuristic"),
    "wide-ppo": (8, 12, 1.25, 0.018, "ppo"),
    "deep-ppo": (16, 12, 1.0, 0.015, "ppo"),
    "sensitive": (4, 12, 1.25, 0.005, "heuristic"),
    "more-samples": (8, 24, 0.75, 0.025, "heuristic"),
}


def latency(histogram):
    total = histogram.sum()
    counts = histogram.cumsum()
    bounds = [2 ** int(np.searchsorted(counts, total * q, side="left") + 1) / 1e6
              for q in (0.5, 0.95, 0.99)]
    return dict(zip(("p50_upper_ms", "p95_upper_ms", "p99_upper_ms"), bounds, strict=True))


def run(args, preset):
    pattern = fixtures(args.seed, args.pairs)
    request = struct.pack(">I", len(pattern)) + b"".join(struct.pack(">qBB", *row) for row in pattern)
    java_home = os.getenv("JAVA_HOME")
    java = Path(java_home) / "bin/java" if java_home else "java"
    command = [
        java, "-Xmx384m", "-cp", os.getenv("WAHOO_JAVA_CLASSPATH", str(CLASSES)),
        "com.jay.wahoo.ai.LookaheadBenchmark", args.actor, str(args.value or ""),
        *map(str, PRESETS[preset][:4]), str(args.workers), PRESETS[preset][4], args.opponent,
        args.opponent_actor, str(args.opponent_value or ""),
    ]
    start = perf_counter()
    proc = subprocess.run(command, input=request, check=True, capture_output=True)
    rows = np.zeros(len(pattern), dtype=STATS)
    histogram = np.zeros(32, dtype=np.int64)
    decisions = searched = changed = 0
    examples = []
    lines = proc.stdout.decode().splitlines()
    if len(lines) != len(pattern):
        raise RuntimeError("Benchmark did not return all games")
    for i, line in enumerate(lines):
        outcome, turns, rolls, zero, one, count, examined, overrides, buckets, example = line.split(",")
        rows[i]["outcome"], rows[i]["turns"], rows[i]["rolls"] = int(outcome), int(turns), int(rolls)
        rows[i]["completed"] = int(zero), int(one)
        decisions += int(count)
        searched += int(examined)
        changed += int(overrides)
        histogram += np.fromstring(buckets[1:-1], sep=":", dtype=np.int64)
        if example and len(examples) < 256:
            values = example.split(":")
            samples = []
            for offset in (6, 10):
                source, dest, capture, danger = values[offset:offset + 4]
                samples.append({"source": int(source), "destination": int(dest),
                                "capture_seat": int(capture), "danger": float(danger)})
            examples.append({
                "dice_seed": pattern[i][0], "starting_seat": pattern[i][1],
                "team": pattern[i][2], "outcome": int(outcome),
                "roll": int(values[0]), "seat": int(values[1]), "die": int(values[2]),
                "six_streak": int(values[3]), "assisting": values[4] == "1",
                "estimated_gain": float(values[5]), "original": samples[0], "lookahead": samples[1],
            })
    if args.outcomes:
        np.save(args.outcomes, rows["outcome"], allow_pickle=False)
    return {
        "rules_schema": 3, "actor_sha256": hashlib.sha256(args.actor.read_bytes()).hexdigest(),
        "value_sha256": hashlib.sha256(args.value.read_bytes()).hexdigest() if args.value else None,
        "opponent_actor_sha256": hashlib.sha256(args.opponent_actor.read_bytes()).hexdigest()
        if args.opponent != "heuristic" else None,
        "opponent_value_sha256": hashlib.sha256(args.opponent_value.read_bytes()).hexdigest()
        if args.opponent_value else None,
        "preset": preset, "settings": PRESETS[preset], "opponent": args.opponent,
        "seed": args.seed, "pairs": args.pairs, "workers": args.workers,
        "elapsed_seconds": round(perf_counter() - start, 2), "results": summarize(rows, args.seed),
        "decisions": decisions, "searched": searched, "changed": changed,
        "latency": latency(histogram), "examples": examples,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--actor", type=Path, default=Path("models/best.wah"))
    parser.add_argument("--value", type=Path)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--pairs", type=int, required=True)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--preset", choices=PRESETS, required=True)
    parser.add_argument("--opponent", choices=["live", "heuristic", "lookahead"], default="live")
    parser.add_argument("--opponent-actor", type=Path, default=Path("models/best.wah"))
    parser.add_argument("--opponent-value", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--outcomes", type=Path, help="Save per-game outcomes in fixture order")
    args = parser.parse_args()
    if args.pairs < 4 or args.pairs % 4 or args.workers < 1:
        parser.error("Pairs must be a positive multiple of four, and workers must be positive")
    if args.preset != "base" and args.value is None:
        parser.error("Lookahead presets require --value")
    if args.opponent == "lookahead" and args.opponent_value is None:
        parser.error("A lookahead opponent requires --opponent-value")
    result = run(args, args.preset)
    write_json(args.report, result)
    print(json.dumps({key: val for key, val in result.items() if key != "examples"}), flush=True)


if __name__ == "__main__":
    main()
