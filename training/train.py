"""Run or resume Maskable PPO against a rotating population of Wahoo opponents."""
import argparse
from collections import deque
from contextlib import contextmanager
import json
from pathlib import Path
import signal
from time import perf_counter

import numpy as np
from sb3_contrib import MaskablePPO
from stable_baselines3.common.callbacks import BaseCallback
from stable_baselines3.common.logger import configure
import torch

from .bridge import SCHEMA, WahooVecEnv
from .checkpoints import OpponentPool, run_lock, save_checkpoint, write_json
from .distill import warm_start
from .shared_policy import SharedMovePolicy


class SharedOpponentPool(OpponentPool):
    def refresh(self):
        super().refresh()
        self.frozen = sorted((self.run / "frozen").glob("*.wah"))

    def pick(self, rng):
        draw = rng.random()
        if draw < 0.02:
            return "random"
        if draw < 0.37:
            return "heuristic"
        if draw >= 0.80 and self.history:
            recent = self.history[-4:]
            return recent[int(rng.integers(len(recent)))]
        if draw >= 0.57 and self.frozen:
            return self.frozen[int(rng.integers(len(self.frozen)))]
        return self.incumbent if self.incumbent.is_file() else "heuristic"


class CheckpointPPO(MaskablePPO):
    after_update = None

    def _excluded_save_params(self):
        return [*super()._excluded_save_params(), "after_update"]

    def train(self):
        super().train()
        if self.after_update is not None:
            self.after_update(self)


class TrainingStatus(BaseCallback):
    def __init__(self):
        super().__init__()
        self.stop_requested = False
        self.stop_this_rollout = False
        self.recent = deque(maxlen=1000)
        self.finished = 0

    def request_stop(self, *_):
        self.stop_requested = True

    def _on_rollout_start(self):
        self.stop_this_rollout = self.stop_requested

    def _on_step(self):
        for info in self.locals["infos"]:
            if "episode" in info:
                self.recent.append(info)
                self.finished += 1
        return not self.stop_this_rollout


@contextmanager
def stop_handlers(handler):
    previous = {sig: signal.signal(sig, handler) for sig in (signal.SIGINT, signal.SIGTERM)}
    try:
        yield
    finally:
        for sig, callback in previous.items():
            signal.signal(sig, callback)


def train(args):
    run = args.run.resolve()
    torch.set_num_threads(args.torch_threads)
    settings = {
        "rules_schema": SCHEMA,
        "seed": args.seed, "envs": args.envs, "turn_limit": args.turn_limit,
        "n_steps": args.rollout, "batch_size": args.batch, "n_epochs": args.epochs,
        "learning_rate": args.learning_rate, "gamma": args.gamma,
        "gae_lambda": args.gae_lambda, "clip_range": args.clip, "ent_coef": args.entropy,
        "width": args.width,
    }
    if args.shared_moves:
        settings["actor"] = "shared-move-v1"
    sample_count = args.envs * args.rollout
    if args.batch < 2 or sample_count % args.batch:
        raise ValueError("--batch must be at least 2 and divide --envs times --rollout")
    with run_lock(run):
        marker = run / "latest.json"
        config_file = run / "config.json"
        if marker.exists() != args.resume:
            raise ValueError("Use --resume for an existing checkpoint, or a fresh --run directory")
        if args.resume and json.loads(config_file.read_text()) != settings:
            raise ValueError("Resume with the same rules schema, rollout, seed and PPO configuration in config.json")
        if not args.resume:
            write_json(config_file, settings)
        pool = (SharedOpponentPool if args.shared_moves else OpponentPool)(run)
        env = WahooVecEnv(args.envs, args.workers, args.turn_limit, pool.pick)
        try:
            if args.resume:
                checkpoint = json.loads(marker.read_text())
                model = CheckpointPPO.load(run / checkpoint["checkpoint"], env=env, device=args.device)
            else:
                ppo_settings = {key: settings[key] for key in (
                    "n_steps", "batch_size", "n_epochs", "learning_rate", "gamma",
                    "gae_lambda", "clip_range", "ent_coef",
                )}
                policy_class = SharedMovePolicy if args.shared_moves else "MlpPolicy"
                kwargs = {} if args.shared_moves else {
                    "activation_fn": torch.nn.Tanh,
                    "net_arch": dict(pi=[args.width, args.width], vf=[args.width, args.width]),
                }
                model = CheckpointPPO(
                    policy_class, env, **ppo_settings, device=args.device, seed=args.seed,
                    policy_kwargs=kwargs,
                )
                if args.warm_start:
                    warm_start(model, args.warm_start, pool.pick, args.seed ^ 0x574148,
                               args.workers, run)
                if args.shared_moves:
                    save_checkpoint(model, run)
                    pool.refresh()
            _learn(args, run, model, env, pool)
        finally:
            env.close()


