package com.jay.wahoo;

import java.util.Optional;

public class StartBoard implements ContainingBoard {

    private final Marble[] area = new Marble[4];

    @Override
    public boolean testMove(Marble m, int move) {
        return (move == 1 || move == 6) && m.player().playerBoard().testMove(m, 1);
    }

    @Override
    public void move(Marble m, int move) {
        m.player().playerBoard().move(m, 1);
        Integer marblePosition = findMarblePosition(m)
            .orElseThrow(() -> new IllegalArgumentException("Can't move marble out of start board because it's not there"));
        area[marblePosition] = null;
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
