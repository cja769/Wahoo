package com.jay.wahoo.controller;

import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.Player;
import com.jay.wahoo.controller.dto.*;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.dto.GameSummary.GameEndReason;
import com.jay.wahoo.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MainController {

    private final GameService gameService;

    @GetMapping("/games")
    public JoinableGameList getJoinableGames() {
        return JoinableGameList.from(gameService.getJoinableGames());
    }

    @GetMapping("/bots")
    public Map<String, Boolean> getBots() {
        return Map.of("ppoPreview", gameService.isPreviewAvailable(), "ppoDefault", gameService.isPpoDefault());
    }

    @GetMapping("/create")
    public JoinableGameList create(@RequestParam(defaultValue = "false") boolean ppoPreview) {
        gameService.createGame(ppoPreview);
        return getJoinableGames();
    }

    @PostMapping("/join")
    public String join(@RequestBody JoinGame joinGame) {
        Game game = gameService.getGameById(joinGame.gameId)
            .orElseThrow(() -> new IllegalArgumentException("No game with id " + joinGame.gameId + " exists"));
        return gameService.joinGame(game, joinGame.playerName, joinGame.playerId);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@RequestBody LeaveGame leaveGame) {
        Game game = gameService.getGameById(leaveGame.gameId)
            .orElseThrow(() -> new IllegalArgumentException("No game with id " + leaveGame.gameId + " exists"));
        synchronized (game) {
            if (!checkPlayerToken(game, leaveGame.playerId, leaveGame.playerToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            gameService.leaveGame(game, leaveGame.playerId);
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/forfeit")
    public ResponseEntity<GameSummary> forfeit(@RequestBody ForfeitGame forfeitGame) {
        Game game = gameService.getGameById(forfeitGame.gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        synchronized (game) {
            if (!checkPlayerToken(game, forfeitGame.playerId, forfeitGame.playerToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            GameSummary summary = gameService.endGame(game, forfeitGame.playerId, GameEndReason.FORFEIT);
            return ResponseEntity.ok(summary);
        }
    }

    @GetMapping("/game/{gameId}")
    public GameSummary getGame(@PathVariable String gameId) {
        return gameService.getGameById(gameId)
            .map(game -> {
                synchronized (game) {
                    return (GameSummary) new GameState(game);
                }
            })
            .or(() -> gameService.getFinishedGame(gameId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/start/{id}")
    public ResponseEntity<Void> start(@PathVariable String id,
                                      @RequestHeader(value = "X-Wahoo-Seat", required = false) String token) {
        Game game = gameService.getGameById(id).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        synchronized (game) {
            if (Arrays.stream(game.players).noneMatch(player -> player.hasSeatToken(token))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            gameService.startGameLoop(game);
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/play")
    public ResponseEntity<GameState> play(@RequestBody HumanPlay play) {
        Game game = gameService.getGameById(play.gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        synchronized (game) {
            if (game.currentPlayer == null
                || !game.currentPlayer.hasSeatToken(play.playerToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            gameService.moveHuman(game, play.playerId, play.marbleId);
            return ResponseEntity.ok().build();
        }
    }

    @GetMapping({"/network/{gameId}/{playerId}", "/train/{times}"})
    public ResponseEntity<Void> retiredNeatEndpoint() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

    private boolean checkPlayerToken(Game game, String playerId, String token) {
        for (Player player : game.players) {
            if (player.identifier().equals(playerId)) {
                return player.hasSeatToken(token);
            }
        }
        return false;
    }
}
