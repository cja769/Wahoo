package com.jay.wahoo.service;

import com.jay.wahoo.Game;
import com.jay.wahoo.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeciderServiceTest {

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
        assertThat(DeciderService.getPlayerOrderForPlayer(p1, players)).containsExactly(p1, p2, p3, p4);
        assertThat(DeciderService.getPlayerOrderForPlayer(p2, players)).containsExactly(p2, p3, p4, p1);
        assertThat(DeciderService.getPlayerOrderForPlayer(p3, players)).containsExactly(p3, p4, p1, p2);
        assertThat(DeciderService.getPlayerOrderForPlayer(p4, players)).containsExactly(p4, p1, p2, p3);
    }
}
