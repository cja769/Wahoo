package com.jay.wahoo;

import java.util.Optional;

public class StartBoard implements ContainingBoard {

    private final Marble[] area = new Marble[4];

    @Override
    public TestMove testMove(Marble m, int move, int priorBoardSpots) {
        if (!(move == 1 || move == 6)) {
            return new TestMove(-1, m);
        }
        return m.player().playerBoard().testMove(m, 1, priorBoardSpots);
    }

    @Override
    public MoveResult move(Marble m, int move) {
        MoveResult result = m.player().playerBoard().move(m, 1);
        Integer marblePosition = findMarblePosition(m)
            .orElseThrow(() -> new IllegalArgumentException("Can't move marble out of start board because it's not there"));
        area[marblePosition] = null;
        return result;
    }

    public void addToBoard(Marble m) {
//        System.out.println("Marble " + m.player().identifier() + m.identifier() + " was sent home");
        for (int i = 0; i < area.length; i++) {
            if (area[i] == null) {
                area[i] = m;
                break;
            }
        }
    }

    @Override
    public int getMarblePositionOnTable(Marble marble) {
        Optional<Integer> marblePosition = findMarblePosition(marble);
        if (marblePosition.isPresent()) {
            return -1;
        }
        return marble.player().playerBoard().getMarblePositionOnTable(marble);
    }

    @Override
    public Marble[] getArea() {
        return area;
    }

    public int getNumberOfMarbles() {
        int numHome = 0;
        for (int i = 0; i < area.length; i++) {
            if (area[i] != null) {
                numHome++;
            }
        }
        return numHome;
    }
}
