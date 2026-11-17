package com.jay.wahoo.ai;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;

public final class ValueProbe {
    public static void main(String[] args) throws Exception {
        var policy = DensePolicy.load(Path.of(args[0])).loadValue(Path.of(args[1]));
        try (var input = new DataInputStream(System.in); var output = new DataOutputStream(System.out)) {
            int count = input.readInt();
            float[] observation = new float[Decision.SIZE];
            for (int i = 0; i < count; i++) {
                for (int j = 0; j < observation.length; j++) observation[j] = input.readFloat();
                output.writeFloat(policy.estimate(observation));
            }
        }
    }
}
