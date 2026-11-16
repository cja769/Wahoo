package com.jay.wahoo.ai;

import com.jay.wahoo.Game;
import com.jay.wahoo.Marble;
import com.jay.wahoo.Player;

import java.util.SplittableRandom;

final class Simulation {
    private final Game game;
    private final Policy[] policies;
    private final SplittableRandom[] choices = new SplittableRandom[4];

    Simulation(long seed, int startingSeat, int turnLimit, Policy teamZero, Policy teamOne) {
        SplittableRandom random = new SplittableRandom(seed);
        SplittableRandom[] dice = new SplittableRandom[4];
        for (int seat = 0; seat < dice.length; seat++) {
            dice[seat] = random.split();
            choices[seat] = random.split();
        }
        game = Game.headless(seat -> dice[seat].nextInt(1, 7), startingSeat, turnLimit);
        policies = new Policy[] {teamZero, teamOne};
        advance();
    }

    void step(int action) {
        if (game.isGameComplete()) {
            throw new IllegalStateException("Episode is over");
        }
        game.playAction(action);
        advance();
    }

    private void advance() {
        while (!game.isGameComplete()) {
            game.advanceRoll();
            int mask = game.actionMask();
            if (mask == 0) {
                continue;
            }
            Player actor = game.currentPlayer;
            Policy policy = policies[actor.team()];
            int action;
            if (game.legalCount() == 1) {
                action = Integer.numberOfTrailingZeros(mask);
            } else if (policy == null) {
                return;
            } else if (policy == RandomPolicy.INSTANCE) {
                int chosen = choices[actor.seat()].nextInt(game.legalCount());
                for (int i = 0; i < chosen; i++) {
                    mask &= mask - 1;
                }
                action = Integer.numberOfTrailingZeros(mask);
            } else {
                action = policy.choose(Decision.encode(game), mask);
            }
            game.playAction(action);
        }
    }

    boolean done() { return game.isGameComplete(); }
    int winner() { return game.winningTeam(); }
    int mask() { return game.actionMask(); }
    int turns() { return game.turnCount(); }
    int rolls() { return game.rollCount(); }
    float[] observation() { return Decision.encode(game); }

    int completed(int team) {
        return game.players[team].safeBoard().getNumberOfMarblesComplete()
            + game.players[team + 2].safeBoard().getNumberOfMarblesComplete();
    }

    float progress(int team) {
        float total = 0;
        for (int seat = team; seat < 4; seat += 2) {
            for (Marble marble : game.players[seat].marbles()) {
                total += Decision.progress(marble.position());
            }
        }
        return total / 8;
    }

    private enum RandomPolicy implements Policy {
        INSTANCE;

        @Override
        public int choose(float[] observation, int mask) {
            throw new IllegalStateException("Random policy must use the game's seeded RNG");
        }
    }

    static Policy randomPolicy() {
        return RandomPolicy.INSTANCE;
    }
}
