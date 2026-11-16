package com.jay.wahoo;

public class SafeBoard implements ContainingBoard {
    static final int LENGTH = 6;
    private final Marble[] area = new Marble[LENGTH];

    public boolean isComplete() {
        return getNumberOfMarblesComplete() == 4;
    }

    public int getNumberOfMarblesComplete() {
        int completed = 0;
        for (int i = area.length - 1; i >= 2 && area[i] != null; i--) {
            completed++;
        }
        return completed;
    }

    @Override
    public Marble[] getArea() {
        return area;
    }
}
