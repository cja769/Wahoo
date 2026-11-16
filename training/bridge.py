"""Binary transport and SB3 vector environment backed by the Java game engine."""
import os
from pathlib import Path
import struct
import subprocess

import gymnasium as gym
import numpy as np
from stable_baselines3.common.vec_env import VecEnv

ROOT = Path(__file__).resolve().parents[1]
CLASSES = ROOT / "backend" / "build" / "classes" / "java" / "main"
OBS_SIZE = 123
SCHEMA = 1
ACTIONS = 4
ACTIVE = -2
UNFINISHED = -1

FIELDS = [
    ("outcome", "i1"), ("mask", "u1"), ("turns", ">i4"), ("rolls", ">i4"),
    ("completed", "u1", (2,)), ("progress", ">f4", (2,)),
]
STATS = np.dtype(FIELDS)
FRAME = np.dtype([*FIELDS, ("obs", ">f4", (OBS_SIZE,))])
PROBE = np.dtype([("logits", ">f4", (ACTIONS,)), ("action", "u1")])


def _string(value):
    data = str(value).encode("utf-8")
    return struct.pack(">i", len(data)) + data


class JavaBridge:
    def __init__(self, environments, workers, turn_limit):
        java_home = os.environ.get("JAVA_HOME")
        java = str(Path(java_home) / "bin" / "java") if java_home else "java"
        classpath = os.environ.get("WAHOO_JAVA_CLASSPATH", str(CLASSES))
        self.process = subprocess.Popen(
            [java, "-XX:-UsePerfData", "-cp", classpath,
             "com.jay.wahoo.ai.SimulationServer", str(environments), str(workers),
             str(turn_limit)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, start_new_session=True,
        )
        try:
            expected = struct.pack(">4i", 0x57414856, 1, SCHEMA, OBS_SIZE)
            if self._read(len(expected)) != expected:
                raise RuntimeError("Java simulation protocol or observation schema mismatch")
        except BaseException:
            self.close()
            raise
        self.environments = environments

    def _read(self, size):
        data = self.process.stdout.read(size)
        if len(data) != size:
            raise RuntimeError(
                "Java simulation exited or returned an incomplete frame. "
                "Check Java stderr and build backend classes before running training."
            )
        return data

    def _exchange(self, request, dtype, count):
        try:
            self.process.stdin.write(request)
            self.process.stdin.flush()
        except BrokenPipeError as error:
            raise RuntimeError("Java simulation closed stdin. Check Java stderr.") from error
        return np.frombuffer(self._read(dtype.itemsize * count), dtype=dtype, count=count)

    def reset(self, fixtures):
        request = bytearray(struct.pack(">Bi", 1, len(fixtures)))
        for index, seed, start, team, opponent in fixtures:
            request.extend(struct.pack(">iqBB", index, seed, start, team))
            request.extend(_string(opponent))
        return self._exchange(request, FRAME, len(fixtures))

    def step(self, actions):
        return self._exchange(b"\x02" + actions.tobytes(), FRAME, self.environments)

    def probe(self, policy):
        return self._exchange(b"\x03" + _string(policy), PROBE, self.environments)

    def benchmark(self, candidate, opponent, fixtures):
        request = bytearray(b"\x04" + _string(candidate) + _string(opponent))
        request.extend(struct.pack(">i", len(fixtures)))
        for seed, start, team in fixtures:
            request.extend(struct.pack(">qBB", seed, start, team))
        return self._exchange(request, STATS, len(fixtures))

    def close(self):
        try:
            self.process.stdin.close()
        except BrokenPipeError:
            pass
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        self.process.stdout.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


def terminal_reward(outcome, team, progress):
    if outcome == UNFINISHED:
        return 0.15 * float(progress[team] - progress[1 - team])
    if outcome in (0, 1):
        return 1.0 if outcome == team else -1.0
    return 0.0


class WahooVecEnv(VecEnv):
    def __init__(self, environments, workers, turn_limit, opponent_picker):
        self.bridge = JavaBridge(environments, workers, turn_limit)
        self.opponent_picker = opponent_picker
        self.rngs = [np.random.default_rng() for _ in range(environments)]
        self.episodes = np.arange(environments, dtype=np.int64)
        self.teams = np.zeros(environments, dtype=np.int8)
        self.lengths = np.zeros(environments, dtype=np.int32)
        self.rolls = np.zeros(environments, dtype=np.int32)
        self.frames = None
        self.actions = None
        self.total_rolls = 0
        super().__init__(environments, gym.spaces.Box(0, 1, (OBS_SIZE,), np.float32),
                         gym.spaces.Discrete(ACTIONS))

    def _reset_slots(self, indices):
        remaining = list(indices)
        # Very short test horizons can end before either teammate has a decision.
        for _ in range(64):
            if not remaining:
                return
            fixtures = []
            for index in remaining:
                rng = self.rngs[index]
                episode = int(self.episodes[index])
                team, start = episode % 2, (episode // 2) % 4
                self.teams[index] = team
                self.episodes[index] += 1
                dice_seed = int(rng.integers(2**63, dtype=np.int64))
                fixtures.append((index, dice_seed, start, team, self.opponent_picker(rng)))
            frames = self.bridge.reset(fixtures)
            self.total_rolls += int(frames["rolls"].sum())
            self.frames[remaining] = frames
            self.rolls[remaining] = frames["rolls"]
            self.lengths[remaining] = 0
            remaining = [index for index in remaining if self.frames[index]["outcome"] != ACTIVE]
        if remaining:
            raise RuntimeError("Increase the turn limit: no learner decisions during reset")

    def reset(self):
        for index, seed in enumerate(self._seeds):
            if seed is not None:
                self.rngs[index] = np.random.default_rng(seed)
                self.episodes[index] = index
        self._reset_seeds()
        self._reset_options()
        self.frames = np.empty(self.num_envs, dtype=FRAME)
        self._reset_slots(range(self.num_envs))
        return self.frames["obs"].astype(np.float32)

    def step_async(self, actions):
        actions = np.asarray(actions)
        if (actions.shape != (self.num_envs,) or actions.dtype.kind not in "iu"
                or np.any(actions < 0) or np.any(actions >= ACTIONS)):
            raise ValueError("Expected one integer in [0, 4) per environment")
        if not np.all(self.action_masks()[np.arange(self.num_envs), actions]):
            raise ValueError("Attempted masked Wahoo action")
        self.actions = actions.astype(np.uint8)

    def step_wait(self):
        if self.actions is None:
            raise RuntimeError("step_async must be called before step_wait")
        self.frames = self.bridge.step(self.actions).copy()
        self.actions = None
        self.total_rolls += int((self.frames["rolls"] - self.rolls).sum())
        self.rolls[:] = self.frames["rolls"]
        self.lengths += 1
        dones = self.frames["outcome"] != ACTIVE
        rewards = np.zeros(self.num_envs, dtype=np.float32)
        infos = [{} for _ in range(self.num_envs)]
        finished = np.flatnonzero(dones)
        for index in finished:
            frame, team = self.frames[index], self.teams[index]
            outcome = int(frame["outcome"])
            rewards[index] = terminal_reward(outcome, team, frame["progress"])
            infos[index] = {
                "terminal_observation": frame["obs"].astype(np.float32),
                "episode": {"r": float(rewards[index]), "l": int(self.lengths[index])},
                "team_win": outcome == team,
                "unfinished": outcome == UNFINISHED,
                "turns": int(frame["turns"]),
                "completed": int(frame["completed"][team]),
            }
        self._reset_slots(finished)
        return self.frames["obs"].astype(np.float32), rewards, dones, infos

    def action_masks(self):
        return (self.frames["mask"][:, None] & (1 << np.arange(ACTIONS))) != 0

    def close(self):
        self.bridge.close()

    def get_attr(self, attr_name, indices=None):
        if attr_name == "render_mode":
            return [None for _ in self._get_indices(indices)]
        if attr_name == "action_masks":
            return [lambda index=index: self.action_masks()[index]
                    for index in self._get_indices(indices)]
        raise AttributeError(attr_name)

    def set_attr(self, attr_name, value, indices=None):
        raise AttributeError(attr_name)

    def env_method(self, method_name, *args, indices=None, **kwargs):
        if method_name != "action_masks":
            raise AttributeError(method_name)
        masks = self.action_masks()
        return [masks[index] for index in self._get_indices(indices)]

    def env_is_wrapped(self, wrapper_class, indices=None):
        return [False for _ in self._get_indices(indices)]
