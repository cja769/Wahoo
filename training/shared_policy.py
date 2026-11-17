"""Maskable PPO actor that scores each marble with the same network."""
import torch
from torch import nn
from sb3_contrib.common.maskable.policies import MaskableActorCriticPolicy

MARBLE_WIDTH = 16
CONTEXT_WIDTH = 48
MOVE_WIDTH = 48


class MoveScore(nn.Linear):
    def __init__(self):
        super().__init__(MOVE_WIDTH, 1)

    def forward(self, moves):
        return super().forward(moves).squeeze(-1)


class MoveExtractor(nn.Module):
    latent_dim_vf = CONTEXT_WIDTH

    def __init__(self):
        super().__init__()
        self.piece = nn.Linear(5, MARBLE_WIDTH)
        self.context = nn.Linear(3 + 4 * (1 + MARBLE_WIDTH), CONTEXT_WIDTH)
        self.move = nn.Linear(CONTEXT_WIDTH + MARBLE_WIDTH + 9, MOVE_WIDTH)
        self.value = nn.Linear(CONTEXT_WIDTH, CONTEXT_WIDTH)
        self.score = MoveScore()

    def board(self, observation):
        header = observation[:, :3]
        seats = observation[:, 3:87].reshape(-1, 4, 21)
        pieces = self.piece(seats[:, :, 1:].reshape(-1, 4, 4, 5)).tanh()
        # Sum in double precision so a permutation preserves the pooled features.
        pooled = pieces.mean(dim=2, dtype=torch.float64).to(pieces.dtype)
        teams = torch.cat((seats[:, :, :1], pooled), dim=-1).flatten(1)
        context = self.context(torch.cat((header, teams), dim=1)).tanh()
        owner = torch.where(header[:, 2, None, None].bool(), pieces[:, 2], pieces[:, 0])
        return context, owner

    def moves(self, observation, context, owner):
        options = observation[:, 87:].reshape(-1, 4, 9)
        features = torch.cat((context[:, None, :].expand(-1, 4, -1), owner, options), dim=-1)
        return self.score(self.move(features).tanh())

    def forward(self, observation):
        context, owner = self.board(observation)
        return self.moves(observation, context, owner), self.value(context).tanh()

    def forward_actor(self, observation):
        return self.moves(observation, *self.board(observation))

    def forward_critic(self, observation):
        context, _ = self.board(observation)
        return self.value(context).tanh()


class SharedMovePolicy(MaskableActorCriticPolicy):
    def _build(self, lr_schedule):
        self.mlp_extractor = MoveExtractor()
        self.action_net = nn.Identity()
        self.value_net = nn.Linear(CONTEXT_WIDTH, 1)
        if self.ortho_init:
            self.mlp_extractor.apply(lambda module: self.init_weights(module, gain=2**0.5))
            self.init_weights(self.mlp_extractor.score, gain=0.01)
            self.init_weights(self.value_net, gain=1)
        self.optimizer = self.optimizer_class(
            self.parameters(), lr=lr_schedule(1), **self.optimizer_kwargs
        )
