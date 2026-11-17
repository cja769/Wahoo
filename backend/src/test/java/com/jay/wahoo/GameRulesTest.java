package com.jay.wahoo;

import com.jay.wahoo.ai.Decision;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesTest {
    @Test
    void rolloutCopiesThePendingAssistedMoveAndUsesIndependentDice() {
        AtomicInteger diceCalls = new AtomicInteger();
        Game source = Game.headless(seat -> { diceCalls.incrementAndGet(); return 6; }, 0, 800);
        source.joinGame("Human", "p0");
        for (int i = 0; i < 4; i++) {
            place(source, 0, i, GameBoard.SAFE_START + 1 + i);
        }
        Marble first = place(source, 2, 0, 5);
        Marble furthest = place(source, 2, 1, 30);
        Marble target = place(source, 1, 0, 25);
        rollAndPlay(source, 0); // The assisted marble captures on the first six.
        source.advanceRoll();
        assertEquals(GameBoard.HOME, target.position());
        assertEquals(1, source.sixCount());
        assertTrue(source.awaitingHumanMove);
        Game branch = source.rolloutGame(seat -> 6);
        branch.restoreDecision(source);
        assertEquals(2, branch.marbleOwner().seat());
        assertEquals(source.actionMask(), branch.actionMask());
        assertArrayEquals(Decision.encode(source), Decision.encode(branch));
        assertFalse(branch.currentPlayer.isHuman());
        branch.playAction(0);
        branch.advanceRoll();
        assertTrue(branch.rolledThreeSixes);
        assertEquals(GameBoard.HOME, branch.players[2].marbles().get(1).position());
        assertEquals(11, first.position());
        assertEquals(30, furthest.position());
        assertEquals(2, diceCalls.get());
        branch.restoreDecision(source);
        assertArrayEquals(Decision.encode(source), Decision.encode(branch));
        assertEquals(1, branch.sixCount());
        assertEquals(2, branch.rollCount());
    }

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
    void legalSixRequiresAMoveAndBlockedSixStillRerollsAndCountsTowardPenalty() {
        Game game = game(6, 6, 6, 6, 6, 6, 5, 3);
        for (int i = 0; i < 3; i++) {
            place(game, 0, i, GameBoard.SAFE_START + i);
        }
        Marble track = place(game, 0, 3, 44);

        game.advanceRoll();
        assertTrue(game.awaitingComputerMove);
        game.advanceRoll();
        assertEquals(1, game.rollCount());
        assertThrows(IllegalArgumentException.class, () -> game.playAction(0));
        assertEquals(0, game.sixCount());
        game.moveComputer();
        assertEquals(50, track.position());

        Game.GameState blocked = game.next();
        assertFalse(blocked.awaitingHumanMove);
        assertEquals("p0", blocked.currentPlayerId);
        assertEquals(0, game.actionMask());
        assertEquals(2, game.sixCount());
        assertEquals(0, game.turnCount());
        assertTrue(game.next().penaltyApplied);
        assertEquals(GameBoard.HOME, track.position());

        rollAndPlay(game, 3);
        rollAndPlay(game, 3);
        assertTrue(game.next().penaltyApplied);
        assertEquals(GameBoard.HOME, track.position());
        game.advanceRoll();
        assertEquals(1, game.turnCount());
        game.advanceRoll();
        assertEquals(1, game.currentPlayer.seat());
    }

    @Test
    void humanWithoutAnEligibleTrackMarbleStillRerollsAfterThreeBlockedSixes() {
        Game game = game(6, 6, 6, 6, 4);
        game.joinGame("Human", "p0");
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, GameBoard.SAFE_START + i);
        }
        for (int count = 1; count <= 2; count++) {
            Game.GameState state = game.next();
            assertFalse(state.awaitingHumanMove);
            assertFalse(game.awaitingComputerMove);
            assertEquals("p0", state.currentPlayerId);
            assertEquals(count, game.sixCount());
            assertEquals(0, game.turnCount());
        }
        Game.GameState penalty = game.next();
        assertTrue(penalty.rolledThreeSixes);
        assertFalse(penalty.penaltyApplied);
        assertEquals(0, game.sixCount());
        for (int i = 0; i < 4; i++) {
            assertEquals(GameBoard.SAFE_START + i, game.players[0].marbles().get(i).position());
        }
        game.advanceRoll();
        assertEquals(0, game.turnCount());
        assertEquals(1, game.sixCount());
        game.advanceRoll();
        assertEquals(1, game.turnCount());
        assertEquals(0, game.sixCount());
    }

    @Test
    void humanSixWithLegalMovesCannotRollAgainBeforePlaying() {
        Game game = game(6, 4);
        game.joinGame("Human", "p0");
        game.advanceRoll();
        assertTrue(game.awaitingHumanMove);
        game.advanceRoll();
        assertEquals(1, game.rollCount());
        assertThrows(IllegalArgumentException.class, () -> game.playAction(-1));
        game.moveHuman("p0", 0);
        assertEquals(1, game.sixCount());
        game.advanceRoll();
        assertEquals(0, game.currentPlayer.seat());
        assertTrue(game.awaitingHumanMove);
        game.moveHuman("p0", 0);
        assertEquals(1, game.turnCount());
        assertEquals(0, game.sixCount());
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
    void safeLaneHasFiveSlotsAndRequiresTheFinalFourToBePacked() {
        Game game = game(1);
        Player player = game.players[0];
        assertEquals(5, player.safeBoard().getArea().length);
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, GameBoard.SAFE_START + i);
        }
        Marble end = player.marbles().get(3);
        assertFalse(player.safeBoard().isComplete());
        assertNull(game.board().legalMove(end, 2));
        for (int i = 3; i >= 0; i--) {
            game.board().apply(game.board().legalMove(player.marbles().get(i), 1));
        }
        assertTrue(player.safeBoard().isComplete());
        assertEquals(GameBoard.FINISH, end.position());
        assertNull(game.board().legalMove(end, 1));
    }

    @Test
    void completedPlayerMovesTeammateAndTeamWinsWhenBothFinish() {
        Game game = game(1);
        game.joinGame("Human", "p0");
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, 53 + i);
            place(game, 2, i, i == 0 ? 52 : 53 + i);
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
    void teammatePenaltyUsesFurthestTrackMarbleWhileSafeMarblesStayProtected() {
        Game game = game(6, 6, 6);
        for (int i = 0; i < 4; i++) {
            place(game, 0, i, 53 + i);
        }
        Marble protectedMarble = place(game, 2, 0, GameBoard.FINISH);
        place(game, 2, 1, 5);
        Marble furthestTrack = place(game, 2, 2, 31);
        rollAndPlay(game, 1);
        rollAndPlay(game, 1);
        assertPenalty(game);
        assertEquals(GameBoard.FINISH, protectedMarble.position());
        assertEquals(GameBoard.HOME, furthestTrack.position());
        assertEquals(17, game.players[2].marbles().get(1).position());
        assertTrue(game.players[0].safeBoard().isComplete());
    }

    @Test
    void secondTeamCanWinWithAnExactSafeLaneMove() {
        Game game = Game.headless(ignored -> 1, 1, 800);
        for (int i = 0; i < 4; i++) {
            place(game, 1, i, GameBoard.SAFE_START + 1 + i);
            place(game, 3, i, GameBoard.SAFE_START + (i == 0 ? i : 1 + i));
        }
        game.advanceRoll();
        assertEquals(3, game.marbleOwner().seat());
        game.playAction(0);
        assertEquals(1, game.winningTeam());
        assertEquals(2, game.getWinningTeam().size());
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
        game.board().relocate(blocker, GameBoard.FINISH);
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
            assertEquals(2f / SafeBoard.LENGTH, actual[actions + 18 + 3]);
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
