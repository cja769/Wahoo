package com.jay.wahoo;

import com.jay.wahoo.ai.Decision;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesTest {
    @Test
    void eachThirdSixResetsFurthestMarbleAndKeepsRolling() {
        Game game = game(6, 6, 6, 6, 6, 6, 1, 4);
        Marble first = place(game, 0, 0, 5);
        Marble furthest = place(game, 0, 1, 39);
        rollAndPlay(game, 0);
        rollAndPlay(game, 0);
        assertEquals(2, game.sixCount());

        assertPenalty(game);
        assertEquals(GameBoard.HOME, furthest.position());
        assertEquals(17, first.position());

        rollAndPlay(game, 0);
        rollAndPlay(game, 0);
        assertPenalty(game);
        assertEquals(GameBoard.HOME, first.position());

        rollAndPlay(game, 1);
        assertEquals(1, game.turnCount());
        game.advanceRoll();
        assertEquals(1, game.currentPlayer.seat());
        assertEquals(8, game.rollCount());
    }

    @Test
    void sixWithoutAnyLegalMoveEndsTurnAndClearsStreak() {
        Game game = game(6, 6, 6);
        for (int i = 0; i < 3; i++) {
            place(game, 0, i, 52 + i);
        }
        place(game, 0, 3, 44);
        rollAndPlay(game, 3); // Lands on 50, with its own safe marbles blocking the next 6.
        assertEquals(1, game.sixCount());
        game.advanceRoll();
        assertEquals(0, game.actionMask());
        assertEquals(0, game.sixCount());
        assertEquals(1, game.turnCount());
        game.advanceRoll();
        assertEquals(1, game.currentPlayer.seat());
        assertEquals(15, game.actionMask());
    }

    @Test
    void invalidChoiceCannotAdvanceOrRerollAPendingDecision() {
        Game game = game(2, 1);
        Marble marble = place(game, 0, 2, 3);
        game.joinGame("Human", "p0");
        Game.GameState before = game.next();
        assertTrue(before.awaitingHumanMove);
        assertEquals(4, game.actionMask());
        assertTrue(before.states.get(0).playerAreaPositions.get(0).isMovable);

        assertThrows(IllegalArgumentException.class, () -> game.moveHuman("p0", 0));
        assertThrows(IllegalArgumentException.class, () -> game.moveHuman("p2", 2));
        assertThrows(IllegalArgumentException.class, () -> game.playAction(-1));
        assertThrows(IllegalArgumentException.class, () -> game.playAction(4));
        Game.GameState pending = game.next();
        assertFalse(pending.diceRollUpdated);
        assertEquals(1, game.rollCount());
        assertEquals(0, game.turnCount());
        assertEquals(3, marble.position());

        game.moveHuman("p0", 2);
        assertEquals(5, marble.position());
        assertEquals(1, game.turnCount());
        assertFalse(game.awaitingHumanMove);
    }

    @Test
    void neverRollsOverPendingBotMove() {
        Game game = game(1, 4);
        game.advanceRoll();
        assertTrue(game.awaitingComputerMove);
        game.advanceRoll();
        assertEquals(15, game.actionMask());
        assertEquals(1, game.rollCount());
    }

    @Test
    void completedPlayerMovesTeammateAndTeamWinsWhenBothFinish() {
        Game game = game(1);
        game.joinGame("Human", "p0");
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, 54 + i);
            place(game, 2, i, i == 0 ? 53 : 54 + i);
        }
        game.advanceRoll();
        assertEquals(2, game.marbleOwner().seat());
        assertEquals(1, game.actionMask());
        assertEquals(1, Decision.encode(game)[2]);
        assertThrows(IllegalArgumentException.class, () -> game.moveHuman("p0", 0));
        game.moveHuman("p2", 0);
        assertTrue(game.isGameComplete());
        assertEquals(0, game.winningTeam());
        assertEquals(2, game.getWinningTeam().size());
        game.advanceRoll();
        assertEquals(1, game.rollCount());
    }

    @Test
    void penaltyUsesFurthestMarbleIncludingSafeLaneForAssistedPlay() {
        Game game = game(6, 6, 6);
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, 54 + i);
        }
        Marble partner = place(game, 2, 0, 56);
        place(game, 2, 1, 5);
        rollAndPlay(game, 1);
        rollAndPlay(game, 1);
        assertPenalty(game);
        assertEquals(GameBoard.HOME, partner.position());
        assertTrue(game.players[0].safeBoard().isComplete());
    }

    @Test
    void turnLimitIsUnfinishedAndDoesNotInventAWinner() {
        Game game = Game.headless(seat -> 3, 3, 1);
        game.advanceRoll();
        assertEquals(3, game.currentPlayer.seat());
        assertTrue(game.isGameComplete());
        assertEquals(-1, game.winningTeam());
        assertTrue(game.getWinningTeam().isEmpty());
    }

    @Test
    void crossesTrackSegmentsAndCapturesAnOpponentOnLanding() {
        Game game = game(3);
        Marble marble = place(game, 0, 0, 13);
        Marble jumped = place(game, 1, 0, 0);
        Marble captured = place(game, 1, 1, 2);
        game.advanceRoll();
        assertEquals(16, game.legalMove(0).destination());
        assertSame(captured, game.legalMove(0).captured());
        game.playAction(0);
        assertSame(jumped, game.players[1].playerBoard().getArea()[0]);
        assertSame(marble, game.players[1].playerBoard().getArea()[2]);
        assertEquals(0, jumped.position());
        assertEquals(GameBoard.HOME, captured.position());
    }

    @Test
    void jumpsOtherColorsButCannotPassItsOwnMarbleOrOvershootTheSafeLane() {
        Game game = game(6);
        Marble marble = place(game, 0, 0, 50);
        Marble blocker = place(game, 0, 1, 52);
        assertNull(game.board().legalMove(marble, 3));
        game.board().relocate(blocker, 57);
        assertEquals(52, game.board().legalMove(marble, 2).destination());
        assertNull(game.board().legalMove(blocker, 1));
        game.board().relocate(marble, 55);
        assertNull(game.board().legalMove(marble, 3));
    }

    @Test
    void actorRelativeEncodingRotatesSeatsAndUsesEngineDestinationsAndCaptureMask() {
        float[] expected = null;
        for (int seat = 0; seat < 4; seat++) {
            Game game = Game.headless(ignored -> 3, seat, 800);
            place(game, seat, 0, 13);
            place(game, seat, 2, 50);
            place(game, (seat + 1) % 4, 1, 2);
            place(game, (seat + 3) % 4, 0, 28);
            game.advanceRoll();
            float[] actual = Decision.encode(game);
            assertEquals(Decision.SIZE, actual.length);
            assertEquals(5, game.actionMask());
            for (float feature : actual) {
                assertTrue(feature >= 0 && feature <= 1);
            }
            if (expected != null) {
                assertArrayEquals(expected, actual);
            }
            int actions = 87;
            assertEquals(1, actual[actions]);
            assertEquals(17f / GameBoard.TRACK_LENGTH, actual[actions + 2]);
            assertEquals(1, actual[actions + 5]);
            assertEquals(1f / 12, actual[actions + 8]); // Other opponent can capture on a 2.
            assertEquals(0, actual[actions + 9]);
            assertEquals(2f / 6, actual[actions + 18 + 3]);
            expected = actual;
        }
    }

    private static Game game(int... rolls) {
        AtomicInteger index = new AtomicInteger();
        return Game.headless(seat -> rolls[index.getAndIncrement()], 0, 800);
    }

    private static Marble place(Game game, int seat, int index, int position) {
        Marble marble = game.players[seat].marbles().get(index);
        game.board().relocate(marble, position);
        return marble;
    }

    private static void rollAndPlay(Game game, int action) {
        game.advanceRoll();
        game.playAction(action);
    }

    private static void assertPenalty(Game game) {
        game.advanceRoll();
        assertTrue(game.rolledThreeSixes);
        assertEquals(0, game.sixCount());
        assertEquals(0, game.turnCount());
        assertEquals(0, game.actionMask());
        assertEquals(0, game.currentPlayer.seat());
    }
}
