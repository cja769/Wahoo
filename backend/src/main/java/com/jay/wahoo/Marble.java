package com.jay.wahoo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Marble {

    private final Player player;
    private final Integer identifier;
    private int distance;

    public void incrementDistance(int increment) {
        distance += increment;
    }

    public void resetDistance() {
        distance = 0;
    }

    public int distance() {
        return distance;
    }

    public Player player() {
        return player;
    }

    public Integer identifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof  Marble) {
            Marble other = (Marble) obj;
            return identifier().equals(other.identifier()) && player.equals(other.player());
        }
        return false;
    }

    public boolean isSameTeam(Marble m) {
        return player.isTeammate(m.player);
    }

}
