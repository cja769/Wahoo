"""Export the critic of a trusted shared-policy PPO checkpoint for Java lookahead."""
import argparse
from hashlib import sha256
from pathlib import Path
import struct
from tempfile import TemporaryDirectory

import numpy as np
from sb3_contrib import MaskablePPO

from .checkpoints import atomic_write, export_actor
from .shared_policy import SharedMovePolicy


def export(checkpoint, path):
    model = MaskablePPO.load(checkpoint, device="cpu")
    policy = model.policy
    if not isinstance(policy, SharedMovePolicy):
        raise ValueError("Lookahead requires a shared move-scoring policy")
    with TemporaryDirectory() as directory:
        actor = Path(directory) / "actor.wah"
        export_actor(model, actor)
        digest = sha256(actor.read_bytes()).digest()
    with atomic_write(path) as temporary, temporary.open("wb") as stream:
        stream.write(b"WAV1" + digest)
        for layer in (policy.mlp_extractor.value, policy.value_net):
            stream.write(struct.pack(">I", layer.out_features))
            for tensor in (layer.weight, layer.bias):
                weights = tensor.detach().cpu().numpy()
                if not np.isfinite(weights).all():
                    raise ValueError("Value head contains nonfinite weights")
                stream.write(weights.astype(">f4").tobytes())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export(args.checkpoint, args.output)
