package com.jay.wahoo.ai;

import com.jay.wahoo.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LookaheadPolicyTest {
    @TempDir Path directory;

    @Test
    void ignoresEquivalentHomePiecesAndLeavesSourceDiceAndBoardUntouched() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var game = Game.headless(seat -> calls.getAndIncrement() < 2 ? 6 : 1, 0, 800);
        DensePolicy actor = actor();
        var policy = new LookaheadPolicy(actor, obs -> obs[3], 4, 8, 0.5f, 0, Policy.HEURISTIC);
        game.advanceRoll();
        assertFalse(policy.compare(game, Decision.encode(game)).searched());
        game.playAction(0);
        game.advanceRoll();
        game.playAction(0);
        game.advanceRoll();
        float[] original = Decision.encode(game);
        int mask = game.actionMask();
        var decision = policy.compare(game, original);
        assertTrue(decision.searched());
        assertNotEquals(0, mask & (1 << decision.move()));
        assertEquals(decision, policy.compare(game, original));
        assertEquals(3, calls.get());
        assertArrayEquals(original, Decision.encode(game));
    }

    @Test
    void matchedDiceReplayEachSeatsOwnSequence() {
        var dice = new LookaheadPolicy.MatchedDice(5);
        dice.sample(new SplittableRandom(51));
        int first = dice.roll(0);
        int other = dice.roll(2);
        int second = dice.roll(0);
        dice.reset();
        assertEquals(other, dice.roll(2));
        assertEquals(first, dice.roll(0));
        assertEquals(second, dice.roll(0));
    }

    @Test
    void rejectsValueHeadFromDifferentActor() throws Exception {
        DensePolicy actor = actor();
        Path file = directory.resolve("policy.wav");
        try (var out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(0x57415631);
            out.write(HexFormat.of().parseHex(actor.sha256()));
            writeLayer(out, 48, 48, 0);
            writeLayer(out, 48, 1, 0.25f);
        }
        assertEquals(0.25f, actor.loadValue(file).estimate(new float[Decision.SIZE]));
        byte[] data = Files.readAllBytes(file);
        data[4] ^= 1;
        Files.write(file, data);
        assertThrows(java.io.IOException.class, () -> actor.loadValue(file));
    }

    private DensePolicy actor() throws Exception {
        Path path = directory.resolve("actor.wah");
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x57414832);
            out.writeInt(Decision.SCHEMA);
            out.writeInt(Decision.SIZE);
            writeLayer(out, 5, 16, 0);
            writeLayer(out, 71, 48, 0);
            writeLayer(out, 73, 48, 0);
            writeLayer(out, 48, 1, 0);
        }
        return DensePolicy.load(path);
    }

    private static void writeLayer(DataOutputStream out, int inputs, int outputs, float bias) throws Exception {
        out.writeInt(outputs);
        for (int i = 0; i < inputs * outputs; i++) out.writeFloat(0);
        for (int i = 0; i < outputs; i++) out.writeFloat(bias);
    }
}
