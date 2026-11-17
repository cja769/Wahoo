"""Atomic model publication, run locking, and the historical opponent pool."""
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import struct
import tempfile

import numpy as np
from torch import nn

from .bridge import ACTIONS, OBS_SIZE, SCHEMA
from .shared_policy import SharedMovePolicy


@contextmanager
def run_lock(run):
    run.mkdir(parents=True, exist_ok=True)
    with (run / ".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError(f"An active trainer or promotion is using {run}") from error
        yield


@contextmanager
def atomic_write(path):
    fd, filename = tempfile.mkstemp(dir=path.parent, suffix=path.suffix)
    os.close(fd)
    temporary = Path(filename)
    try:
        yield temporary
        with temporary.open("rb") as stream:
            os.fsync(stream.fileno())
        temporary.replace(path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        temporary.unlink(missing_ok=True)


def write_json(path, value):
    with atomic_write(path) as temporary:
        temporary.write_text(json.dumps(value, indent=2, allow_nan=False) + "\n")


def actor_layers(policy):
    modules = list(policy.mlp_extractor.policy_net)
    if len(modules) != 4 or not all(
        isinstance(module, nn.Linear if index % 2 == 0 else nn.Tanh)
        for index, module in enumerate(modules)
    ) or not isinstance(policy.action_net, nn.Linear):
        raise ValueError("Java export requires a two-layer Linear/Tanh actor")
    layers = [modules[0], modules[2], policy.action_net]
    if (layers[0].in_features != OBS_SIZE or layers[-1].out_features != ACTIONS
            or any(layer.out_features > 512 for layer in layers)):
        raise ValueError("Unsupported observation or actor layer dimensions")
    return layers


def export_actor(model, path):
    if isinstance(model.policy, SharedMovePolicy):
        actor = model.policy.mlp_extractor
        layers = [actor.piece, actor.context, actor.move, actor.score]
        magic = 0x57414832
    else:
        layers = actor_layers(model.policy)
        magic = 0x57414831
    with atomic_write(path) as temporary, temporary.open("wb") as stream:
        stream.write(struct.pack(">III", magic, SCHEMA, OBS_SIZE))
        for layer in layers:
            stream.write(struct.pack(">I", layer.out_features))
            for tensor in (layer.weight, layer.bias):
                values = tensor.detach().cpu().numpy()
                if not np.all(np.isfinite(values)):
                    raise ValueError("Model contains nonfinite weights")
                stream.write(values.astype(">f4").tobytes())


def save_checkpoint(model, run):
    step = model.num_timesteps
    stem = f"step-{step:012d}"
    zip_file = run / f"{stem}.zip"
    actor_file = run / f"{stem}.wah"
    with atomic_write(zip_file) as temporary:
        model.save(temporary)
    export_actor(model, actor_file)
    write_json(run / "latest.json", {"step": step, "checkpoint": zip_file.name,
                                    "actor": actor_file.name})


class OpponentPool:
    def __init__(self, run):
        self.run = run
        self.refresh()

    def refresh(self):
        marker = self.run / "latest.json"
        if marker.is_file():
            last_actor = json.loads(marker.read_text())["actor"]
            self.history = sorted(path for path in self.run.glob("step-*.wah") if path.name <= last_actor)
        else:
            self.history = []
        self.incumbent = self.run / "best.wah"

    def pick(self, rng):
        draw = rng.random()
        if draw < 0.25:
            return "random"
        if draw < 0.60 or not self.history:
            return "heuristic"
        if self.incumbent.is_file() and rng.random() < 0.25:
            return self.incumbent
        # Sample recent opponents and uniformly sample all generations, including early ones.
        pool = self.history[-4:] if rng.random() < 0.5 else self.history
        return pool[int(rng.integers(len(pool)))]