def _learn(args, run, model, env, pool):
    status = TrainingStatus()
    initial_step = model.num_timesteps
    target = initial_step + args.steps
    last_checkpoint = initial_step
    last_update = initial_step
    # New streams after resume because in-flight episodes and rollouts are not checkpointed.
    env.seed(int(np.random.SeedSequence([args.seed, initial_step, 0x574148]).generate_state(1)[0]))
    model.set_logger(configure(str(run), []))
    started = perf_counter()

    def after_update(ppo):
        nonlocal last_checkpoint, last_update
        step = ppo.num_timesteps
        last_update = step
        elapsed = max(perf_counter() - started, 1e-6)
        metrics = {
            "step": step, "decisions_per_second": round((step - initial_step) / elapsed, 1),
            "rolls_per_second": round(env.total_rolls / elapsed, 1),
            "games_finished_this_session": status.finished,
        }
        if status.recent:
            metrics["mean_reward"] = float(np.mean([info["episode"]["r"] for info in status.recent]))
            for stat in ("team_win", "unfinished", "turns", "completed"):
                metrics[f"mean_{stat}"] = float(np.mean([info[stat] for info in status.recent]))
        for name, value in ppo.logger.name_to_value.items():
            if name.startswith("train/") and np.isfinite(value):
                metrics[name] = float(value)
        if (step - last_checkpoint >= args.checkpoint_every or step >= target
                or status.stop_requested):
            save_checkpoint(ppo, run)
            last_checkpoint = step
            pool.refresh()
            metrics["checkpoint"] = True
        record = json.dumps(metrics, allow_nan=False)
        with (run / "metrics.jsonl").open("a") as stream:
            stream.write(record + "\n")
        print(record, flush=True)

    model.after_update = after_update
    with stop_handlers(status.request_stop):
        model.learn(total_timesteps=args.steps, callback=status, reset_num_timesteps=False)
    if last_checkpoint < last_update:
        # A signal between updates can stop the next rollout before the checkpoint hook runs.
        model.num_timesteps = last_update
        save_checkpoint(model, run)
    if last_update == initial_step and not status.stop_requested:
        raise RuntimeError("No PPO update completed")


def parser():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--run", required=True, type=Path)
    cli.add_argument("--resume", action="store_true")
    cli.add_argument("--shared-moves", action="store_true")
    cli.add_argument("--warm-start", type=Path, help="Compatible dense PPO checkpoint to imitate")
    cli.add_argument("--device", default="cpu")
    for name, default in {
        "seed": 104729, "envs": 16, "workers": 4, "torch-threads": 1, "steps": 500_000,
        "turn-limit": 800, "rollout": 256, "batch": 256, "epochs": 5, "width": 64,
        "checkpoint-every": 32768,
    }.items():
        cli.add_argument(f"--{name}", type=int, default=default)
    for name, default in {
        "learning-rate": 3e-4, "gamma": 0.997, "gae-lambda": 0.97, "clip": 0.2, "entropy": 0.012,
    }.items():
        cli.add_argument(f"--{name}", type=float, default=default)
    return cli


def main():
    cli = parser()
    args = cli.parse_args()
    positive = (args.envs, args.workers, args.torch_threads, args.steps, args.turn_limit,
                args.rollout, args.batch, args.epochs, args.width, args.checkpoint_every)
    if min(positive) < 1 or args.width > 512 or not 0 <= args.seed < 2**32:
        cli.error("Expected positive counts, width at most 512, and an unsigned 32-bit training seed")
    if args.warm_start and (args.resume or not args.shared_moves or args.device != "cpu"):
        cli.error("--warm-start initializes a fresh --shared-moves run on CPU")
    train(args)


if __name__ == "__main__":
    main()
