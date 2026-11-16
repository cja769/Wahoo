"""Evaluate an exported Wahoo policy and optionally promote it after head-to-head tests."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from tempfile import TemporaryDirectory
from time import perf_counter

import numpy as np
from sb3_contrib import MaskablePPO
import torch

from .bridge import SCHEMA, JavaBridge, WahooVecEnv, UNFINISHED
from .checkpoints import actor_layers, atomic_write, export_actor, run_lock, write_json

MIN_PROMOTION_PAIRS = 256


def verify_parity(checkpoint, actor, workers):
    model = MaskablePPO.load(checkpoint, device="cpu")
    layers = actor_layers(model.policy)
    env = WahooVecEnv(12, workers, 800, lambda _: "heuristic")
    rng = np.random.default_rng(14319)
    max_error = 0.0
    try:
        env.seed(81811)
        observations = env.reset()
        for _ in range(32):
            masks = env.action_masks()
            java = env.bridge.probe(actor)
            with torch.no_grad():
                values = torch.as_tensor(observations)
                for layer in layers[:-1]:
                    values = torch.tanh(layer(values))
                logits = layers[-1](values).numpy()
            error = float(np.max(np.abs(logits - java["logits"])))
            max_error = max(max_error, error)
            np.testing.assert_allclose(java["logits"], logits, rtol=1e-5, atol=2e-5)
            actions, _ = model.predict(observations, action_masks=masks, deterministic=True)
            np.testing.assert_array_equal(java["action"], actions)
            random_legal = np.argmax(np.where(masks, rng.random(masks.shape), -1), axis=1)
            observations, *_ = env.step(random_legal)
    finally:
        env.close()
    return {"decisions": 384, "max_logit_error": max_error}


def fixtures(seed, pairs):
    # Dedicated evaluation namespace. Replay each set of per-seat dice streams with teams swapped.
    rng = np.random.default_rng(np.random.SeedSequence([seed, 0x4556414C]))
    return [(int(dice_seed), index % 4, team)
            for index, dice_seed in enumerate(rng.integers(2**63, size=pairs, dtype=np.int64))
            for team in (0, 1)]


def summarize(rows, seed):
    teams = np.tile([0, 1], len(rows) // 2)
    wins = rows["outcome"] == teams
    unfinished = rows["outcome"] == UNFINISHED
    # The two games from a dice seed are correlated. Bootstrap seed pairs as clusters.
    # Each pair has 0, 1, or 2 wins, so multinomial resampling needs little memory.
    pair_wins = wins.reshape(-1, 2).sum(axis=1)
    pairs = len(pair_wins)
    counts = np.bincount(pair_wins, minlength=3)
    rng = np.random.default_rng(np.random.SeedSequence([seed, 0x4349]))
    resampled = rng.multinomial(pairs, counts / pairs, size=20000)
    interval = np.quantile((resampled[:, 1] + 2 * resampled[:, 2]) / len(rows), [0.025, 0.975])
    index = np.arange(len(rows))
    return {
        "games": len(rows), "wins": int(wins.sum()),
        "losses": int((~wins & ~unfinished).sum()), "unfinished": int(unfinished.sum()),
        "win_rate": float(wins.mean()), "win_rate_ci95": interval.tolist(),
        "unfinished_rate": float(unfinished.mean()), "mean_turns": float(rows["turns"].mean()),
        "mean_rolls": float(rows["rolls"].mean()),
        "mean_completed": float(rows["completed"][index, teams].mean()),
        "mean_opponent_completed": float(rows["completed"][index, 1 - teams].mean()),
    }


def promotion_failures(results, pairs):
    failures = []
    if pairs < MIN_PROMOTION_PAIRS:
        failures.append(f"Need at least {MIN_PROMOTION_PAIRS} mirrored pairs per opponent")
    for opponent in ("random", "heuristic", "incumbent"):
        if opponent == "incumbent" and opponent not in results:
            continue
        row = results[opponent]
        if row["win_rate_ci95"][0] <= 0.5:
            failures.append(f"{opponent}: lower 95% win-rate bound must exceed 50%")
        if row["unfinished_rate"] > 0.10:
            failures.append(f"{opponent}: unfinished rate exceeds 10%")
    return failures


def evaluate(args, checkpoint, actor, run):
    best = run / "best.wah" if run else None
    matches = {"random": "random", "heuristic": "heuristic"}
    matches.update({f"checkpoint/{path.name}": path.resolve() for path in args.opponent})
    if len(matches) != len(args.opponent) + 2:
        raise ValueError("Opponent filenames must be distinct")
    if best and best.is_file():
        matches["incumbent"] = best

    parity = verify_parity(checkpoint, actor, args.workers) if checkpoint else None
    games = fixtures(args.seed, args.pairs)
    start = perf_counter()
    results = {}
    with JavaBridge(1, args.workers, args.turn_limit) as bridge:
        for label, opponent in matches.items():
            rows = bridge.benchmark(actor, opponent, games)
            results[label] = summarize(rows, args.seed)
    elapsed = perf_counter() - start
    failures = promotion_failures(results, args.pairs)
    if parity is None:
        failures.append("Promotion requires a PPO checkpoint for Python/Java parity")
    if args.turn_limit != 800:
        failures.append("Promotion uses the fixed 800-turn horizon")

    report = {
        "model": (checkpoint or actor).name, "rules_schema": SCHEMA,
        "actor_sha256": hashlib.sha256(actor.read_bytes()).hexdigest(),
        "seed": args.seed, "pairs_per_opponent": args.pairs,
        "turn_limit": args.turn_limit, "python_java_parity": parity, "matchups": results,
        "opponent_actor_sha256": {
            label: hashlib.sha256(opponent.read_bytes()).hexdigest()
            for label, opponent in matches.items() if isinstance(opponent, Path)
        },
        "games_per_second": round(len(matches) * len(games) / elapsed, 1),
        "promotion_failures": failures, "promoted": bool(args.promote and not failures),
    }
    if report["promoted"]:
        with atomic_write(best) as temporary:
            shutil.copyfile(actor, temporary)
        write_json(run / "best-benchmark.json", report)
    return report


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("model", type=Path, help="Trusted PPO .zip checkpoint or exported .wah policy")
    cli.add_argument("--opponent", action="append", type=Path, default=[], help="Historical .wah policy")
    cli.add_argument("--run", type=Path, help="Run directory containing the incumbent best.wah")
    cli.add_argument("--pairs", type=int, default=512)
    cli.add_argument("--seed", type=int, default=20270101)
    cli.add_argument("--workers", type=int, default=4)
    cli.add_argument("--turn-limit", type=int, default=800)
    cli.add_argument("--report", type=Path)
    cli.add_argument("--promote", action="store_true")
    args = cli.parse_args()
    if args.pairs < 4 or args.pairs % 4 or args.workers < 1 or args.turn_limit < 1 or args.seed < 0:
        cli.error("Pairs must be a positive multiple of four, workers and turns positive, and seed nonnegative")
    if args.promote and not args.run:
        cli.error("--promote requires --run")
    if args.model.suffix not in {".zip", ".wah"} or any(p.suffix != ".wah" for p in args.opponent):
        cli.error("Use a .zip or .wah candidate and .wah historical opponents")

    torch.set_num_threads(1)
    run = args.run.resolve() if args.run else None
    with TemporaryDirectory(prefix="wahoo-benchmark-") as temporary:
        checkpoint = args.model.resolve() if args.model.suffix == ".zip" else None
        if checkpoint:
            config_file = checkpoint.parent / "config.json"
            if not config_file.is_file() or json.loads(config_file.read_text()).get("rules_schema") != SCHEMA:
                cli.error(f"Checkpoint must be in a training run with rules schema {SCHEMA}")
            actor = Path(temporary) / "candidate.wah"
            export_actor(MaskablePPO.load(checkpoint, device="cpu"), actor)
        else:
            actor = args.model.resolve()
        if args.promote:
            with run_lock(run):
                report = evaluate(args, checkpoint, actor, run)
        else:
            report = evaluate(args, checkpoint, actor, run)
    if args.report:
        write_json(args.report, report)
    print(json.dumps(report, indent=2))
    if args.promote and not report["promoted"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
