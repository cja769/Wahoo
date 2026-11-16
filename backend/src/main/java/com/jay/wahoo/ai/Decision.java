package com.jay.wahoo.ai;

import com.jay.wahoo.Game;
import com.jay.wahoo.GameBoard;
import com.jay.wahoo.GameBoard.LegalMove;
import com.jay.wahoo.Marble;
import com.jay.wahoo.Player;

public final class Decision {
    public static final int SCHEMA = 1;
    public static final int SIZE = 123;
    static final int ACTION_OFFSET = 87;
    static final int ACTION_FEATURES = 9;

    private Decision() {}

    public static float[] encode(Game game) {
        if (game.legalCount() == 0) {
            throw new IllegalStateException("Observation requires a legal decision");
        }
        Player actor = game.currentPlayer;
        int[] threats = threats(game, actor.seat());
        float[] observation = new float[SIZE];
        observation[0] = game.diceRoll / 6f;
        observation[1] = game.sixCount() / 2f;
        observation[2] = game.marbleOwner() == actor ? 0 : 1;
        int index = 3;
        for (int offset = 0; offset < 4; offset++) {
            Player player = game.players[(actor.seat() + offset) % 4];
            observation[index++] = player.safeBoard().getNumberOfMarblesComplete() / 4f;
            for (Marble marble : player.marbles()) {
                int position = marble.position();
                observation[index++] = position == GameBoard.HOME ? 1 : 0;
                observation[index++] = trackPosition(actor, player, position);
                observation[index++] = safeSlot(position);
                observation[index++] = progress(position);
                observation[index++] = danger(threats, player, position);
            }
        }
        for (int action = 0; action < 4; action++) {
            LegalMove move = game.legalMove(action);
            if (move != null) {
                Player owner = move.marble().player();
                int position = move.destination();
                Marble captured = move.captured();
                boolean opponent = captured != null && captured.player().team() != actor.team();
                observation[index] = 1;
                observation[index + 1] = move.marble().position() == GameBoard.HOME ? 1 : 0;
                observation[index + 2] = trackPosition(actor, owner, position);
                observation[index + 3] = safeSlot(position);
                observation[index + 4] = progress(position);
                observation[index + 5] = opponent ? 1 : 0;
                observation[index + 6] = opponent ? progress(captured.position()) : 0;
                observation[index + 7] = captured != null && !opponent ? 1 : 0;
                // Opposing colors may pass this marble. Their captured marble will be home.
                observation[index + 8] = destinationDanger(game, actor, move);
            }
            index += ACTION_FEATURES;
        }
        return observation;
    }

    private static float destinationDanger(Game game, Player actor, LegalMove move) {
        if (move.destination() >= GameBoard.SAFE_START) {
            return 0;
        }
        int track = GameBoard.trackIndex(move.marble().player(), move.destination());
        int outcomes = 0;
        for (int offset = 1; offset < 4; offset += 2) {
            Player opponent = game.players[(actor.seat() + offset) % 4];
            Player owner = opponent.safeBoard().isComplete() ? opponent.partner() : opponent;
            int rolls = 0;
            int relativeTarget = (track - GameBoard.trackIndex(owner, 0) + GameBoard.TRACK_LENGTH)
                % GameBoard.TRACK_LENGTH;
            if (relativeTarget >= GameBoard.SAFE_START) {
                continue;
            }
            for (Marble marble : owner.marbles()) {
                int position = marble == move.captured() ? GameBoard.HOME : marble.position();
                if (position == GameBoard.HOME) {
                    if (relativeTarget == 0
                        && game.board().legalDestinationAfterCapture(marble, 1, move.captured()) == 0) {
                        rolls |= 1 | (1 << 5);
                    }
                } else {
                    int roll = relativeTarget - position;
                    if (roll >= 1 && roll <= 6
                        && game.board().legalDestinationAfterCapture(marble, roll, move.captured()) == relativeTarget) {
                        rolls |= 1 << (roll - 1);
                    }
                }
            }
            outcomes += Integer.bitCount(rolls);
        }
        return outcomes / 12f;
    }

    private static int[] threats(Game game, int seat) {
        int[] counts = new int[GameBoard.TRACK_LENGTH];
        for (int offset = 1; offset < 4; offset += 2) {
            Player opponent = game.players[(seat + offset) % 4];
            Player moving = opponent.safeBoard().isComplete() ? opponent.partner() : opponent;
            for (int roll = 1; roll <= 6; roll++) {
                long reached = 0;
                for (Marble marble : moving.marbles()) {
                    int position = game.board().legalDestination(marble, roll);
                    if (position >= 0 && position < GameBoard.SAFE_START) {
                        reached |= 1L << GameBoard.trackIndex(moving, position);
                    }
                }
                while (reached != 0) {
                    int target = Long.numberOfTrailingZeros(reached);
                    counts[target]++;
                    reached &= reached - 1;
                }
            }
        }
        return counts;
    }

    private static float danger(int[] threats, Player owner, int position) {
        return position >= 0 && position < GameBoard.SAFE_START
            ? threats[GameBoard.trackIndex(owner, position)] / 12f : 0;
    }

    private static float trackPosition(Player actor, Player owner, int position) {
        if (position < 0 || position >= GameBoard.SAFE_START) {
            return 0;
        }
        int relative = (GameBoard.trackIndex(owner, position) - GameBoard.trackIndex(actor, 0)
            + GameBoard.TRACK_LENGTH) % GameBoard.TRACK_LENGTH;
        return (relative + 1) / (float) GameBoard.TRACK_LENGTH;
    }

    private static float safeSlot(int position) {
        return position >= GameBoard.SAFE_START ? (position - GameBoard.SAFE_START + 1) / 6f : 0;
    }

    public static float progress(int position) {
        return (position + 1f) / (GameBoard.FINISH + 1);
    }
}
