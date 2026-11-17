import json

import numpy as np
import pytest
from sb3_contrib import MaskablePPO
import torch

from training.benchmark import verify_parity
from training.bridge import SCHEMA, WahooVecEnv
from training.distill import warm_start
from training.shared_policy import MoveExtractor, SharedMovePolicy
from training.train import parser, train


@pytest.mark.parametrize("assisting", [0, 1])
def test_shared_actor_equivariance_and_shared_critic_invariance(assisting):
    torch.manual_seed(801)
    generator = np.random.default_rng(302)
    model = MoveExtractor()
    original = torch.rand(32, 123)
    original[:, 2] = assisting
    mask = torch.rand(32, 4) > 0.3
    mask[:, 0] = True
    original[:, 87::9] = mask.float()
    moves = original[:, 87:].reshape(-1, 4, 9)
    moves[~mask] = 0
    permuted = original.clone()
    for seat in range(4):
        order = generator.permutation(4)
        offset = 4 + 21 * seat
        pieces = original[:, offset:offset + 20].reshape(-1, 4, 5)
        permuted[:, offset:offset + 20] = pieces[:, order].flatten(1)
        if seat == 2 * assisting:
            permuted[:, 87:] = moves[:, order].flatten(1)
            action_order = order
    with torch.no_grad():
        logits, value = model(original)
        shuffled_logits, shuffled_value = model(permuted)
    torch.testing.assert_close(shuffled_logits, logits[:, action_order], rtol=0, atol=1e-7)
    torch.testing.assert_close(shuffled_value, value, rtol=0, atol=1e-7)
    original_action = logits.masked_fill(~mask, -torch.inf).argmax(dim=1)
    chosen = shuffled_logits.masked_fill(~mask[:, action_order], -torch.inf).argmax(dim=1)
    np.testing.assert_array_equal(np.asarray(action_order)[chosen], original_action)


def test_shared_ppo_checkpoint_resume_masking_and_java(tmp_path):
    options = ["--run", str(tmp_path), "--shared-moves", "--envs", "4", "--workers", "2",
               "--steps", "64", "--rollout", "16", "--batch", "32", "--epochs", "1"]
    train(parser().parse_args(options))
    marker = json.loads((tmp_path / "latest.json").read_text())
    assert marker["step"] == 64
    assert (tmp_path / "step-000000000000.wah").exists()
    actor = tmp_path / marker["actor"]
    checkpoint = tmp_path / marker["checkpoint"]
    assert actor.read_bytes()[:4] == b"WAH2"
    policy = MaskablePPO.load(checkpoint, device="cpu")
    assert isinstance(policy.policy, SharedMovePolicy)
    parity = verify_parity(checkpoint, actor, 2)
    assert parity["decisions"] == 384
    env = WahooVecEnv(4, 2, 800, lambda _: actor)
    try:
        env.seed(14001)
        observations = env.reset()
        for _ in range(48):
            mask = env.action_masks()
            actions, _ = policy.predict(observations, action_masks=mask, deterministic=False)
            assert mask[np.arange(4), actions].all()
            observations, *_ = env.step(actions)
    finally:
        env.close()
    train(parser().parse_args([*options, "--resume"]))
    marker = json.loads((tmp_path / "latest.json").read_text())
    assert marker["step"] == 128


def test_warm_start_rejects_a_teacher_from_old_rules(tmp_path):
    config = tmp_path / "config.json"
    config.write_text(json.dumps({"rules_schema": SCHEMA - 1}))
    with pytest.raises(ValueError, match="teacher checkpoint requires"):
        warm_start(None, tmp_path / "teacher.zip", None, 1, 1, tmp_path)
