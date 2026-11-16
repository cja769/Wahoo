package com.jay.wahoo.service;

import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.Player;
import com.jay.wahoo.ai.Decision;
import com.jay.wahoo.ai.DensePolicy;
import com.jay.wahoo.ai.Policy;
import com.jay.wahoo.config.PolicyConfig.BotPolicies;
import com.jay.wahoo.controller.dto.JoinableGameList;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.dto.GameSummary.GameEndReason;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameService {

    private final Map<String, Game> gameMap = new ConcurrentHashMap<>();
    private final Map<String, GameState> finishedGames = new ConcurrentHashMap<>();
    private final Set<String> activeLoops = ConcurrentHashMap.newKeySet();
    private static final int TTL_DAYS = 1;
    private final SimpMessagingTemplate template;
    private final BotPolicies computerPolicies;
    @Value("${wahoo.roll-delay-ms:600}")
    private int rollDelayMs;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void pruneMaps() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(TTL_DAYS);
        gameMap.forEach((gameId, game) -> {
            synchronized (game) {
                if (game.lastUpdated.isBefore(cutoff)) {
                    gameMap.remove(gameId, game);
                }
            }
        });
        finishedGames.entrySet().stream()
            .filter(entry -> entry.getValue().lastUpdated.isBefore(cutoff))
            .forEach(entry -> finishedGames.remove(entry.getKey()));
    }

    public GameSummary endGame(Game game, String losingPlayerId, GameEndReason reason) {
        synchronized (game) {
            if (!gameMap.remove(game.identifier, game)) {
                return finishedGames.get(game.identifier);
            }
            Player player = Arrays.stream(game.players)
                .filter(p -> !p.identifier().equals(losingPlayerId) && !p.partner().identifier().equals(losingPlayerId))
                .findFirst()
                .orElseThrow();
            GameState summary = new GameState(game, player.identifier(), reason);
            finishedGames.put(summary.gameId, summary);
            sendGameStateUpdate(summary);
            return summary;
        }
    }

    public GameSummary endGame(Game game, GameEndReason status) {
        gameMap.remove(game.identifier);
        GameState summary = new GameState(game, game.getWinningTeam().get(0).identifier(), status);
        finishedGames.put(summary.gameId, summary);
        sendGameStateUpdate(summary);
        return summary;
    }

    public List<Game> getJoinableGames() {
        return gameMap.values().stream()
            .filter(Game::isJoinable)
            .toList();
    }

    public Map<String, Object> botInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("rulesSchema", Decision.SCHEMA);
        info.put("ppoDefault", computerPolicies.defaultPolicy() != Policy.HEURISTIC);
        info.put("ppoPreview", computerPolicies.preview().isPresent());
        if (computerPolicies.defaultPolicy() instanceof DensePolicy actor) {
            info.put("ppoDefaultSha256", actor.sha256());
        }
        computerPolicies.preview().ifPresent(policy -> {
            if (policy instanceof DensePolicy actor) {
                info.put("ppoPreviewSha256", actor.sha256());
            }
        });
        return info;
    }

    public Game createGame(boolean preview) {
        Policy policy = preview ? computerPolicies.preview()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "PPO preview is unavailable"))
            : computerPolicies.defaultPolicy();
        Game game = new Game(policy, preview);
        gameMap.put(game.identifier, game);
        sendGameStateUpdate(game);
        return game;
    }

    public Optional<Game> getGameById(String id) {
        return Optional.ofNullable(gameMap.get(id));
    }

    public String joinGame(Game game, String playerName, String playerId) {
        synchronized (game) {
            if (!gameMap.containsKey(game.identifier)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            String token = game.joinGame(playerName, playerId);
            sendGameStateUpdate(game);
            return token;
        }
    }

    public Game leaveGame(Game game, String playerId) {
        synchronized (game) {
            game.leaveGame(playerId);
            sendGameStateUpdate(game);
            return game;
        }
    }

    public Optional<GameState> getFinishedGame(String gameId) {
        return Optional.ofNullable(finishedGames.get(gameId));
    }

    public void startGameLoop(Game game) {
        synchronized (game) {
            if (game.awaitingHumanMove || !gameMap.containsKey(game.identifier)
                || !activeLoops.add(game.identifier)) {
                return;
            }
        }
        Mono.fromRunnable(() -> runGameLoop(game))
            .subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private void runGameLoop(Game game) {
        try {
            while (true) {
                boolean computerMove;
                synchronized (game) {
                    if (!gameMap.containsKey(game.identifier)) {
                        activeLoops.remove(game.identifier);
                        return;
                    }
                    GameState state = game.next();
                    if (state.gameComplete) {
                        endGame(game, GameEndReason.GAME_OVER);
                        activeLoops.remove(game.identifier);
                        return;
                    }
                    sendGameStateUpdate(state);
                    if (game.awaitingHumanMove) {
                        // Release before a human action can enter the game and restart its loop.
                        activeLoops.remove(game.identifier);
                        return;
                    }
                    computerMove = game.awaitingComputerMove;
                }
                if (computerMove) {
                    pause(rollDelayMs);
                    synchronized (game) {
                        if (gameMap.containsKey(game.identifier)) {
                            sendGameStateUpdate(game.moveComputer());
                        }
                    }
                    pause(rollDelayMs);
                } else {
                    pause(2L * rollDelayMs);
                }
            }
        } catch (RuntimeException error) {
            synchronized (game) {
                activeLoops.remove(game.identifier);
            }
            throw error;
        }
    }

    public Game moveHuman(Game game, String playerId, int marbleId) {
        synchronized (game) {
            if (!gameMap.containsKey(game.identifier)) {
                throw new IllegalArgumentException("The game has ended");
            }
            game.moveHuman(playerId, marbleId);
            startGameLoop(game);
            return game;
        }
    }

    private void sendGameStateUpdate(Game game) {
        sendGameStateUpdate(new GameState(game));
    }

    private void sendGameStateUpdate(GameSummary gameState) {
        template.convertAndSend("/topic/game/" + gameState.gameId, gameState);
        template.convertAndSend("/topic/joinable", JoinableGameList.from(getJoinableGames()));
    }

    private void pause(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

}
