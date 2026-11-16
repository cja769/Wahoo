package com.jay.wahoo;

public class StartBoard implements ContainingBoard {
    private final Marble[] area = new Marble[4];

    void add(Marble marble) {
        for (int i = 0; i < area.length; i++) {
            if (area[i] == null) {
                area[i] = marble;
                return;
            }
        }
        throw new IllegalStateException("Home is full");
    }

    void remove(Marble marble) {
        for (int i = 0; i < area.length; i++) {
            if (area[i] == marble) {
                area[i] = null;
                return;
            }
        }
        throw new IllegalStateException("Marble is missing from home");
    }

    @Override
    public Marble[] getArea() {
        return area;
    }
}
