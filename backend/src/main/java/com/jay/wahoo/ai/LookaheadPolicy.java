package com.jay.wahoo.ai;

import com.jay.wahoo.Game;
import com.jay.wahoo.GameBoard;
import com.jay.wahoo.Marble;
import com.jay.wahoo.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.SplittableRandom;

/** Compares close legal moves on matched hypothetical dice streams. */
public final class LookaheadPolicy implements Policy {
    private final DensePolicy actor;
    private final DensePolicy.PositionValue value;
    private final int rolls;
    private final int samples;
    private final float margin;
    private final float improvement;
    private final Policy continuation;

    public LookaheadPolicy(DensePolicy actor, DensePolicy.PositionValue value, int rolls,
                           int samples, float margin, float improvement, Policy continuation) {
        if (rolls < 1 || rolls > 32 || samples < 1 || samples > 64
            || !Float.isFinite(margin) || !Float.isFinite(improvement)
            || margin < 0 || improvement < 0) {
            throw new IllegalArgumentException("Invalid lookahead budget");
        }
        this.actor = actor;
        this.value = value;
        this.rolls = rolls;
        this.samples = samples;
        this.margin = margin;
        this.improvement = improvement;
        this.continuation = continuation;
    }

    public static LookaheadPolicy approved(DensePolicy actor, Path value) throws IOException {
        return new LookaheadPolicy(actor, actor.loadValue(value), 16, 12, 1.0f, 0.025f, Policy.HEURISTIC);
    }

    public String actorSha256() {
        return actor.sha256();
    }

    @Override
    public int choose(float[] observation, int mask) {
        return actor.choose(observation, mask);
    }

    @Override
    public int choose(Game game) {
        return compare(game, Decision.encode(game)).move();
    }

    record Choice(int move, int original, boolean searched, float estimatedGain) {}

    Choice compare(Game game, float[] observation) {
        int legal = game.actionMask();
        float[] logits = actor.logits(observation);
        int original = Policy.bestLegal(logits, legal);
        int candidates = 1 << original;
        boolean homeIncluded = game.legalMove(original).marble().position() == GameBoard.HOME;
        for (int action = 0; action < 4; action++) {
            if (action == original || (legal & (1 << action)) == 0
                || logits[original] - logits[action] > margin) {
                continue;
            }
            boolean home = game.legalMove(action).marble().position() == GameBoard.HOME;
            if (!home || !homeIncluded) {
                candidates |= 1 << action;
            }
            homeIncluded |= home;
        }
        if (Integer.bitCount(candidates) == 1) {
            return new Choice(original, original, false, 0);
        }

        int rootSeat = game.currentPlayer.seat();
        int perspective = game.currentPlayer.team();
        MatchedDice dice = new MatchedDice(rolls + 1);
        Game scratch = game.rolloutGame(seat -> dice.roll((seat - rootSeat + 4) % 4));
        SplittableRandom random = new SplittableRandom(positionSeed(game));
        float[] totals = new float[4];
        for (int sample = 0; sample < samples; sample++) {
            dice.sample(random);
            for (int actions = candidates; actions != 0; actions &= actions - 1) {
                int action = Integer.numberOfTrailingZeros(actions);
                dice.reset();
                scratch.restoreDecision(game);
                scratch.playAction(action);
                totals[action] += rollout(scratch, perspective);
            }
        }
        int choice = original;
        float bestGain = 0;
        for (int actions = candidates & ~(1 << original); actions != 0; actions &= actions - 1) {
            int action = Integer.numberOfTrailingZeros(actions);
            float gain = (totals[action] - totals[original]) / samples;
            float adjusted = gain - improvement - 0.06f * (logits[original] - logits[action]);
            if (adjusted > bestGain) {
                choice = action;
                bestGain = adjusted;
            }
        }
        return new Choice(choice, original, true, (totals[choice] - totals[original]) / samples);
    }

    private float rollout(Game game, int perspective) {
        for (int i = 0; i < rolls && !game.isGameComplete(); i++) {
            game.advanceRoll();
            int mask = game.actionMask();
            if (mask != 0) {
                int action = game.legalCount() == 1 ? Integer.numberOfTrailingZeros(mask) : continuation.choose(game);
                game.playAction(action);
            }
        }
        if (!game.isGameComplete()) {
            game.advanceRoll(); // Observe the next roll without applying the pending decision.
        }
        int winner = game.winningTeam();
        if (winner >= 0) {
            return winner == perspective ? 1 : -1;
        }
        // The learned value can extrapolate beyond terminal win/loss rewards.
        float estimate = Math.max(-1f, Math.min(1f, value.estimate(Decision.encodeBoard(game))));
        return game.currentPlayer.team() == perspective ? estimate : -estimate;
    }

    static long positionSeed(Game game) {
        // Sort within each relative seat so renumbering identical physical marbles preserves the samples.
        long hash = 31L * game.diceRoll + game.sixCount();
        int[] positions = new int[4];
        for (int offset = 0; offset < 4; offset++) {
            Player player = game.players[(game.currentPlayer.seat() + offset) % 4];
            for (Marble marble : player.marbles()) {
                positions[marble.identifier()] = marble.position();
            }
            Arrays.sort(positions);
            for (int position : positions) {
                hash = (hash ^ (position + 2)) * 0x100000001b3L;
            }
        }
        return hash;
    }

    static final class MatchedDice {
        private final int[][] values;
        private final int[] offsets = new int[4];
        private final int length;

        MatchedDice(int length) {
            this.length = length;
            values = new int[4][length];
        }

        void sample(SplittableRandom random) {
            for (int roll = 0; roll < length; roll++) {
                for (int seat = 0; seat < 4; seat++) {
                    values[seat][roll] = random.nextInt(1, 7);
                }
            }
        }

        void reset() {
            Arrays.fill(offsets, 0);
        }

        int roll(int seat) {
            return values[seat][offsets[seat]++];
        }
    }
}
