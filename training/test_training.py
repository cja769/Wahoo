import json
import signal
import subprocess
import sys
from time import monotonic, sleep

import numpy as np
import pytest
from sb3_contrib import MaskablePPO

from training.benchmark import fixtures, promotion_failures, summarize, verify_parity
from training.bridge import JavaBridge, WahooVecEnv, terminal_reward
from training.checkpoints import OpponentPool, atomic_write, run_lock
from training.train import parser, train


def test_reward_preserves_wins_and_only_uses_progress_at_cutoff():
    progress = [0.9, 0.1]
    assert terminal_reward(0, 0, progress) == 1
    assert terminal_reward(1, 0, progress) == -1
    assert terminal_reward(-2, 0, progress) == 0
    assert terminal_reward(-1, 0, progress) == pytest.approx(0.12)
    assert terminal_reward(-1, 1, progress) == pytest.approx(-0.12)
    assert terminal_reward(-1, 0, [1, 0]) == pytest.approx(0.15)
    assert terminal_reward(-1, 0, [0.7, 0.7]) == 0


def test_vector_masks_match_java_observations_and_invalid_actions_are_rejected():
    env = WahooVecEnv(4, 2, 48, lambda _: "random")
    try:
        env.seed(851)
        observation = env.reset()
        done_seen = False
        invalid_seen = False
        rng = np.random.default_rng(49)
        for _ in range(64):
            masks = env.action_masks()
            np.testing.assert_array_equal(masks, observation[:, 87::9] == 1)
            assert masks.sum(axis=1).min() >= 2  # Forced decisions never cross the bridge.
            assert np.all((observation >= 0) & (observation <= 1))
            actions = np.argmax(np.where(masks, rng.random(masks.shape), -1), axis=1)
            illegal = np.argwhere(~masks)
            if len(illegal):
                slot, action = illegal[0]
                bad = actions.copy()
                bad[slot] = action
                with pytest.raises(ValueError, match="masked"):
                    env.step_async(bad)
                np.testing.assert_array_equal(masks, env.action_masks())
                invalid_seen = True
            observation, rewards, dones, infos = env.step(actions)
            assert np.all(rewards[~dones] == 0)
            for i in np.flatnonzero(dones):
                assert infos[i]["unfinished"]
                assert infos[i]["turns"] == 48
                assert abs(rewards[i]) <= 0.15
                done_seen = True
        assert done_seen and invalid_seen
    finally:
        env.close()


def test_fixtures_repeat_and_balance_seats_and_workers():
    games = fixtures(8290, 16)
    assert games == fixtures(8290, 16)
    assert games != fixtures(8291, 16)
    assert len({seed for seed, _, _ in games}) == 16
    assert {(start, team) for _, start, team in games} == {(s, t) for s in range(4) for t in range(2)}
    with JavaBridge(1, 1, 800) as serial, JavaBridge(1, 3, 800) as parallel:
        a = serial.benchmark("random", "heuristic", games)
        b = parallel.benchmark("random", "heuristic", games)
    np.testing.assert_array_equal(a, b)
    stats = summarize(a, 8290)
    assert stats["wins"] + stats["losses"] + stats["unfinished"] == stats["games"] == 32
    assert stats["mean_opponent_completed"] >= stats["mean_completed"]


def test_promotion_requires_significant_improvement_and_enough_games():
    row = {"win_rate_ci95": [0.51, 0.59], "unfinished_rate": 0.01}
    results = {"random": row, "heuristic": row}
    assert not promotion_failures(results, 256)
    assert promotion_failures(results, 128)
    results["incumbent"] = {"win_rate_ci95": [0.49, 0.58], "unfinished_rate": 0.11}
    assert len(promotion_failures(results, 256)) == 2


def test_atomic_files_and_run_lock(tmp_path):
    path = tmp_path / "actor.wah"
    path.write_bytes(b"existing")
    with pytest.raises(OSError, match="write failed"):
        with atomic_write(path) as temporary:
            temporary.write_bytes(b"partial")
            raise OSError("write failed")
    assert path.read_bytes() == b"existing"
    with run_lock(tmp_path), pytest.raises(RuntimeError, match="active trainer"):
        with run_lock(tmp_path):
            pytest.fail("Concurrent lock acquired")
    with run_lock(tmp_path):
        pass


def test_training_resume_checkpoint_history_and_python_java_parity(tmp_path):
    base = ["--run", str(tmp_path), "--envs", "4", "--workers", "2", "--steps", "64",
            "--rollout", "16", "--batch", "32", "--epochs", "1", "--width", "16"]
    train(parser().parse_args(base))
    marker = json.loads((tmp_path / "latest.json").read_text())
    model = MaskablePPO.load(tmp_path / marker["checkpoint"], device="cpu")
    assert model.num_timesteps == 64
    assert model.policy.optimizer.state_dict()["state"]
    parity = verify_parity(tmp_path / marker["checkpoint"], tmp_path / marker["actor"], workers=2)
    assert parity["decisions"] == 384

    # A file left by an interruption prior to updating the latest pointer is uncommitted.
    orphan = tmp_path / "step-000000000130.wah"
    orphan.write_bytes(b"incomplete")
    pool = OpponentPool(tmp_path)
    assert pool.history == [tmp_path / marker["actor"]]

    train(parser().parse_args([*base, "--resume"]))
    resumed = json.loads((tmp_path / "latest.json").read_text())
    assert resumed["step"] == 128
    assert (tmp_path / marker["checkpoint"]).is_file()
    pool.refresh()
    assert len(pool.history) == 2
    assert orphan not in pool.history
    steps = [json.loads(line)["step"] for line in (tmp_path / "metrics.jsonl").read_text().splitlines()]
    assert steps == [64, 128]


def test_graceful_stop_checkpoints_an_optimized_rollout(tmp_path):
    process = subprocess.Popen(
        [sys.executable, "-m", "training.train", "--run", str(tmp_path), "--envs", "4",
         "--workers", "2", "--steps", "9999999", "--rollout", "16", "--batch", "32",
         "--epochs", "1", "--width", "16", "--checkpoint-every", "9999999"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, start_new_session=True,
    )
    try:
        metrics = tmp_path / "metrics.jsonl"
        deadline = monotonic() + 20
        while not metrics.exists() and process.poll() is None and monotonic() < deadline:
            sleep(0.05)
        assert metrics.exists()
        process.send_signal(signal.SIGTERM)
        _, stderr = process.communicate(timeout=10)
        assert process.returncode == 0, stderr.decode()
        marker = json.loads((tmp_path / "latest.json").read_text())
        assert marker["step"] >= 64 and marker["step"] % 64 == 0
        assert (tmp_path / marker["checkpoint"]).is_file()
        assert (tmp_path / marker["actor"]).is_file()
    finally:
        if process.poll() is None:
            process.kill()
            process.communicate()
