package com.jay.wahoo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PlayerBoard implements ContainingBoard {

    private PlayerBoard nextPlayerBoard;
    private SafeBoard nextSafeBoard;
    private Player player;
    private final int NUM_SPOTS_BEFORE_SAFE = 10;
    private final Marble[] area = new Marble[14];

    public void setNextPlayerBoard(PlayerBoard nextPlayerBoard) {
        this.nextPlayerBoard = nextPlayerBoard;
    }

    public void setNextSafeBoard(SafeBoard safeBoard) {
        this.nextSafeBoard = safeBoard;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    @Override
    public TestMove testMove(Marble m, int move, int priorBoardSpots) {
        int position = findMarblePosition(m).orElse(-1);
        int adjustedPriorBoardSpots = priorBoardSpots - position;
        for (int i = move; i > 0; i--) {
            position++;
            if (position >= NUM_SPOTS_BEFORE_SAFE && m.player().equals(nextSafeBoard.getPlayer())) {
                return nextSafeBoard.testMove(m, i, adjustedPriorBoardSpots + position - 1);
            }
            if (position >= area.length) {
                return nextPlayerBoard.testMove(m, i, adjustedPriorBoardSpots + position - 1);
            }
            if (area[position] != null && area[position].player().equals(m.player())) {
                return new TestMove(m.player().startBoard().getMarblePositionOnTable(m), m);
            }
        }
        boolean isOnStart = position == 0;
        boolean isOnLeftOpponentStart = nextPlayerBoard.player.equals(m.player()) && isOnStart;
        boolean isOnRightOpponentStart = nextPlayerBoard.player.equals(m.player().partner()) && isOnStart;
        boolean isOnTeammateStart = this.player.equals(m.player().partner()) && isOnStart;
        int location = adjustedPriorBoardSpots + position;
        return new TestMove(true, Optional.ofNullable(area[position]), isOnLeftOpponentStart, isOnRightOpponentStart, isOnTeammateStart, location, -1, m);
    }

    @Override
    public Move move(Marble m, int move) {
        int position = findMarblePosition(m).orElse(-1);
        if (position > -1) {
            area[position] = null;
        }
        if ((position + move) >= NUM_SPOTS_BEFORE_SAFE && m.player().equals(nextSafeBoard.getPlayer())) {
            Move result = nextSafeBoard.move(m, (position + move) - (NUM_SPOTS_BEFORE_SAFE - 1));
            result.setSpotsMoved(move);
            return result;
        } else if ((position + move) >= area.length) {
            Move result = nextPlayerBoard.move(m, (position + move) - (area.length - 1));
            result.setSpotsMoved(move);
            return result;
        }
        Marble existing = area[position + move];
        area[position + move] = m;
        MoveResult moveResult = MoveResult.NONE;
        if (existing != null) {
            existing.player().startBoard().addToBoard(existing);
            moveResult = m.isSameTeam(existing) ? MoveResult.TEAM_KILL : MoveResult.OPPONENT_KILL;
        }
        return Move.builder()
            .moveResult(moveResult)
            .spotsMoved(move)
            .killedMarble(existing)
            .build();
    }

    public List<Marble> getFlattenedBoard() {
        List<Marble> board = areaToList();
        board.addAll(nextPlayerBoard.getFlattenedBoardInternal(player));
        return board;
    }

    private List<Marble> areaToList() {
        return new ArrayList<>(Arrays.asList(area));
    }

    private List<Marble> getFlattenedBoardInternal(Player player) {
        if (this.player == player) {
            return new ArrayList<>();
        }
        List<Marble> board = areaToList();
        board.addAll(nextPlayerBoard.getFlattenedBoardInternal(player));
        return board;
    }

    public Integer restFurthestMarble(Player p) {
        if (!nextSafeBoard.getPlayer().equals(p)) {
            Integer marbleDistance = nextPlayerBoard.restFurthestMarble(p);
            if (marbleDistance != null) {
                return marbleDistance;
            }
        }
        for (int i = 0; i < area.length; i++) {
            Marble m = area[i];
            if (m != null && m.player().equals(p)) {
                p.startBoard().addToBoard(area[i]);
                area[i] = null;
                int distance = m.distance();
                m.resetDistance();
                return distance;
            }
        }
        return null;
    }

    @Override
    public int getMarblePositionOnTable(Marble marble) {
        return findMarblePosition(marble)
            .orElseGet(() -> {
                if (nextSafeBoard.getPlayer().equals(marble.player())) {
                    return nextSafeBoard.getMarblePositionOnTable(marble) + (NUM_SPOTS_BEFORE_SAFE - 1);
                }
                return nextPlayerBoard.getMarblePositionOnTable(marble) + (area.length - 1);
            });
    }

    @Override
    public Marble[] getArea() {
        return area;
    }

}
