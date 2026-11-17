package com.jay.wahoo.ai;

import com.jay.wahoo.Game;
import com.jay.wahoo.GameBoard;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class LookaheadBenchmark {
    private record Fixture(long seed, int start, int team) {}

    public static void main(String[] args) throws Exception {
        DensePolicy actor = DensePolicy.load(Path.of(args[0]));
        int horizon = Integer.parseInt(args[2]);
        int workers = Integer.parseInt(args[6]);
        Policy continuation = args.length > 7 && args[7].equals("ppo") ? actor : Policy.HEURISTIC;
        LookaheadPolicy search = horizon == 0 ? null : new LookaheadPolicy(actor, actor.loadValue(Path.of(args[1])),
            horizon, Integer.parseInt(args[3]), Float.parseFloat(args[4]), Float.parseFloat(args[5]), continuation);
        Policy opponent = switch (args[8]) {
            case "heuristic" -> Policy.HEURISTIC;
            case "live" -> DensePolicy.load(Path.of(args[9]));
            case "lookahead" -> {
                DensePolicy opponentActor = DensePolicy.load(Path.of(args[9]));
                yield LookaheadPolicy.approved(opponentActor, Path.of(args[10]));
            }
            default -> throw new IllegalArgumentException("Unknown benchmark opponent " + args[8]);
        };
        var in = new DataInputStream(new BufferedInputStream(System.in));
        Fixture[] fixtures = new Fixture[in.readInt()];
        for (int i = 0; i < fixtures.length; i++) {
            fixtures[i] = new Fixture(in.readLong(), in.readUnsignedByte(), in.readUnsignedByte());
        }
        var pool = new ForkJoinPool(workers);
        try {
            var rows = pool.submit(() -> IntStream.range(0, fixtures.length)
                .parallel().mapToObj(i -> play(fixtures[i], search, actor, opponent, i < 512)).toArray(String[]::new)).get();
            for (String row : rows) {
                System.out.println(row);
            }
        } finally {
            pool.shutdown();
        }
    }

    private static String play(Fixture fixture, LookaheadPolicy search, DensePolicy actor,
                               Policy opponent, boolean recordExample) {
        var measured = new Measured(search, actor, recordExample);
        var game = new Simulation(fixture.seed, fixture.start, 800,
            fixture.team == 0 ? measured : opponent, fixture.team == 1 ? measured : opponent);
        return game.winner() + "," + game.turns() + "," + game.rolls() + ","
            + game.completed(0) + "," + game.completed(1) + ","
            + measured.decisions + "," + measured.searched + "," + measured.changed + ","
            + Arrays.toString(measured.histogram).replace(" ", "").replace(",", ":") + ","
            + (measured.example == null ? "" : measured.example);
    }

    private static final class Measured implements Policy {
        private final LookaheadPolicy search;
        private final DensePolicy actor;
        private final boolean recordExample;
        int decisions;
        int searched;
        int changed;
        final int[] histogram = new int[32];
        String example;

        Measured(LookaheadPolicy search, DensePolicy actor, boolean recordExample) {
            this.search = search;
            this.actor = actor;
            this.recordExample = recordExample;
        }

        @Override
        public int choose(float[] observation, int mask) {
            return actor.choose(observation, mask);
        }

        @Override
        public int choose(Game game) {
            long start = System.nanoTime();
            int move;
            LookaheadPolicy.Choice choice = null;
            float[] observation = Decision.encode(game);
            if (search != null) {
                choice = search.compare(game, observation);
                move = choice.move();
            } else {
                move = actor.choose(observation, game.actionMask());
            }
            int nanos = (int) Math.min(Integer.MAX_VALUE, Math.max(1, System.nanoTime() - start));
            histogram[31 - Integer.numberOfLeadingZeros(nanos)]++;
            decisions++;
            if (choice != null) {
                searched += choice.searched() ? 1 : 0;
                if (move != choice.original()) {
                    changed++;
                    if (recordExample && example == null) {
                        example = example(game, choice, observation);
                    }
                }
            }
            return move;
        }
    }

    private static String example(Game game, LookaheadPolicy.Choice choice, float[] observation) {
        var old = game.legalMove(choice.original());
        var next = game.legalMove(choice.move());
        return game.rollCount() + ":" + game.currentPlayer.seat() + ":" + game.diceRoll + ":" + game.sixCount()
            + ":" + (game.currentPlayer != game.marbleOwner() ? 1 : 0) + ":" + choice.estimatedGain()
            + ":" + describe(old, choice.original(), observation) + ":" + describe(next, choice.move(), observation);
    }

    private static String describe(GameBoard.LegalMove move, int action, float[] observation) {
        return move.marble().position() + ":" + move.destination() + ":"
            + (move.captured() == null ? -1 : move.captured().player().seat()) + ":"
            + observation[Decision.ACTION_OFFSET + action * Decision.ACTION_FEATURES + 8];
    }
}
