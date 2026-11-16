package com.jay.wahoo;

public class PlayerBoard implements ContainingBoard {
    static final int LENGTH = 14;
    private final Marble[] area = new Marble[LENGTH];

    @Override
    public Marble[] getArea() {
        return area;
    }
}
