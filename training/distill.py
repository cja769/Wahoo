"""Initialize the shared policy using a teacher on legal Java game states."""
import json
from time import perf_counter

import numpy as np
from sb3_contrib import MaskablePPO
import torch
from torch.nn import functional as F

from .bridge import ACTIONS, OBS_SIZE, SCHEMA, WahooVecEnv
from .checkpoints import actor_layers, write_json


def warm_start(model, checkpoint, opponent_picker, seed, workers, run, batches=2048):
    config = checkpoint.parent / "config.json"
    if not config.is_file() or json.loads(config.read_text()).get("rules_schema") != SCHEMA:
        raise ValueError(f"The teacher checkpoint requires a config.json with rules schema {SCHEMA}")
    teacher = MaskablePPO.load(checkpoint, device="cpu").policy
    actor_layers(teacher)
    rng = np.random.default_rng(seed)
    size = 256
    states = torch.empty((batches * size, OBS_SIZE))
    masks = torch.empty((len(states), ACTIONS), dtype=torch.bool)
    targets = torch.empty_like(masks, dtype=torch.float32)
    values = torch.empty(len(states))
    started = perf_counter()

    env = WahooVecEnv(size, workers, 800, opponent_picker)
    try:
        env.seed(seed)
        observation = env.reset()
        with torch.no_grad():
            for i in range(batches):
                rows = slice(i * size, (i + 1) * size)
                state = torch.from_numpy(observation)
                mask = torch.from_numpy(env.action_masks())
                features = teacher.extract_features(state)
                logits = teacher.action_net(teacher.mlp_extractor.forward_actor(features))
                value = teacher.predict_values(state).flatten()
                states[rows], masks[rows], targets[rows], values[rows] = state, mask, logits, value
                # A few randomized legal moves expose the student to states off the teacher's path.
                greedy = logits.masked_fill(~mask, -torch.inf).argmax(dim=1).numpy()
                random = np.argmax(np.where(mask.numpy(), rng.random(mask.shape), -1), axis=1)
                action = np.where(rng.random(size) < 0.15, random, greedy)
                observation, *_ = env.step(action)
    finally:
        env.close()

    # Ignore forced moves in the policy loss. Fit the critic on all visited states.
    choices = masks.sum(dim=1) > 1
    teacher_probs = (targets / 0.8).masked_fill(~masks, -torch.inf).softmax(dim=1)
    generator = torch.Generator().manual_seed(seed)
    split = torch.randperm(len(states), generator=generator)
    validation, train = split[:16384], split[16384:]
    policy = model.policy
    optimizer = torch.optim.Adam(policy.parameters(), lr=0.001)
    history = []
    for epoch in range(12):
        for index in train[torch.randperm(len(train), generator=generator)].split(512):
            logits, value_latents = policy.mlp_extractor(states[index])
            logprob = logits.masked_fill(~masks[index], -1e8).log_softmax(dim=1)
            cross_entropy = -(teacher_probs[index] * logprob).sum(dim=1)
            imitation = cross_entropy.sum() / choices[index].sum().clamp_min(1)
            value_loss = F.mse_loss(policy.value_net(value_latents).flatten(), values[index])
            loss = imitation + 0.5 * value_loss
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
        with torch.no_grad():
            logits = policy.mlp_extractor.forward_actor(states[validation])
            legal_logits = logits.masked_fill(~masks[validation], -1e8)
            # Identical home marbles can share a move even if the IDs differ.
            rows = validation[choices[validation]]
            spec = states[rows, 87:].reshape(-1, 4, 9)
            desired = targets[rows].masked_fill(~masks[rows], -torch.inf).argmax(dim=1)
            chosen = legal_logits[choices[validation]].argmax(dim=1)
            row = torch.arange(len(rows))
            agreement = (spec[row, desired] == spec[row, chosen]).all(dim=1).float().mean().item()
            history.append({"epoch": epoch + 1, "physical_move_agreement": agreement})
            print(history[-1], flush=True)
    write_json(run / "imitation.json", {
        "teacher": str(checkpoint), "seed": seed, "samples": len(states),
        "elapsed_seconds": perf_counter() - started, "validation_decisions": len(validation),
        "history": history,
    })
