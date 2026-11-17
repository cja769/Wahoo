package com.jay.wahoo.ai;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Inference for the dense and shared move-scoring actors exported by training. */
public final class DensePolicy implements Policy {
    private static final int DENSE_MAGIC = 0x57414831; // WAH1
    private static final int SHARED_MAGIC = 0x57414832; // WAH2
    private static final int PIECE_WIDTH = 16;
    private static final int CONTEXT_WIDTH = 48;
    private static final int MOVE_WIDTH = 48;
    private final Layer[] layers;
    private final String sha256;
    private final ThreadLocal<float[][]> activations;
    private final ThreadLocal<SharedBuffers> sharedBuffers;

    private DensePolicy(Layer[] layers, String sha256) {
        this.layers = layers;
        this.sha256 = sha256;
        sharedBuffers = layers.length == 4 ? ThreadLocal.withInitial(SharedBuffers::new) : null;
        activations = sharedBuffers != null ? null : ThreadLocal.withInitial(() -> {
            float[][] buffers = new float[layers.length][];
            for (int i = 0; i < layers.length; i++) {
                buffers[i] = new float[layers[i].outputs];
            }
            return buffers;
        });
    }

    public static DensePolicy load(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
        try (var input = new DataInputStream(new BufferedInputStream(
            new DigestInputStream(Files.newInputStream(path), digest)))) {
            int magic = input.readInt();
            if ((magic != DENSE_MAGIC && magic != SHARED_MAGIC) || input.readInt() != Decision.SCHEMA
                || input.readInt() != Decision.SIZE) {
                throw new IOException("Incompatible Wahoo policy " + path);
            }
            Layer[] layers;
            if (magic == SHARED_MAGIC) {
                layers = new Layer[] {
                    readLayer(input, 5, PIECE_WIDTH),
                    readLayer(input, 3 + 4 * (1 + PIECE_WIDTH), CONTEXT_WIDTH),
                    readLayer(input, CONTEXT_WIDTH + PIECE_WIDTH + Decision.ACTION_FEATURES, MOVE_WIDTH),
                    readLayer(input, MOVE_WIDTH, 1)
                };
            } else {
                layers = new Layer[3];
                int inputs = Decision.SIZE;
                for (int i = 0; i < layers.length; i++) {
                    layers[i] = readLayer(input, inputs, i == layers.length - 1 ? 4 : 0);
                    inputs = layers[i].outputs;
                }
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in Wahoo policy " + path);
            }
            return new DensePolicy(layers, HexFormat.of().formatHex(digest.digest()));
        }
    }

    public String sha256() {
        return sha256;
    }

    private static Layer readLayer(DataInputStream input, int inputs, int requiredWidth) throws IOException {
        int outputs = input.readInt();
        if (outputs < 1 || outputs > 512 || (requiredWidth > 0 && outputs != requiredWidth)) {
            throw new IOException("Invalid Wahoo policy layer width " + outputs);
        }
        return new Layer(inputs, outputs, readFloats(input, inputs * outputs), readFloats(input, outputs));
    }

    private static float[] readFloats(DataInputStream input, int length) throws IOException {
        float[] values = new float[length];
        for (int i = 0; i < length; i++) {
            values[i] = input.readFloat();
            if (!Float.isFinite(values[i])) {
                throw new IOException("Nonfinite Wahoo policy weight");
            }
        }
        return values;
    }

    private float[] evaluate(float[] observation) {
        if (sharedBuffers != null) {
            return evaluateShared(observation, sharedBuffers.get());
        }
        float[][] buffers = activations.get();
        float[] previous = observation;
        for (int i = 0; i < layers.length; i++) {
            layers[i].forward(previous, buffers[i], i != layers.length - 1);
            previous = buffers[i];
        }
        return previous;
    }

