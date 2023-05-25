package com.jay.wahoo;

import com.jay.wahoo.Game;
import com.jay.wahoo.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GameTest {

    @Test
    void test_getPlayerOrderForPlayer() {
        Player p1 = new Player(null, 1, "p1");
        Player p2 = new Player(null, 2, "p2");
        Player p3 = new Player(null, 3, "p3");
        Player p4 = new Player(null, 4, "p4");
        Player[] players = new Player[4];
        players[0] = p1;
        players[1] = p2;
        players[2] = p3;
        players[3] = p4;
        assertThat(Game.getPlayerOrderForPlayer(p1, players)).containsExactly(p1, p2, p3, p4);
        assertThat(Game.getPlayerOrderForPlayer(p2, players)).containsExactly(p2, p3, p4, p1);
        assertThat(Game.getPlayerOrderForPlayer(p3, players)).containsExactly(p3, p4, p1, p2);
        assertThat(Game.getPlayerOrderForPlayer(p4, players)).containsExactly(p4, p1, p2, p3);
    }
}
