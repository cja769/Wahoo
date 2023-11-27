package com.jay.wahoo;

import lombok.*;

import java.util.Optional;

public interface Board {
    TestMove testMove(Marble m, int move, int priorBoardSpots);
    Move move(Marble m, int move);

    @Getter
    @Builder
    class Move {
        private MoveResult moveResult;
        @Setter
        private int spotsMoved;
        private Marble killedMarble;
        public void execute(Player p, Marble marbleMoved) {
            marbleMoved.incrementDistance(spotsMoved);
            moveResult.move.action(p, killedMarble, spotsMoved);
        }
    }
    @FunctionalInterface
    interface MoveAction {
        void action(Player player, Marble killed, int spotsMoved);
    }

    @RequiredArgsConstructor
    enum MoveResult {
        TEAM_KILL((p, killed, moved) -> {
            p.addTeammateKill();
            p.incrementFitness(moved - killed.distance());
            killed.player().decrementFitness(killed.distance());
            killed.resetDistance();
        }),
        OPPONENT_KILL((p, killed, moved) -> {
            p.addOpponentKill();
            p.incrementFitness(moved + killed.distance());
            killed.player().decrementFitness(killed.distance());
            killed.resetDistance();
        }),
        NONE((p, killed, moved) -> p.incrementFitness(moved));

        final MoveAction move;
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
