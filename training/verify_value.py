"""Check a shared PPO value head against its exported Java implementation."""
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess

import numpy as np
from sb3_contrib import MaskablePPO
import torch

from .bridge import CLASSES, WahooVecEnv


def verify(checkpoint, actor, value):
    torch.set_num_threads(1)
    rng = np.random.default_rng(53)
    env = WahooVecEnv(64, 2, 800, lambda _: "heuristic")
    boards = []
    try:
        env.seed(811)
        observation = env.reset()
        for _ in range(128):
            boards.append(observation)
            mask = env.action_masks()
            observation, *_ = env.step(np.argmax(np.where(mask, rng.random(mask.shape), -1), axis=1))
    finally:
        env.close()
    positions = np.concatenate(boards)
    model = MaskablePPO.load(checkpoint, device="cpu")
    with torch.no_grad():
        expected = model.policy.predict_values(torch.as_tensor(positions)).numpy().ravel()

    java_home = os.getenv("JAVA_HOME")
    java = Path(java_home) / "bin/java" if java_home else "java"
    command = [
        java, "-cp", os.getenv("WAHOO_JAVA_CLASSPATH", str(CLASSES)),
        "com.jay.wahoo.ai.ValueProbe", actor, value,
    ]
    results = subprocess.run(command, input=struct.pack(">I", len(positions)) + positions.astype(">f4").tobytes(),
                             capture_output=True, check=True)
    actual = np.frombuffer(results.stdout, dtype=">f4")
    np.testing.assert_allclose(actual, expected, rtol=1e-5, atol=2e-5)
    return {
        "positions": len(positions),
        "max_error": float(np.max(np.abs(expected - actual))),
        "range": [float(actual.min()), float(actual.max())],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("--actor", type=Path, default=Path("models/best.wah"))
    parser.add_argument("--value", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.checkpoint, args.actor, args.value)))
