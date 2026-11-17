package com.jay.wahoo.ai;

import com.jay.wahoo.Game;

import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/** Replays an observed change and estimates its value using independent full-game continuations. */
public final class LookaheadExamples {
    public static void main(String[] args) throws Exception {
        var actor = DensePolicy.load(Path.of(args[0]));
        var value = actor.loadValue(Path.of(args[1]));
        Policy continuation = args[6].equals("ppo") ? actor : Policy.HEURISTIC;
        var policy = new LookaheadPolicy(actor, value, Integer.parseInt(args[2]),
            Integer.parseInt(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5]), continuation);
        long fixture = Long.parseLong(args[7]);
        int start = Integer.parseInt(args[8]);
        int team = Integer.parseInt(args[9]);
        int roll = Integer.parseInt(args[10]);
        int samples = Integer.parseInt(args[11]);
        long seed = Long.parseLong(args[12]);
        int workers = Integer.parseInt(args[13]);
        var replay = new Replay(policy, roll);
        new Simulation(fixture, start, 800, team == 0 ? replay : actor, team == 1 ? replay : actor);
        if (replay.position == null) {
            throw new IllegalArgumentException("The requested decision was not reached");
        }
        var random = new SplittableRandom(seed);
        long[] dice = IntStream.range(0, samples).mapToLong(i -> random.nextLong()).toArray();
        var pool = new ForkJoinPool(workers);
        try {
            String[] rows = pool.submit(() -> IntStream.range(0, samples).parallel().mapToObj(i ->
                play(replay.position, replay.choice.original(), dice[i], actor) + ","
                    + play(replay.position, replay.choice.move(), dice[i], actor)).toArray(String[]::new)).get();
            for (String row : rows) {
                System.out.println(row);
            }
        } finally {
            pool.shutdown();
        }
    }

    private static int play(Game position, int action, long seed, Policy actor) {
        var random = new SplittableRandom(seed);
        SplittableRandom[] rolls = {random.split(), random.split(), random.split(), random.split()};
        Game game = position.rolloutGame(seat -> rolls[seat].nextInt(1, 7));
        game.restoreDecision(position);
        game.playAction(action);
        while (!game.isGameComplete()) {
            game.advanceRoll();
            int mask = game.actionMask();
            if (mask != 0) {
                game.playAction(game.legalCount() == 1 ? Integer.numberOfTrailingZeros(mask) : actor.choose(game));
            }
        }
        return game.winningTeam();
    }

    private static final class Replay implements Policy {
        final LookaheadPolicy policy;
        final int roll;
        Game position;
        LookaheadPolicy.Choice choice;

        Replay(LookaheadPolicy policy, int roll) {
            this.policy = policy;
            this.roll = roll;
        }

        @Override
        public int choose(float[] observation, int mask) {
            return policy.choose(observation, mask);
        }

        @Override
        public int choose(Game game) {
            var decision = policy.compare(game, Decision.encode(game));
            if (game.rollCount() == roll) {
                if (decision.move() == decision.original()) {
                    throw new IllegalStateException("Lookahead did not change this move");
                }
                position = game.rolloutGame(seat -> { throw new IllegalStateException("Snapshot dice read"); });
                position.restoreDecision(game);
                choice = decision;
            }
            return decision.move();
        }
    }
}
