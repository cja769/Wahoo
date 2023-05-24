package com.jay.wahoo;

import com.jay.wahoo.Player.PlayerState;
import com.jay.wahoo.dto.GameSummary;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.config.NEAT_Config;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
    public int diceRoll;
    private boolean gameStarted = false;
    public LocalDateTime lastUpdated;
    public List<Marble> movableMarbles = new ArrayList<>();
    public boolean rolledThreeSixes;

    public Game(List<Genome> genomes, boolean verbose) {
        assert genomes.size() == 4;
        this.verbose = verbose;
        this.identifier = UUID.randomUUID().toString();
        this.players = new Player[4];
        Player lastPlayer = null;
        Player firstPlayer = null;
        for (int i = 0; i < 4; i++) {
            Player thisPlayer = new Player(genomes.get(i), i, "CPU " + (i+1));
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
            if (diceRoll == 6 && sixCount + 1 >= 3) {
                if (verbose) {
                    System.out.println("Player " + currentPlayer.identifier() + " rolled 3 sixes");
                }
                gameBoard.resetFurthestMarble(currentPlayer);
                sixCount = 0;
                rolledThreeSixes = true;
            } else {
                movableMarbles = getMovableMarbles(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer);
                if (!movableMarbles.isEmpty() && currentPlayer.isHuman()) {
                    awaitingHumanMove = true;
                } else {
                    if (!movableMarbles.isEmpty()) {
                        Marble marbleToMove = chooseMarbleToMove(currentPlayer, movableMarbles, diceRoll, sixCount);
                        gameBoard.move(marbleToMove, diceRoll);
                    }
                    incrementTurn();
                }
            }
        }
        lastUpdated = LocalDateTime.now();
        return new GameState(this);
    }

    private List<Marble> getMovableMarbles(Player player) {
        return player.marbles().stream()
            .filter(m -> gameBoard.testMove(m, diceRoll))
            .toList();
    }

    private void incrementTurn() {
        if (diceRoll == 6) {
            sixCount++;
        } else {
            sixCount = 0;
            playerPos++;
            turns++;
            if (turns % 4 == 0 && verbose) {
                printGameBoard(turns/4);
            }
        }
    }

    public GameState moveHuman(String playerIdentifier, int marbleIdentifier) {
        gameStarted = true;
        if (!awaitingHumanMove) {
            throw new IllegalArgumentException("Can't move human because their moved is not being waited on");
        }
        List<Marble> selectedMarbles = getMovableMarbles(currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer).stream()
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
        return currentPlayer != null && currentPlayer.safeBoard().isComplete() && currentPlayer.partner().safeBoard().isComplete();
    }

    public List<Player> getWinningTeam() {
        return Arrays.stream(players)
            .filter(p -> p.safeBoard().isComplete() && p.partner().safeBoard().isComplete())
            .toList();
    }

    public List<Genome> play() {
        while (!next().gameComplete);
        if (verbose) {
            System.out.println("Players " + currentPlayer.identifier() + " and " + currentPlayer.partner().identifier() + " have won");
            printGameBoard(turns/4);
        }
        return List.of(currentPlayer.genome(), currentPlayer.partner().genome());
    }

    protected void printGameBoard(int turn) {
        System.out.println("After " + turn + " turns");
        for (Player player : players) {
            System.out.println("Player " + player.identifier() + " area");
            printBoard(player.safeBoard());
            printBoard(player.playerBoard());
            printBoard(player.startBoard());
            System.out.println();
        }
        System.out.println("----------------------------------");
    }

    protected void printBoard(ContainingBoard b) {
        System.out.println(Arrays.stream(b.getArea())
            .map(m -> m == null ? "0" : m.player().identifier() + "" + m.identifier())
            .collect(Collectors.joining(" ")));
    }

    protected Marble chooseMarbleToMove(Player player, List<Marble> canMove, int diceRoll, int numSixes) {
        List<Integer> positions = Arrays.stream(players)
            .flatMap(p -> p.marbles().stream()
                .map(m -> p.startBoard().getMarblePositionOnTable(m)))
            .toList();
        float[] inputs = new float[NEAT_Config.INPUTS];
        for (int i = 0; i < positions.size(); i++) {
            inputs[i] = positions.get(i);
        }
        inputs[NEAT_Config.INPUTS - 2] = diceRoll;
        inputs[NEAT_Config.INPUTS - 1] = numSixes;
        float[] outputs = player.genome().evaluateNetwork(inputs);
        Map<Marble, Float> possible = new HashMap<>();
        canMove.forEach(m -> possible.put(m, outputs[m.identifier()]));
        return possible.entrySet().stream()
            .max(Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .get();
    }

    protected int rollDie() {
        return new Random().nextInt(6) + 1;
    }

    public static class GameState extends GameSummary {
        public List<PlayerState> states;
        public int currentRoll;
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
        }

        public GameState (Game game, String winningPlayerId, GameEndReason reason) {
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
