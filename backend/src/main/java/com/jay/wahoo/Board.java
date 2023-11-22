package com.jay.wahoo;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.function.Consumer;

public interface Board {
    TestMove testMove(Marble m, int move, int priorBoardSpots);
    MoveResult move(Marble m, int move);

    @RequiredArgsConstructor
    enum MoveResult {
        TEAM_KILL(Player::addTeammateKill),
        OPPONENT_KILL(Player::addOpponentKill),
        NONE(p -> {});

        final Consumer<Player> resultFunction;
     }

    @AllArgsConstructor
    class TestMove {
        public boolean isMovable;
        public Optional<Marble> marbleKilled;
        public boolean isOnLeftOpponentStart;
        public boolean isOnRightOpponentStart;
        public boolean isOnTeammateStart;
        public int newLocation;
        public int safeAreaLocation;
        public Marble marble;

        TestMove(int newLocation, Marble marble) {
            this.newLocation = newLocation;
            this.marbleKilled = Optional.empty();
            this.safeAreaLocation = -1;
            this.marble = marble;
        }

        TestMove(int newLocation, int safeAreaLocation, Marble marble) {
            this(newLocation, marble);
            this.safeAreaLocation = safeAreaLocation;
        }

    }
}
