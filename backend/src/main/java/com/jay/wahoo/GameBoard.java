package com.jay.wahoo;

public final class GameBoard {
    public static final int HOME = -1;
    public static final int TRACK_LENGTH = 4 * PlayerBoard.LENGTH;
    // Enter the safe lane after slot 9 of the preceding player's track.
    public static final int SAFE_START = TRACK_LENGTH - 4;
    public static final int FINISH = SAFE_START + SafeBoard.LENGTH - 1;
    private static final int ILLEGAL = -2;

    private final Player[] players;

    public GameBoard(Player[] players) {
        this.players = players;
    }

    public static int trackIndex(Player owner, int position) {
        return (owner.seat() * PlayerBoard.LENGTH + position) % TRACK_LENGTH;
    }

    public record LegalMove(Marble marble, int destination, Marble captured) {}

    public LegalMove legalMove(Marble marble, int roll) {
        int destination = legalDestination(marble, roll);
        if (destination == ILLEGAL) {
            return null;
        }
        Marble captured = destination < SAFE_START ? getTrack(marble.player(), destination) : null;
        return new LegalMove(marble, destination, captured);
    }

    // Also used for hypothetical next-roll threats. No state changes or allocations.
    public int legalDestination(Marble marble, int roll) {
        return legalDestinationAfterCapture(marble, roll, null);
    }

    public int legalDestinationAfterCapture(Marble marble, int roll, Marble captured) {
        Player owner = marble.player();
        int position = marble == captured ? HOME : marble.position();
        if (position == HOME && roll != 1 && roll != 6) {
            return ILLEGAL;
        }
        int destination = position == HOME ? 0 : position + roll;
        if (destination > FINISH) {
            return ILLEGAL;
        }
        for (int next = position + 1; next <= destination; next++) {
            if (next >= SAFE_START) {
                if (owner.safeBoard().getArea()[next - SAFE_START] != null) {
                    return ILLEGAL;
                }
            } else {
                Marble occupant = getTrack(owner, next);
                if (occupant != null && occupant != captured && occupant.player() == owner) {
                    return ILLEGAL;
                }
            }
        }
        return destination;
    }

    void apply(LegalMove move) {
        if (move.captured() != null) {
            relocate(move.captured(), HOME);
        }
        relocate(move.marble(), move.destination());
    }

    boolean sendFurthestTrackMarbleHome(Player owner) {
        Marble furthest = null;
        for (Marble marble : owner.marbles()) {
            int position = marble.position();
            if (position > HOME && position < SAFE_START
                && (furthest == null || position > furthest.position())) {
                furthest = marble;
            }
        }
        if (furthest == null) {
            return false;
        }
        relocate(furthest, HOME);
        return true;
    }

    private Marble getTrack(Player owner, int position) {
        int index = trackIndex(owner, position);
        return players[index / PlayerBoard.LENGTH].playerBoard().getArea()[index % PlayerBoard.LENGTH];
    }

    void relocate(Marble marble, int destination) {
        Player owner = marble.player();
        int position = marble.position();
        if (position == HOME) {
            owner.startBoard().remove(marble);
        } else {
            setBoardSlot(owner, position, null);
        }
        if (destination == HOME) {
            owner.startBoard().add(marble);
        } else {
            setBoardSlot(owner, destination, marble);
        }
        marble.setPosition(destination);
    }

    private void setBoardSlot(Player owner, int position, Marble value) {
        if (position >= SAFE_START) {
            owner.safeBoard().getArea()[position - SAFE_START] = value;
        } else {
            int index = trackIndex(owner, position);
            players[index / PlayerBoard.LENGTH].playerBoard().getArea()[index % PlayerBoard.LENGTH] = value;
        }
    }
}
