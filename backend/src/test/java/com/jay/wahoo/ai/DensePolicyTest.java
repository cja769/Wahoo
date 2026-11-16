package com.jay.wahoo.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
