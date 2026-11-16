package com.jay.wahoo;

public class SafeBoard implements ContainingBoard {
    public static final int LENGTH = 5;
    private static final int MARBLES_TO_FINISH = 4;
    private final Marble[] area = new Marble[LENGTH];

    public boolean isComplete() {
        return getNumberOfMarblesComplete() == MARBLES_TO_FINISH;
    }

    public int getNumberOfMarblesComplete() {
        int completed = 0;
        for (int i = area.length - 1; i >= LENGTH - MARBLES_TO_FINISH && area[i] != null; i--) {
            completed++;
        }
        return completed;
    }

    @Override
    public Marble[] getArea() {
        return area;
    }
}
