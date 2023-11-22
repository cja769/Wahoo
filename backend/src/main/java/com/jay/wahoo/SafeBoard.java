package com.jay.wahoo;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class SafeBoard implements ContainingBoard {

    private final Marble[] area = new Marble[6];
    private final Player player;

    public SafeBoard(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isComplete() {
        return getNumberOfMarblesComplete() == 4;
    }

    public int getNumberOfMarblesComplete() {
        int numComplete = 0;
        for (int i = area.length - 1; i > 1; i--) {
            if (area[i] != null) {
                numComplete++;
            } else {
                break;
            }
        }
        return numComplete;
    }

    public long getNumberOfMarbles() {
        return Arrays.stream(area)
            .filter(Objects::nonNull)
            .count();
    }

    @Override
    public TestMove testMove(Marble m, int move, int priorBoardSpots) {
        Integer position = findMarblePosition(m)
            .orElse(-1);
        for (int i = 0; i < move; i++) {
            position++;
            if (position >= area.length) {
                return new TestMove(52, position, m);
            }
            Marble marbleAtLocation = area[position];
            if (marbleAtLocation != null) {
                return new TestMove(52, position, m);
            }
        }
        return new TestMove(true, Optional.empty(), false, false, false, 52, position, m);
    }

    @Override
    public MoveResult move(Marble m, int move) {
        Integer startingPos = findMarblePosition(m)
            .orElse(-1);
        if (startingPos > -1) {
            area[startingPos] = null;
        }
        if ((startingPos + move) >= area.length) {
            throw new IllegalArgumentException("This move is not legal");
        }
        area[startingPos + move] = m;
        return MoveResult.NONE;
    }

    @Override
    public int getMarblePositionOnTable(Marble marble) {
        return findMarblePosition(marble)
            .orElseThrow(() -> new IllegalArgumentException("Marble is not on board"));
    }

    @Override
    public Marble[] getArea() {
        return area;
    }

}
