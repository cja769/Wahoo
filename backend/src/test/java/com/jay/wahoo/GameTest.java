package com.jay.wahoo;

import com.jay.wahoo.neat.Genome;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class GameTest {

    @Test
    public void getWinnerSort() {
        Player p1 = new Player(new Genome(), 1, null);
        Player p2 = new Player(new Genome(), 2, null);
        Player p3 = new Player(new Genome(), 3, null);
        addMoves(p1, 10, 0, 0);
        addMoves(p2, 8, 0, 2);
        addMoves(p3, 8, 1, 1);
        List<Player> expected = List.of(p1, p2, p3);
        List<Player> actual = Stream.of(p3, p2, p1)
            .sorted(Game.getWinnerSort())
            .toList();
        assertThat(actual).isEqualTo(expected);
    }

    private void addMoves(Player p, int correctMoves, int incorrectMoves, int noMoves) {
        addMoves(p, Player::addCorrectMove, correctMoves);
        addMoves(p, Player::addIncorrectMove, incorrectMoves);
        addMoves(p, Player::addNoMove, noMoves);
    }

    private void addMoves(Player p, Consumer<Player> consumer, int times) {
        for (int i = 0; i < times; i++) {
            consumer.accept(p);
        }
    }
}
