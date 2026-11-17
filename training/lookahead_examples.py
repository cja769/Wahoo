"""Replay tactical examples and compare paired continuations for each move."""
import argparse
import json
import os
from pathlib import Path
import subprocess

import numpy as np

from .bridge import CLASSES
from .checkpoints import write_json


def examples(report):
    changed = sorted(report["examples"], key=lambda row: -row["estimated_gain"])
    categories = {
        "take_capture": lambda a, b, seat: (
            b["capture_seat"] >= 0 and (b["capture_seat"] - seat) % 2 == 1
            and a["capture_seat"] == -1
        ),
        "avoid_exposure": lambda a, b, _: a["danger"] - b["danger"] >= 1 / 12 - 1e-6,
        "release_marble": lambda a, b, _: a["source"] >= 0 and b["source"] == -1,
        "enter_safe_lane": lambda a, b, _: a["destination"] < 52 <= b["destination"],
    }
    used = set()
    for name, predicate in categories.items():
        for row in changed:
            identity = row["dice_seed"], row["team"]
            if identity not in used and predicate(row["original"], row["lookahead"], row["seat"]):
                used.add(identity)
                yield name, row
                break


def replay(report, row, args, seed):
    java_home = os.getenv("JAVA_HOME")
    java = Path(java_home) / "bin/java" if java_home else "java"
    command = [
        java, "-Xmx384m", "-cp", os.getenv("WAHOO_JAVA_CLASSPATH", str(CLASSES)),
        "com.jay.wahoo.ai.LookaheadExamples", args.actor, args.value, *map(str, report["settings"]),
        *[str(row[key]) for key in ("dice_seed", "starting_seat", "team", "roll")],
        str(args.samples), str(seed), str(args.workers),
    ]
    result = subprocess.run(command, capture_output=True, check=True)
    outcomes = np.fromstring(result.stdout.decode().replace("\n", ","), sep=",", dtype=np.int8)
    outcomes = outcomes.reshape(args.samples, 2)
    wins = outcomes == row["team"]
    delta = wins[:, 1].astype(int) - wins[:, 0]
    counts = np.bincount(delta + 1, minlength=3)
    bootstrap = np.random.default_rng(seed).multinomial(args.samples, counts / args.samples, size=20000)
    interval = np.quantile((bootstrap[:, 2] - bootstrap[:, 0]) / args.samples, [0.025, 0.975])
    return {
        **row, "replay_seed": seed, "paired_continuations": args.samples,
        "original_move_wins": int(wins[:, 0].sum()),
        "lookahead_move_wins": int(wins[:, 1].sum()),
        "difference": float(delta.mean()), "difference_ci95": interval.tolist(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("benchmark", type=Path)
    parser.add_argument("--actor", type=Path, default=Path("models/best.wah"))
    parser.add_argument("--value", type=Path, required=True)
    parser.add_argument("--samples", type=int, default=2048)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--seed", type=int, default=20271146)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    benchmark = json.loads(args.benchmark.read_text())
    if benchmark["opponent"] != "live":
        parser.error("Example reconstruction expects games against the live actor")
    output = {name: replay(benchmark, row, args, args.seed + index)
              for index, (name, row) in enumerate(examples(benchmark))}
    write_json(args.report, output)


if __name__ == "__main__":
    main()
