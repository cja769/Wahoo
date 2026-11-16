package com.jay.wahoo.ai;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Inference-only actor using the exact Linear/Tanh layers exported by training. */
public final class DensePolicy implements Policy {
    private static final int MAGIC = 0x57414831; // WAH1
    private final Layer[] layers;
    private final ThreadLocal<float[][]> activations;

    private DensePolicy(Layer[] layers) {
        this.layers = layers;
        activations = ThreadLocal.withInitial(() -> {
            float[][] buffers = new float[layers.length][];
            for (int i = 0; i < layers.length; i++) {
                buffers[i] = new float[layers[i].outputs];
            }
            return buffers;
        });
    }

    public static DensePolicy load(Path path) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC || input.readInt() != Decision.SCHEMA
                || input.readInt() != Decision.SIZE) {
                throw new IOException("Incompatible Wahoo policy " + path);
            }
            Layer[] layers = new Layer[3];
            int inputs = Decision.SIZE;
            for (int i = 0; i < layers.length; i++) {
                int outputs = input.readInt();
                if (outputs < 1 || outputs > 512 || (i == layers.length - 1 && outputs != 4)) {
                    throw new IOException("Invalid Wahoo policy layer width " + outputs);
                }
                float[] weights = readFloats(input, inputs * outputs);
                float[] biases = readFloats(input, outputs);
                layers[i] = new Layer(inputs, outputs, weights, biases);
                inputs = outputs;
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in Wahoo policy " + path);
            }
            return new DensePolicy(layers);
        }
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
        float[][] buffers = activations.get();
        float[] previous = observation;
        for (int i = 0; i < layers.length; i++) {
            layers[i].forward(previous, buffers[i], i != layers.length - 1);
            previous = buffers[i];
        }
        return previous;
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
            for (int row = 0; row < outputs; row++) {
                float sum = biases[row];
                int offset = row * inputs;
                for (int col = 0; col < inputs; col++) {
                    sum += input[col] * weights[offset + col];
                }
                output[row] = tanh ? (float) Math.tanh(sum) : sum;
            }
        }
    }
}
