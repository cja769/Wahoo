package com.jay.wahoo.dto;

import com.jay.wahoo.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GameSummary {
    public GameEndReason endReason;
    public List<GameSummaryPlayer> winningPlayers = new ArrayList<>();
    public List<GameSummaryPlayer> losingPlayers;
    public String gameId;

    public static class GameSummaryPlayer {
        public String playerName;
        public String playerId;

        public static GameSummaryPlayer from(Player player) {
            GameSummaryPlayer p = new GameSummaryPlayer();
            p.playerName = player.getName();
            p.playerId = player.identifier();
            return p;
        }
    }

    public enum GameEndReason {
        FORFEIT, GAME_OVER
    }
}
