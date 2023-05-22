package com.jay.wahoo;

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
        for (int i = 2; i < area.length; i++) {
            if (area[i] == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean testMove(Marble m, int move) {
        Integer position = findMarblePosition(m)
            .orElse(-1);
        for (int i = 0; i < move; i++) {
            position++;
            if (position >= area.length) {
                return false;
            }
            Marble marbleAtLocation = area[position];
            if (marbleAtLocation != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void move(Marble m, int move) {
        Integer startingPos = findMarblePosition(m)
            .orElse(-1);
        if (startingPos > -1) {
            area[startingPos] = null;
        }
        area[startingPos + move] = m;
    }

    @Override
    public int getMarblePositionOnTable(Marble marble) {
        return findMarblePosition(marble)
            .orElseThrow(() -> {
                return new IllegalArgumentException("Marble is not on board");
            });
    }

    @Override
    public Marble[] getArea() {
        return area;
    }

}
