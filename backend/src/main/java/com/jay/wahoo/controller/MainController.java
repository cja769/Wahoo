package com.jay.wahoo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.wahoo.Game;
import com.jay.wahoo.Game.GameState;
import com.jay.wahoo.Player;
import com.jay.wahoo.WahooEnvironment;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.Pool;
import com.jay.wahoo.neat.Species;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MainController {

    private Map<String, Game> gameMap = new HashMap<>();
    private Map<String, GameSummary> finishedGames = new HashMap<>();

    @Autowired
    private SimpMessagingTemplate template;

    @GetMapping("/run/{generations}")
    public String run(@PathVariable Integer generations) throws IOException {
        WahooEnvironment environment = new WahooEnvironment();
        Pool pool = getPool();
        int generation = 0;
        while (true) {
            pool.evaluateFitness(environment);
            System.out.println("Generation : " + generation);
            generation++;
            if (generation > generations) {
                break;
            }
            pool.breedNewGeneration();
        }
        String serialized = new ObjectMapper().writer().writeValueAsString(pool);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("pool.json"), "utf-8"))) {
            writer.write(serialized);
        }
        playBestMatch(pool);
        return "Done";
    }

    @GetMapping("/games")
    public JoinableGameList getJoinableGames() {
        return JoinableGameList.from(gameMap.values());
    }

    public static class JoinableGameList {
        public List<JoinableGame> games;

        public static JoinableGameList from(Collection<Game> games) {
            JoinableGameList l = new JoinableGameList();
            l.games = games.stream()
                .filter(Game::isJoinable)
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

    @GetMapping("/create")
    public JoinableGameList create() throws IOException {
        Game game = new Game(getPlayersFromPool(getPool(), 4), false);
        gameMap.put(game.identifier, game);
        sendGameStateUpdate(game);
        return getJoinableGames();
    }

    @PostMapping("/join")
    public String join(@RequestBody JoinGame joinGame) throws NoSuchAlgorithmException {
        Game game = gameMap.get(joinGame.gameId);
        if (game == null) {
            throw new IllegalArgumentException("No game with id " + joinGame.gameId + " exists");
        }
        game.joinGame(joinGame.playerName, joinGame.playerId);
        sendGameStateUpdate(game);
        return createPlayerToken(joinGame.playerId, joinGame.gameId);
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@RequestBody LeaveGame leaveGame) throws NoSuchAlgorithmException {
        Game game = gameMap.get(leaveGame.gameId);
        if (game == null) {
            throw new IllegalArgumentException("No game with id " + leaveGame.gameId + " exists");
        }
        if (!checkPlayerToken(leaveGame.playerToken, leaveGame.playerId, leaveGame.gameId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        game.leaveGame(leaveGame.playerId);
        sendGameStateUpdate(game);
        return ResponseEntity.ok().build();
    }

    public static class LeaveGame {
        public String gameId;
        public String playerId;
        public String playerToken;
    }

    @PostMapping("/forfeit")
    public ResponseEntity<GameSummary> forfeit(@RequestBody ForfeitGame forfeitGame) throws NoSuchAlgorithmException {
        if (!checkPlayerToken(forfeitGame.playerToken, forfeitGame.playerId, forfeitGame.gameId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Game game = gameMap.get(forfeitGame.gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        GameSummary summary = endGame(game, forfeitGame.playerId, GameEndReason.FORFEIT);
        sendGameStateUpdate(summary);
        return ResponseEntity.ok(summary);
    }

    public static class ForfeitGame {
        public String playerToken;
        public String gameId;
        public String playerId;
    }

    @GetMapping("/game/{gameId}")
    public GameSummary getGame(@PathVariable String gameId) {
        Game g = gameMap.get(gameId);
        GameSummary gs = finishedGames.get(gameId);
        if (g == null && gs == null) {
            throw new IllegalArgumentException("No game with id " + gameId + " exists");
        }
        if (g != null) {
            return new GameState(g, List.of());
        }
        return gs;
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


    public static class JoinGame {
        public String gameId;
        public String playerName;
        public String playerId;
    }

    @GetMapping("/start/{id}")
    public ResponseEntity<Void> start(@PathVariable String id) {
        Game game = gameMap.get(id);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        startGameLoop(game);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/play")
    public ResponseEntity<GameState> play(@RequestBody HumanPlay play) throws NoSuchAlgorithmException {
        Game game = gameMap.get(play.gameId);
        if (game == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!checkPlayerToken(play.playerToken, game.currentPlayer.identifier(), game.identifier)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        game.moveHuman(play.playerId, play.marbleId);
        startGameLoop(game);
        return ResponseEntity.ok().build();
    }

    private void startGameLoop(Game game) {
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

    private void sleep() {
        try {
            Thread.sleep(750);
        } catch (InterruptedException e) {}
    }

    public static class HumanPlay {
        public String playerId;
        public int marbleId;
        public String gameId;
        public String playerToken;
    }

    public Pool getPool() throws IOException {
        Pool pool = new Pool();
        if (new File("pool.json").exists()) {
            String poolJson;
            try(BufferedReader br = new BufferedReader(new FileReader("pool.json"))) {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                poolJson = sb.toString();
            }
            pool = new ObjectMapper().readerFor(Pool.class).readValue(poolJson);
        } else {
            pool.initializePool();
        }
        return pool;
    }

    public void playBestMatch(Pool pool) {
        new Game(getPlayersFromPool(pool, 4), true).play();
    }

    public List<Genome> getPlayersFromPool(Pool pool, int numPlayers) {
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: pool.getSpecies()){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome,Collections.reverseOrder());

        List<Genome> players = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            players.add(allGenome.get(i));
        }
        return players;
    }

    public GameSummary endGame(Game game, String losingPlayerId, GameEndReason reason) {
        gameMap.remove(game.identifier);
        Player player = Arrays.stream(game.players)
            .filter(p -> !p.identifier().equals(losingPlayerId) && !p.partner().identifier().equals(losingPlayerId))
            .findFirst()
            .get();
        GameState summary = new GameState(game, player.identifier(), reason);
        finishedGames.put(summary.gameId, summary);
        return summary;
    }
    public GameSummary endGame(Game game, GameEndReason status) {
        gameMap.remove(game.identifier);
        GameState summary = new GameState(game, game.getWinningTeam().get(0).identifier(), status);
        finishedGames.put(summary.gameId, summary);
        return summary;
    }

    public static class GameSummary {
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
    }

    public enum GameEndReason {
        FORFEIT, GAME_OVER
    }

    private void sendGameStateUpdate(Game game) {
        sendGameStateUpdate(new GameState(game, List.of()));
    }

    private void sendGameStateUpdate(GameSummary gameState) {
        template.convertAndSend("/topic/game/" + gameState.gameId, gameState);
        template.convertAndSend("/topic/joinable", getJoinableGames());
    }
}
