package com.jay.wahoo.ai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A local, batched, binary stdin/stdout protocol. One JVM hosts independent games. */
public final class SimulationServer {
    private static final int MAGIC = 0x57414856; // WAHV
    private static final int PROTOCOL = 1;
    private static final int RESET = 1;
    private static final int STEP = 2;
    private static final int PROBE = 3;
    private static final int BENCHMARK = 4;
    private static final float[] TERMINAL = new float[Decision.SIZE];

    private final Simulation[] games;
    private final int turnLimit;
    private final ExecutorService workers;
    private final Map<String, DensePolicy> snapshots = new LinkedHashMap<>(32, 0.75f, true);

    private SimulationServer(int environments, int threads, int turnLimit) {
        if (environments < 1 || threads < 1 || turnLimit < 1) {
            throw new IllegalArgumentException("Environments, workers and turns must be positive");
        }
        games = new Simulation[environments];
        this.turnLimit = turnLimit;
        workers = Executors.newFixedThreadPool(threads);
    }

    public static void main(String[] args) throws Exception {
        SimulationServer server = new SimulationServer(
            Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        try {
            server.serve();
        } finally {
            server.workers.shutdownNow();
        }
    }

    private void serve() throws Exception {
        var input = new DataInputStream(new BufferedInputStream(System.in));
        var output = new DataOutputStream(new BufferedOutputStream(System.out));
        output.writeInt(MAGIC);
        output.writeInt(PROTOCOL);
        output.writeInt(Decision.SCHEMA);
        output.writeInt(Decision.SIZE);
        output.flush();
        for (int command; (command = input.read()) != -1; ) {
            switch (command) {
                case RESET -> reset(input, output);
                case STEP -> step(input, output);
                case PROBE -> probe(input, output);
                case BENCHMARK -> benchmark(input, output);
                default -> throw new IOException("Unknown simulation command " + command);
            }
            output.flush();
        }
    }

    private void reset(DataInputStream input, DataOutputStream output) throws Exception {
        int count = input.readInt();
        List<Callable<Frame>> jobs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = input.readInt();
            long seed = input.readLong();
            int start = input.readUnsignedByte();
            int team = input.readUnsignedByte();
            Policy opponent = policy(readString(input));
            jobs.add(() -> {
                var game = new Simulation(seed, start, turnLimit, team == 0 ? null : opponent,
                    team == 0 ? opponent : null);
                games[index] = game;
                return Frame.from(game, true);
            });
        }
        writeFrames(output, jobs, true);
    }

    private void step(DataInputStream input, DataOutputStream output) throws Exception {
        List<Callable<Frame>> jobs = new ArrayList<>(games.length);
        for (Simulation game : games) {
            int action = input.readUnsignedByte();
            jobs.add(() -> {
                game.step(action);
                return Frame.from(game, true);
            });
        }
        writeFrames(output, jobs, true);
    }

    private void probe(DataInputStream input, DataOutputStream output) throws IOException {
        DensePolicy policy = load(readString(input));
        for (Simulation game : games) {
            float[] observation = game.observation();
            float[] logits = policy.logits(observation);
            for (float logit : logits) {
                output.writeFloat(logit);
            }
            output.writeByte(Policy.bestLegal(logits, game.mask()));
        }
    }

    private void benchmark(DataInputStream input, DataOutputStream output) throws Exception {
        Policy candidate = policy(readString(input));
        Policy opponent = policy(readString(input));
        int count = input.readInt();
        List<Callable<Frame>> jobs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long seed = input.readLong();
            int start = input.readUnsignedByte();
            int team = input.readUnsignedByte();
            jobs.add(() -> Frame.from(new Simulation(seed, start, turnLimit,
                team == 0 ? candidate : opponent, team == 0 ? opponent : candidate), false));
        }
        writeFrames(output, jobs, false);
    }

    private Policy policy(String name) throws IOException {
        return switch (name) {
            case "random" -> Simulation.randomPolicy();
            case "heuristic" -> Policy.HEURISTIC;
            default -> load(name);
        };
    }

    // A checkpoint filename is immutable throughout a training session.
    private DensePolicy load(String file) throws IOException {
        DensePolicy policy = snapshots.get(file);
        if (policy == null) {
            policy = DensePolicy.load(Path.of(file));
            snapshots.put(file, policy);
            if (snapshots.size() > 32) {
                snapshots.remove(snapshots.keySet().iterator().next());
            }
        }
        return policy;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 1 || length > 65536) {
            throw new IOException("Invalid policy name length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Incomplete policy name");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeFrames(DataOutputStream output, List<Callable<Frame>> jobs, boolean observations)
        throws Exception {
        for (Future<Frame> result : workers.invokeAll(jobs)) {
            result.get().write(output, observations);
        }
    }

    private record Frame(int outcome, int mask, int turns, int rolls, int zeroCompleted, int oneCompleted,
                         float zeroProgress, float oneProgress, float[] observation) {
        static Frame from(Simulation game, boolean includeObservation) {
            return new Frame(game.done() ? game.winner() : -2, game.mask(), game.turns(), game.rolls(),
                game.completed(0), game.completed(1), game.progress(0), game.progress(1),
                includeObservation && !game.done() ? game.observation() : TERMINAL);
        }

        void write(DataOutputStream output, boolean includeObservation) throws IOException {
            output.writeByte(outcome);
            output.writeByte(mask);
            output.writeInt(turns);
            output.writeInt(rolls);
            output.writeByte(zeroCompleted);
            output.writeByte(oneCompleted);
            output.writeFloat(zeroProgress);
            output.writeFloat(oneProgress);
            if (includeObservation) {
                for (float value : observation) {
                    output.writeFloat(value);
                }
            }
        }
    }
}
