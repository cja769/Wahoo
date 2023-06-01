package com.jay.wahoo;

import java.util.ArrayList;
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
        Integer position = findMarblePosition(m).orElse(-1);
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
    public void move(Marble m, int move) {
        Integer position = findMarblePosition(m).orElse(-1);
        if (position > -1) {
            area[position] = null;
        }
        if ((position + move) >= NUM_SPOTS_BEFORE_SAFE && m.player().equals(nextSafeBoard.getPlayer())) {
            nextSafeBoard.move(m, (position + move) - (NUM_SPOTS_BEFORE_SAFE - 1));
        } else if ((position + move) >= area.length) {
            nextPlayerBoard.move(m, (position + move) - (area.length - 1));
        } else {
            Marble existing = area[position + move];
            if (existing != null) {
                existing.player().startBoard().addToBoard(existing);
            }
            area[position + move] = m;
        }

    }

    public List<Marble> getFlattenedBoard() {
        List<Marble> board = areaToList();
        board.addAll(nextPlayerBoard.getFlattenedBoardInternal(player));
        return board;
    }

    private List<Marble> areaToList() {
        List<Marble> board = new ArrayList<>();
        for (int i = 0 ; i < area.length; i++) {
            board.add(area[i]);
        }
        return board;
    }

    private List<Marble> getFlattenedBoardInternal(Player player) {
        if (this.player == player) {
            return new ArrayList<>();
        }
        List<Marble> board = areaToList();
        board.addAll(nextPlayerBoard.getFlattenedBoardInternal(player));
        return board;
    }

    public boolean restFurthestMarble(Player p) {
        Integer index = null;
        for (int i = 0; i < area.length; i++) {
            Marble m = area[i];
            if (m != null && m.player().equals(p)) {
                index = i;
            }
        }
        if (!nextSafeBoard.getPlayer().equals(p)) {
            if (nextPlayerBoard.restFurthestMarble(p)) {
                return true;
            }
        }
        if (index != null) {
            p.startBoard().addToBoard(area[index]);
            area[index] = null;
            return true;
        }
        return false;
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
