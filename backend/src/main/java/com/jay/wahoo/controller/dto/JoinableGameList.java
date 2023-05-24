package com.jay.wahoo.controller.dto;

import com.jay.wahoo.Game;
import com.jay.wahoo.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinableGameList {

    public List<JoinableGame> games;

    public static JoinableGameList from(Collection<Game> games) {
        JoinableGameList l = new JoinableGameList();
        l.games = games.stream()
            .map(JoinableGame::from)
            .toList();
        return l;
    }

    public static class JoinableGame {
        public String gameId;
        public List<JoinableGameTeam> players;

        public static JoinableGame from(Game game) {
            JoinableGame g = new JoinableGame();
            g.gameId = game.identifier;
            Map<Player, Player> partnerMap = new HashMap<>();
            for (int i = 0; i < game.players.length; i++) {
                Player p = game.players[i];
                if (!partnerMap.containsKey(p) && !partnerMap.containsValue(p)) {
                    partnerMap.put(p, p.partner());
                }
            }
            g.players = partnerMap.keySet().stream()
                .map(JoinableGameTeam::from)
                .toList();
            return g;
        }

        public static class JoinableGameTeam {

            public JoinableGamePlayer memberOne;
            public JoinableGamePlayer memberTwo;

            public static JoinableGameTeam from(Player p) {
                JoinableGameTeam team = new JoinableGameTeam();
                team.memberOne = JoinableGamePlayer.from(p);
                team.memberTwo = JoinableGamePlayer.from(p.partner());
                return team;
            }

            public static class JoinableGamePlayer {
                public String playerName;
                public String playerId;
                public boolean isHuman;

                public static JoinableGamePlayer from(Player player) {
                    JoinableGamePlayer p = new JoinableGamePlayer();
                    p.playerName = player.getName();
                    p.playerId = player.identifier();
                    p.isHuman = player.isHuman();
                    return p;
                }
            }
        }

    }
}
