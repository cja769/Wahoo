package com.jay.wahoo.service;

import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.Player;
import com.jay.wahoo.controller.dto.JoinableGameList;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.dto.GameSummary.GameEndReason;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GameService {

    private Map<String, Game> gameMap = new HashMap<>();
    private Map<String, GameState> finishedGames = new HashMap<>();
    private static final int TTL_DAYS = 1;
    private final SimpMessagingTemplate template;
    private final PoolService poolService;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void pruneMaps() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(TTL_DAYS);
        gameMap.entrySet().stream()
            .filter(entry -> entry.getValue().lastUpdated.isBefore(cutoff))
            .forEach(entry -> gameMap.remove(entry.getKey()));
        finishedGames.entrySet().stream()
            .filter(entry -> entry.getValue().lastUpdated.isBefore(cutoff))
            .forEach(entry -> finishedGames.remove(entry.getKey()));
    }

    public GameSummary endGame(Game game, String losingPlayerId, GameEndReason reason) {
        gameMap.remove(game.identifier);
        Player player = Arrays.stream(game.players)
            .filter(p -> !p.identifier().equals(losingPlayerId) && !p.partner().identifier().equals(losingPlayerId))
            .findFirst()
            .get();
        GameState summary = new GameState(game, player.identifier(), reason);
        finishedGames.put(summary.gameId, summary);
        sendGameStateUpdate(summary);
        return summary;
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

    public Game createGame() throws IOException {
        Game game = new Game(poolService.getPlayersFromPool(poolService.getPool(), 4), false);
        gameMap.put(game.identifier, game);
        sendGameStateUpdate(game);
        return game;
    }

    public Optional<Game> getGameById(String id) {
        return Optional.ofNullable(gameMap.get(id));
    }

    public Game joinGame(Game game, String playerName, String playerId) {
        game.joinGame(playerName, playerId);
        sendGameStateUpdate(game);
        return game;
    }

    public Game leaveGame(Game game, String playerId) {
        game.leaveGame(playerId);
        sendGameStateUpdate(game);
        return game;
    }

    public Optional<GameState> getFinishedGame(String gameId) {
        return Optional.ofNullable(finishedGames.get(gameId));
    }

    public void startGameLoop(Game game) {
        Mono.defer(() -> {
            while (!game.awaitingHumanMove) {
                GameState state = game.next();
                if (state.gameComplete) {
                    sendGameStateUpdate(endGame(game, GameEndReason.GAME_OVER));
                    break;
                } else {
                    sendGameStateUpdate(state);
                    if (!game.awaitingHumanMove) {
                        sleep();
                    }
                }
            }
            return Mono.empty();
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public Game moveHuman(Game game, String playerId, int marbleId) {
        game.moveHuman(playerId, marbleId);
        startGameLoop(game);
        return game;
    }

    private void sendGameStateUpdate(Game game) {
        sendGameStateUpdate(new GameState(game));
    }

    private void sendGameStateUpdate(GameSummary gameState) {
        template.convertAndSend("/topic/game/" + gameState.gameId, gameState);
        template.convertAndSend("/topic/joinable", JoinableGameList.from(getJoinableGames()));
    }

    private void sleep() {
        try {
            Thread.sleep(750);
        } catch (InterruptedException e) {}
    }

}
