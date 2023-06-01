package com.jay.wahoo;

import com.jay.wahoo.Board.TestMove;

import java.util.List;
import java.util.stream.Stream;

public class GameBoard {
    private final List<ContainingBoard> allBoards;

    public GameBoard(List<Player> players) {
        this.allBoards = players.stream()
            .flatMap(p -> Stream.of(p.safeBoard(), p.startBoard(), p.playerBoard()))
            .toList();
    }

    public TestMove testMove(Marble m, int move) {
        return findBoardWithMarble(m).testMove(m, move, m.player().startBoard().getMarblePositionOnTable(m));
    }

    public void move(Marble m, int move) {
        findBoardWithMarble(m).move(m, move);
    }

    protected Board findBoardWithMarble(Marble m) {
        return allBoards.stream()
            .filter(b -> b.contains(m))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No board contains marble " + m));
    }

    public boolean resetFurthestMarble(Player p) {
        return p.playerBoard().restFurthestMarble(p);
    }

}
