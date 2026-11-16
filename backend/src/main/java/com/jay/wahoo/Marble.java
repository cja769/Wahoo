package com.jay.wahoo;

public class Marble {
    private final Player player;
    private final int identifier;
    private int position = GameBoard.HOME;

    public Marble(Player player, int identifier) {
        this.player = player;
        this.identifier = identifier;
    }

    public Player player() {
        return player;
    }

    public int identifier() {
        return identifier;
    }

    // Distance from this marble's start, with safe slots following the track.
    public int position() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }
}