    private float[] evaluateShared(float[] observation, SharedBuffers buffers) {
        sharedContext(observation, buffers);
        System.arraycopy(buffers.context, 0, buffers.move, 0, CONTEXT_WIDTH);
        for (int action = 0; action < 4; action++) {
            System.arraycopy(buffers.owner[action], 0, buffers.move, CONTEXT_WIDTH, PIECE_WIDTH);
            System.arraycopy(observation, Decision.ACTION_OFFSET + action * Decision.ACTION_FEATURES,
                buffers.move, CONTEXT_WIDTH + PIECE_WIDTH, Decision.ACTION_FEATURES);
            layers[2].forward(buffers.move, buffers.hidden, true);
            layers[3].forward(buffers.hidden, buffers.score, false);
            buffers.logits[action] = buffers.score[0];
        }
        return buffers.logits;
    }

    private void sharedContext(float[] observation, SharedBuffers buffers) {
        int owner = observation[2] == 0 ? 0 : 2;
        System.arraycopy(observation, 0, buffers.board, 0, 3);
        for (int seat = 0; seat < 4; seat++) {
            int start = 3 + seat * 21;
            int contextStart = 3 + seat * (1 + PIECE_WIDTH);
            buffers.board[contextStart] = observation[start];
            Arrays.fill(buffers.pooled, 0);
            for (int piece = 0; piece < 4; piece++) {
                layers[0].forward(observation, start + 1 + piece * 5, buffers.piece, true);
                for (int feature = 0; feature < PIECE_WIDTH; feature++) {
                    buffers.pooled[feature] += buffers.piece[feature];
                }
                if (seat == owner) {
                    System.arraycopy(buffers.piece, 0, buffers.owner[piece], 0, PIECE_WIDTH);
                }
            }
            for (int feature = 0; feature < PIECE_WIDTH; feature++) {
                buffers.board[contextStart + 1 + feature] = (float) (buffers.pooled[feature] / 4);
            }
        }
        layers[1].forward(buffers.board, buffers.context, true);
    }

    /** Loads the value head trained with this exact shared move-scoring actor. */
    public PositionValue loadValue(Path path) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (sharedBuffers == null || input.readInt() != 0x57415631
                || !HexFormat.of().formatHex(input.readNBytes(32)).equals(sha256)) {
                throw new IOException("Value head does not match the shared actor: " + path);
            }
            Layer hidden = readLayer(input, CONTEXT_WIDTH, CONTEXT_WIDTH);
            Layer output = readLayer(input, CONTEXT_WIDTH, 1);
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in value head " + path);
            }
            return observation -> {
                SharedBuffers buffers = sharedBuffers.get();
                sharedContext(observation, buffers);
                hidden.forward(buffers.context, buffers.hidden, true);
                output.forward(buffers.hidden, buffers.score, false);
                return buffers.score[0];
            };
        }
    }

    @FunctionalInterface
    public interface PositionValue {
        float estimate(float[] observation);
    }

    private static final class SharedBuffers {
        final float[] board = new float[3 + 4 * (1 + PIECE_WIDTH)];
        final double[] pooled = new double[PIECE_WIDTH];
        final float[] piece = new float[PIECE_WIDTH];
        final float[][] owner = new float[4][PIECE_WIDTH];
        final float[] context = new float[CONTEXT_WIDTH];
        final float[] move = new float[CONTEXT_WIDTH + PIECE_WIDTH + Decision.ACTION_FEATURES];
        final float[] hidden = new float[MOVE_WIDTH];
        final float[] score = new float[1];
        final float[] logits = new float[4];
    }

    public float[] logits(float[] observation) {
        return evaluate(observation).clone();
    }

    @Override
    public int choose(float[] observation, int mask) {
        return Policy.bestLegal(evaluate(observation), mask);
    }

    private record Layer(int inputs, int outputs, float[] weights, float[] biases) {
        void forward(float[] input, float[] output, boolean tanh) {
            forward(input, 0, output, tanh);
        }

        void forward(float[] input, int start, float[] output, boolean tanh) {
            for (int row = 0; row < outputs; row++) {
                float sum = biases[row];
                int offset = row * inputs;
                for (int col = 0; col < inputs; col++) {
                    sum += input[start + col] * weights[offset + col];
                }
                output[row] = tanh ? (float) Math.tanh(sum) : sum;
            }
        }
    }
}
