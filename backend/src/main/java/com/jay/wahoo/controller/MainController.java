package com.jay.wahoo.controller;

import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.controller.dto.*;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.dto.GameSummary.GameEndReason;
import com.jay.wahoo.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MainController {

    private final GameService gameService;

    @GetMapping("/games")
    public JoinableGameList getJoinableGames() {
        return JoinableGameList.from(gameService.getJoinableGames());
    }

    @GetMapping("/create")
    public JoinableGameList create() throws IOException {
        gameService.createGame();
        return getJoinableGames();
    }

    @PostMapping("/join")
    public String join(@RequestBody JoinGame joinGame) throws NoSuchAlgorithmException {
        Game game = gameService.getGameById(joinGame.gameId)
            .orElseThrow(() -> new IllegalArgumentException("No game with id " + joinGame.gameId + " exists"));
        gameService.joinGame(game, joinGame.playerName, joinGame.playerId);
        return createPlayerToken(joinGame.playerId, joinGame.gameId);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@RequestBody LeaveGame leaveGame) throws NoSuchAlgorithmException {
        Game game = gameService.getGameById(leaveGame.gameId)
            .orElseThrow(() -> new IllegalArgumentException("No game with id " + leaveGame.gameId + " exists"));
        if (!checkPlayerToken(leaveGame.playerToken, leaveGame.playerId, leaveGame.gameId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        gameService.leaveGame(game, leaveGame.playerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forfeit")
    public ResponseEntity<GameSummary> forfeit(@RequestBody ForfeitGame forfeitGame) throws NoSuchAlgorithmException {
        if (!checkPlayerToken(forfeitGame.playerToken, forfeitGame.playerId, forfeitGame.gameId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Game game = gameService.getGameById(forfeitGame.gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        GameSummary summary = gameService.endGame(game, forfeitGame.playerId, GameEndReason.FORFEIT);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/game/{gameId}")
    public GameSummary getGame(@PathVariable String gameId) {
        return gameService.getGameById(gameId)
            .map(game -> (GameSummary) new GameState(game, List.of()))
            .or(() -> gameService.getFinishedGame(gameId))
            .orElseThrow(() -> new IllegalArgumentException("No game with id " + gameId + " exists"));
    }

    @GetMapping("/start/{id}")
    public ResponseEntity<Void> start(@PathVariable String id) {
        Game game = gameService.getGameById(id).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        gameService.startGameLoop(game);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/play")
    public ResponseEntity<GameState> play(@RequestBody HumanPlay play) throws NoSuchAlgorithmException {
        Game game = gameService.getGameById(play.gameId).orElse(null);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!checkPlayerToken(play.playerToken, game.currentPlayer.identifier(), game.identifier)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        gameService.moveHuman(game, play.playerId, play.marbleId);
        return ResponseEntity.ok().build();
    }

    private boolean checkPlayerToken(String token, String playerId, String gameId) throws NoSuchAlgorithmException {
        return createPlayerToken(playerId, gameId).equals(token);
    }

    private String createPlayerToken(String playerId, String gameId) throws NoSuchAlgorithmException {
        String unencryptedToken = """
            {
                "playerId" : "%s",
                "gameId" : "%s"
            }
            """.formatted(playerId, gameId);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(unencryptedToken.getBytes(StandardCharsets.UTF_8)));
    }

}
