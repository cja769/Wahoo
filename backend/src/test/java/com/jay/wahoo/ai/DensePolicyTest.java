package com.jay.wahoo.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class DensePolicyTest {
    @TempDir Path directory;

    @Test
    void validatesFormatAndSelectsTheBestLegalAction() throws Exception {
        Path actor = directory.resolve("policy.wah");
        writePolicy(actor, Decision.SCHEMA, false);
        DensePolicy policy = DensePolicy.load(actor);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(actor))),
            policy.sha256());
        float[] input = new float[Decision.SIZE];
        assertArrayEquals(new float[] {0, 1, 2, 3}, policy.logits(input));
        assertEquals(3, policy.choose(input, 15));
        assertEquals(1, policy.choose(input, 3));
        assertThrows(IllegalArgumentException.class, () -> policy.choose(input, 0));

        writePolicy(actor, Decision.SCHEMA + 1, false);
        assertThrows(IOException.class, () -> DensePolicy.load(actor));
        writePolicy(actor, Decision.SCHEMA, true);
        assertThrows(IOException.class, () -> DensePolicy.load(actor));
        Files.write(actor, new byte[0]);
        assertThrows(IOException.class, () -> DensePolicy.load(actor));
    }

    @Test
    void sharedActorScoresThePlayingColorAndMasksIllegalActions() throws Exception {
        Path actor = directory.resolve("shared.wah");
        try (var out = new DataOutputStream(Files.newOutputStream(actor))) {
            out.writeInt(0x57414832);
            out.writeInt(Decision.SCHEMA);
            out.writeInt(Decision.SIZE);
            writeLayer(out, 5, 16, 0);   // Encode the first feature of each marble.
            writeLayer(out, 71, 48, -1);
            writeLayer(out, 73, 48, 48); // Score the selected marble's first embedding feature.
            writeLayer(out, 48, 1, 0);
        }
        DensePolicy model = DensePolicy.load(actor);
        float[] board = new float[Decision.SIZE];
        board[4] = 0.9f;
        board[9] = 0.5f;
        board[4 + 2 * 21 + 2 * 5] = 0.8f;
        assertEquals(0, model.choose(board, 0b1111));
        assertEquals(1, model.choose(board, 0b1110));
        board[2] = 1;
        assertEquals(2, model.choose(board, 0b1111));
        Files.write(actor, new byte[] {1}, StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> DensePolicy.load(actor));
    }

    private static void writeLayer(DataOutputStream out, int inputs, int outputs, int firstWeight)
        throws IOException {
        out.writeInt(outputs);
        for (int i = 0; i < inputs * outputs; i++) {
            out.writeFloat(i == firstWeight ? 1 : 0);
        }
        for (int i = 0; i < outputs; i++) {
            out.writeFloat(0);
        }
    }

    private static void writePolicy(Path file, int schema, boolean nonFinite) throws IOException {
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x57414831);
            out.writeInt(schema);
            out.writeInt(Decision.SIZE);
            out.writeInt(1);
            for (int i = 0; i < Decision.SIZE; i++) {
                out.writeFloat(0);
            }
            out.writeFloat(0);
            out.writeInt(1);
            out.writeFloat(0);
            out.writeFloat(0);
            out.writeInt(4);
            for (int i = 0; i < 4; i++) {
                out.writeFloat(0);
            }
            for (int i = 0; i < 4; i++) {
                out.writeFloat(nonFinite ? Float.NaN : i);
            }
        }
    }
}
