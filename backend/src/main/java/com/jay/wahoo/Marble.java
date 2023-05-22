package com.jay.wahoo;

public record Marble(Player player, Integer identifier) {

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof  Marble) {
            Marble other = (Marble) obj;
            return identifier().equals(other.identifier()) && player.equals(other.player());
        }
        return false;
    }
}
