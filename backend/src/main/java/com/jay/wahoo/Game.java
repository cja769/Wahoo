package com.jay.wahoo;

import com.jay.wahoo.Board.TestMove;
import com.jay.wahoo.Player.PlayerState;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.service.DeciderService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Game {

    private GameBoard gameBoard;
    public Player[] players;
    private int playerPos = 0;
    public Player currentPlayer;
    private int sixCount = 0;
    private int turns = 0;
    private boolean verbose;
    public final String identifier;
    public boolean awaitingHumanMove = false;
    public boolean awaitingComputerMove = false;
    public int diceRoll;
    public boolean diceRollUpdated = false;
    private boolean gameStarted = false;
    public LocalDateTime lastUpdated;
    public List<Marble> movableMarbles = new ArrayList<>();
    public boolean rolledThreeSixes;
    private boolean training;
    private final Integer maxTurns;

    public Game(List<Genome> genomes, boolean verbose, Integer maxTurns, boolean training) {
        assert genomes.size() == 4;
        this.maxTurns = maxTurns;
        this.training = training;
        this.verbose = verbose;
        this.identifier = UUID.randomUUID().toString();
        this.players = new Player[4];
        Player lastPlayer = null;
        Player firstPlayer = null;
        for (int i = 0; i < 4; i++) {
            Player thisPlayer = new Player(genomes.get(i), i, "CPU " + (i + 1));
            players[i] = thisPlayer;
            if (firstPlayer == null) {
                firstPlayer = thisPlayer;
            }
            if (lastPlayer != null) {
                lastPlayer.playerBoard().setNextPlayerBoard(thisPlayer.playerBoard());
                lastPlayer.playerBoard().setNextSafeBoard(thisPlayer.safeBoard());
            }
            lastPlayer = thisPlayer;
        }
        lastPlayer.playerBoard().setNextPlayerBoard(firstPlayer.playerBoard());
        lastPlayer.playerBoard().setNextSafeBoard(firstPlayer.safeBoard());
        for (int i = 0; i < 4; i++) {
            players[i].setPartner(players[(i + 2) % 4]);
        }
        this.gameBoard = new GameBoard(Arrays.stream(players).toList());
        this.lastUpdated = LocalDateTime.now();
    }

    public Game(List<Genome> genomes) {
        this(genomes, false, null, false);
    }

    public boolean isJoinable() {
        return !gameStarted;
    }

    public void joinGame(String name, String playerIdentifier) {
        if (!isJoinable()) {
            throw new IllegalArgumentException("Can't join in progress game");
        }
        Player matched = findPlayer(playerIdentifier);
        if (matched.isHuman()) {
            throw new IllegalArgumentException("Can't join as player " + playerIdentifier + " because that's already a human");
        }
        matched.makeHuman(name);
        if (name.equalsIgnoreCase("train")) {
            this.verbose = true;
            this.training = true;
        }
        lastUpdated = LocalDateTime.now();
    }

    public void leaveGame(String playerId) {
        if (!isJoinable()) {
            throw new IllegalArgumentException("Can't leave in progress game. You have to forfeit");
        }
        Player matched = findPlayer(playerId);
        if (!matched.isHuman()) {
            throw new IllegalArgumentException("Can't join as player " + playerId + " because that's already a human");
        }
        matched.makeBot();
        boolean resetSettings = Arrays.stream(players)
            .noneMatch(p -> p.getName().equalsIgnoreCase("train"));
        if (resetSettings) {
            this.verbose = false;
            this.training = false;
        }
        lastUpdated = LocalDateTime.now();
    }

    private Player findPlayer(String playerId) {
        List<Player> found = Arrays.stream(players)
            .filter(p -> p.identifier().equals(playerId))
            .toList();
        if (found.isEmpty()) {
            throw new IllegalArgumentException("No player exists with identifier " + playerId);
        }
        return found.get(0);
    }

    public GameState next() {
        gameStarted = true;
        rolledThreeSixes = false;
        if (!awaitingHumanMove && !isGameComplete()) {
            playerPos = playerPos % 4;
            currentPlayer = players[playerPos];
            diceRoll = rollDie();
            diceRollUpdated = true;
            if (diceRoll == 6 && sixCount + 1 >= 3) {
                gameBoard.resetFurthestMarble(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer);
                sixCount = 0;
                rolledThreeSixes = true;
            } else {
                List<TestMove> moves = getMoves(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer);
                movableMarbles = getMoveableMarbles(moves);
                if (!movableMarbles.isEmpty()) {
                    if (currentPlayer.isHuman()) {
                        awaitingHumanMove = true;
                    } else {
                        awaitingComputerMove = true;
                    }
                } else {
                    incrementTurn();
                }
            }
        }
        lastUpdated = LocalDateTime.now();
        GameState gameState = new GameState(this);
        diceRollUpdated = false;
        return gameState;
    }

    private List<TestMove> getMoves(Player player) {
        return player.marbles().stream()
            .map(m -> gameBoard.testMove(m, diceRoll))
            .toList();
    }

    private List<Marble> getMoveableMarbles(List<TestMove> moves) {
        return moves.stream()
            .filter(m -> m.isMovable)
            .map(m -> m.marble)
            .toList();
    }

    private void incrementTurn() {
        if (diceRoll == 6) {
            sixCount++;
        } else {
            sixCount = 0;
            playerPos++;
            turns++;
        }
    }

    public GameState moveComputer() {
        List<TestMove> moves = getMoves(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer);
        Marble marbleToMove = DeciderService.decide(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer, currentPlayer.genome(), moves, diceRoll, sixCount, players, training);
        if (marbleToMove != null) {
            gameBoard.move(marbleToMove, diceRoll);
            currentPlayer.addCorrectMove();
        } else {
            currentPlayer.addIncorrectMove();
        }
        awaitingComputerMove = false;
        incrementTurn();
        lastUpdated = LocalDateTime.now();
        return new GameState(this);
    }

    public GameState moveHuman(String playerIdentifier, int marbleIdentifier) {
        gameStarted = true;
        if (!awaitingHumanMove) {
            throw new IllegalArgumentException("Can't move human because their moved is not being waited on");
        }
        List<Marble> selectedMarbles = getMoveableMarbles(getMoves(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer)).stream()
            .filter(m -> m.player().identifier().equals(playerIdentifier) && m.identifier().equals(marbleIdentifier))
            .toList();
        if (selectedMarbles.isEmpty()) {
            throw new IllegalArgumentException("Marble " + marbleIdentifier + " for player " + playerIdentifier + " cannot be moved by player " + currentPlayer.identifier());
        }
        gameBoard.move(selectedMarbles.get(0), diceRoll);
        awaitingHumanMove = false;
        incrementTurn();
        lastUpdated = LocalDateTime.now();
        return new GameState(this);
    }

    public boolean isGameComplete() {
        if (maxTurns != null && turns >= maxTurns) {
            return true;
        }
        return currentPlayer != null && currentPlayer.safeBoard().isComplete() && currentPlayer.partner().safeBoard().isComplete();
    }

    private List<Player> getTopCorrectMovePercentagePlayers() {
        return getPlayersSortedByMovePercentage().stream().limit(2).toList();
    }

    private List<Player> getPlayersSortedByMovePercentage() {
        return Arrays.stream(players)
            .filter(p -> !p.isHuman())
            .sorted((a, b) -> {
                int compare = b.getCorrectMovePercentage().compareTo(a.getCorrectMovePercentage());
                if (compare == 0) {
                    return Integer.compare(b.getCorrectMoves(), a.getCorrectMoves());
                }
                return compare;
            })
            .toList();
    }

    public List<Player> getWinningTeam() {
        List<Player> winners = Arrays.stream(players)
            .filter(p -> p.safeBoard().isComplete() && p.partner().safeBoard().isComplete())
            .toList();
        if (verbose) {
            getPlayersSortedByMovePercentage()
                .forEach(p -> {
                    log.info("Player " + p.getName() + " : Correct moves " + p.getCorrectMoves() + "; Incorrect moves " + p.getIncorrectMoves() + "; Correct move percentage " + p.getCorrectMovePercentage());
                });
            log.info("It took " + turns + " turns to finish the game\n");
        }
        boolean winnersMadeAllCorrectMoves = winners.stream()
            .allMatch(p -> p.getCorrectMovePercentage().equals(1.0));
        if (!winners.isEmpty() && (winnersMadeAllCorrectMoves || !training)) {
            return winners;
        }
        return getTopCorrectMovePercentagePlayers();
    }

    public List<Genome> play() {
        while (!next().gameComplete) {
            if (awaitingComputerMove) {
                moveComputer();
            }
        }
        return getWinningTeam().stream().map(Player::genome).toList();
    }

    protected int rollDie() {
        return new Random().nextInt(6) + 1;
    }

    public static class GameState extends GameSummary {
        public List<PlayerState> states;
        public int currentRoll;
        public boolean diceRollUpdated;
        public boolean gameComplete;
        public String currentPlayerId;
        public String currentPlayerName;
        public boolean awaitingHumanMove;
        public boolean hasStarted;
        public LocalDateTime lastUpdated;
        public boolean rolledThreeSixes;

        public GameState(Game game) {
            this.hasStarted = !game.isJoinable();
            this.awaitingHumanMove = game.awaitingHumanMove;
            this.currentPlayerId = game.currentPlayer != null ? game.currentPlayer.identifier() : null;
            this.currentPlayerName = game.currentPlayer != null ? game.currentPlayer.getName() : null;
            this.gameComplete = game.isGameComplete();
            this.gameId = game.identifier;
            this.currentRoll = game.diceRoll;
            this.states = Arrays.stream(game.players)
                .map(p -> PlayerState.from(p, game.awaitingHumanMove ? game.movableMarbles : List.of()))
                .toList();
            this.lastUpdated = game.lastUpdated;
            this.rolledThreeSixes = game.rolledThreeSixes;
            this.diceRollUpdated = game.diceRollUpdated;
        }

        public GameState(Game game, String winningPlayerId, GameEndReason reason) {
            this(game);
            this.endReason = reason;
            Player player = Arrays.stream(game.players)
                .filter(p -> p.identifier().equals(winningPlayerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No player with id " + winningPlayerId));
            this.winningPlayers.add(GameSummaryPlayer.from(player));
            this.winningPlayers.add(GameSummaryPlayer.from(player.partner()));
            this.losingPlayers = Arrays.stream(game.players)
                .filter(p -> !p.equals(player) && !p.equals(player.partner()))
                .map(GameSummaryPlayer::from)
                .toList();
        }

    }

}
