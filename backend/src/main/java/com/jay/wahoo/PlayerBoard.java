package com.jay.wahoo;

public class PlayerBoard implements ContainingBoard {

    private PlayerBoard nextPlayerBoard;
    private SafeBoard nextSafeBoard;
    private final int NUM_SPOTS_BEFORE_SAFE = 10;
    private final Marble[] area = new Marble[14];

    public void setNextPlayerBoard(PlayerBoard nextPlayerBoard) {
        this.nextPlayerBoard = nextPlayerBoard;
    }

    public void setNextSafeBoard(SafeBoard safeBoard) {
        this.nextSafeBoard = safeBoard;
    }

    @Override
    public boolean testMove(Marble m, int move) {
        Integer position = findMarblePosition(m).orElse(-1);
        for (int i = move; i > 0; i--) {
            position++;
            if (position >= NUM_SPOTS_BEFORE_SAFE && m.player().equals(nextSafeBoard.getPlayer())) {
                return nextSafeBoard.testMove(m, i);
            }
            if (position >= area.length) {
                return nextPlayerBoard.testMove(m, i);
            }
            if (area[position] != null && area[position].player().equals(m.player())) {
                return false;
            }
        }
        return true;
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
                    return nextSafeBoard.getMarblePositionOnTable(marble) + (area.length - 1);
                }
                return nextPlayerBoard.getMarblePositionOnTable(marble) + (area.length - 1);
            });
    }

    @Override
    public Marble[] getArea() {
        return area;
    }

}
