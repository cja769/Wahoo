package com.jay.wahoo.controller;

import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.Player;
import com.jay.wahoo.controller.dto.*;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.dto.GameSummary.GameEndReason;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.service.GameService;
import com.jay.wahoo.service.GenomeService;
import com.jay.wahoo.service.GenomeService.Network;
import com.jay.wahoo.service.PoolService;
import com.jay.wahoo.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MainController {

    private final GameService gameService;
    private final GenomeService genomeService;
    private final TrainingService trainingService;

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
            .map(game -> (GameSummary) new GameState(game))
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

    @GetMapping("/network/{gameId}/{playerId}")
    public Network getNetwork(@PathVariable String gameId,
                              @PathVariable String playerId) throws IOException {
        Game game = gameService.getGameById(gameId)
            .orElseThrow(() -> new IllegalArgumentException("No game with gameId " + gameId + " exists"));
        Player player = Arrays.asList(game.players).stream()
            .filter(p -> p.identifier().equals(playerId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No player id with playerId " + playerId + " exists for game " + gameId));
        if (player.isHuman()) {
            throw new IllegalArgumentException("Player with playerId " + playerId + " in game " + gameId + " is a human");
        }
        return genomeService.getNetwork(player.genome());
    }

    @GetMapping("/train/{times}")
    public ResponseEntity<Void> trainAi(@PathVariable int times) throws IOException {
        trainingService.train(times);
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
