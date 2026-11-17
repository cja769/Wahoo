package com.jay.wahoo;

import com.jay.wahoo.GameBoard.LegalMove;
import com.jay.wahoo.Player.PlayerState;
import com.jay.wahoo.ai.Policy;
import com.jay.wahoo.dto.GameSummary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

public class Game {
    private final GameBoard board;
    private final IntUnaryOperator dice;
    private final Policy policy;
    private final int maxTurns;
    public final Player[] players = new Player[4];
    private final LegalMove[] legalMoves = new LegalMove[4];
    private int playerPos;
    private int sixCount;
    private int turns;
    private int rolls;
    private int legalCount;
    private volatile boolean gameStarted;
    public final String identifier;
    public final boolean ppoPreview;
    public Player currentPlayer;
    public boolean awaitingHumanMove;
    public boolean awaitingComputerMove;
    public int diceRoll;
    public boolean diceRollUpdated;
    public LocalDateTime lastUpdated;
    public boolean rolledThreeSixes;
    private boolean penaltyApplied;

    public Game(Policy policy, boolean ppoPreview) {
        this(policy, seat -> ThreadLocalRandom.current().nextInt(1, 7), 0, Integer.MAX_VALUE,
            UUID.randomUUID().toString(), ppoPreview);
        lastUpdated = LocalDateTime.now();
    }

    public static Game headless(IntUnaryOperator dice, int startingSeat, int maxTurns) {
        return new Game(null, dice, startingSeat, maxTurns, null, false);
    }

    private Game(Policy policy, IntUnaryOperator dice, int startingSeat, int maxTurns, String identifier, boolean ppoPreview) {
        if (startingSeat < 0 || startingSeat >= players.length || maxTurns < 1) {
            throw new IllegalArgumentException("Invalid starting seat or turn limit");
        }
        this.policy = policy;
        this.dice = dice;
        this.maxTurns = maxTurns;
        this.playerPos = startingSeat;
        this.identifier = identifier;
        this.ppoPreview = ppoPreview;
        for (int i = 0; i < players.length; i++) {
            players[i] = new Player(i);
        }
        for (Player player : players) {
            player.setPartner(players[(player.seat() + 2) % players.length]);
        }
        board = new GameBoard(players);
    }

    /** A scratch game for simulations. It has its own board, dice, and no player credentials. */
    public Game rolloutGame(IntUnaryOperator rolloutDice) {
        return headless(rolloutDice, playerPos, maxTurns);
    }

    /** Restore a scratch game to a pending move without transferring the source dice. */
    public void restoreDecision(Game source) {
        if (this == source || identifier != null || source.legalCount == 0 || maxTurns != source.maxTurns) {
            throw new IllegalArgumentException("Expected a scratch game and a compatible pending decision");
        }
        for (Player player : players) {
            for (Marble marble : player.marbles()) {
                if (marble.position() != GameBoard.HOME) {
                    board.relocate(marble, GameBoard.HOME);
                }
            }
        }
        for (Player player : source.players) {
            for (Marble marble : player.marbles()) {
                if (marble.position() != GameBoard.HOME) {
                    board.relocate(players[player.seat()].marbles().get(marble.identifier()), marble.position());
                }
            }
        }
        playerPos = source.playerPos;
        currentPlayer = players[source.currentPlayer.seat()];
        sixCount = source.sixCount;
        turns = source.turns;
        rolls = source.rolls;
        diceRoll = source.diceRoll;
        diceRollUpdated = source.diceRollUpdated;
        rolledThreeSixes = source.rolledThreeSixes;
        penaltyApplied = source.penaltyApplied;
        gameStarted = true;
        legalCount = source.legalCount;
        awaitingHumanMove = false;
        awaitingComputerMove = true;
        for (int action = 0; action < legalMoves.length; action++) {
            LegalMove move = source.legalMoves[action];
            legalMoves[action] = move == null ? null
                : board.legalMove(players[move.marble().player().seat()].marbles().get(action), diceRoll);
        }
    }

    public boolean isJoinable() {
        return !gameStarted;
    }

    public String joinGame(String name, String playerIdentifier) {
        if (!isJoinable()) {
            throw new IllegalArgumentException("Can't join in progress game");
        }
        Player matched = findPlayer(playerIdentifier);
        if (matched.isHuman()) {
            throw new IllegalArgumentException("That seat already has a human player");
        }
        String token = matched.makeHuman(name);
        lastUpdated = LocalDateTime.now();
        return token;
    }

    public void leaveGame(String playerId) {
        if (!isJoinable()) {
            throw new IllegalArgumentException("Can't leave in progress game. You have to forfeit");
        }
        Player matched = findPlayer(playerId);
        if (!matched.isHuman()) {
            throw new IllegalArgumentException("That seat has no human player");
        }
        matched.makeBot();
        lastUpdated = LocalDateTime.now();
    }

    private Player findPlayer(String playerId) {
        return Arrays.stream(players)
            .filter(player -> player.identifier().equals(playerId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown player " + playerId));
    }

    public GameState next() {
        advanceRoll();
        lastUpdated = LocalDateTime.now();
        GameState state = new GameState(this);
        diceRollUpdated = false;
        return state;
    }

    // A roll can open a mandatory decision, reroll, incur a penalty, or end the turn.
    public void advanceRoll() {
        if (legalCount != 0 || isGameComplete()) {
            return;
        }
        gameStarted = true;
        rolledThreeSixes = false;
        penaltyApplied = false;
        currentPlayer = players[playerPos];
        diceRoll = dice.applyAsInt(playerPos);
        if (diceRoll < 1 || diceRoll > 6) {
            throw new IllegalArgumentException("Invalid die roll " + diceRoll);
        }
        rolls++;
        diceRollUpdated = true;
        if (diceRoll == 6 && sixCount == 2) {
            penaltyApplied = board.sendFurthestTrackMarbleHome(marbleOwner());
            sixCount = 0;
            rolledThreeSixes = true;
            return;
        }
        for (Marble marble : marbleOwner().marbles()) {
            LegalMove move = board.legalMove(marble, diceRoll);
            legalMoves[marble.identifier()] = move;
            if (move != null) {
                legalCount++;
            }
        }
        if (legalCount == 0) {
            finishRoll();
        } else {
            awaitingHumanMove = currentPlayer.isHuman();
            awaitingComputerMove = !awaitingHumanMove;
        }
    }

    public int rollCount() { return rolls; }
    public int turnCount() { return turns; }
    public int sixCount() { return sixCount; }
    public GameBoard board() { return board; }
    public int legalCount() { return legalCount; }
    public LegalMove legalMove(int action) { return legalMoves[action]; }

    public Player marbleOwner() {
        return currentPlayer.safeBoard().isComplete() ? currentPlayer.partner() : currentPlayer;
    }

    public int actionMask() {
        int mask = 0;
        for (int action = 0; action < legalMoves.length; action++) {
            if (legalMoves[action] != null) {
                mask |= 1 << action;
            }
        }
        return mask;
    }

    private void endTurn() {
        sixCount = 0;
        playerPos = (playerPos + 1) % players.length;
        turns++;
    }

    public void playAction(int action) {
        if (action < 0 || action >= legalMoves.length || legalMoves[action] == null) {
            throw new IllegalArgumentException("Illegal marble action " + action);
        }
        board.apply(legalMoves[action]);
        Arrays.fill(legalMoves, null);
        legalCount = 0;
        awaitingHumanMove = false;
        awaitingComputerMove = false;
        finishRoll();
    }

    private void finishRoll() {
        if (diceRoll == 6 && winningTeam() < 0) {
            sixCount++;
        } else {
            endTurn();
        }
    }

    public GameState moveComputer() {
        if (!awaitingComputerMove) {
            throw new IllegalStateException("No computer move is pending");
        }
        int mask = actionMask();
        int action = legalCount == 1 ? Integer.numberOfTrailingZeros(mask)
            : Objects.requireNonNull(policy).choose(this);
        playAction(action);
        lastUpdated = LocalDateTime.now();
        return new GameState(this);
    }

    public GameState moveHuman(String playerIdentifier, int marbleIdentifier) {
        if (!awaitingHumanMove || !marbleOwner().identifier().equals(playerIdentifier)) {
            throw new IllegalArgumentException("This player cannot move the requested marble");
        }
        playAction(marbleIdentifier);
        lastUpdated = LocalDateTime.now();
        return new GameState(this);
    }

    public boolean isGameComplete() {
        return turns >= maxTurns || winningTeam() >= 0;
    }

    public int winningTeam() {
        return currentPlayer != null && currentPlayer.safeBoard().isComplete()
            && currentPlayer.partner().safeBoard().isComplete() ? currentPlayer.team() : -1;
    }

    public List<Player> getWinningTeam() {
        int team = winningTeam();
        return team < 0 ? List.of() : List.of(players[team], players[team + 2]);
    }

    private List<Marble> movableMarbles() {
        List<Marble> marbles = new ArrayList<>(legalCount);
        for (LegalMove move : legalMoves) {
            if (move != null) {
                marbles.add(move.marble());
            }
        }
        return marbles;
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
        public boolean penaltyApplied;
        public int rollNumber;

        public GameState(Game game) {
            this.hasStarted = !game.isJoinable();
            this.awaitingHumanMove = game.awaitingHumanMove;
            this.currentPlayerId = game.currentPlayer != null ? game.currentPlayer.identifier() : null;
            this.currentPlayerName = game.currentPlayer != null ? game.currentPlayer.getName() : null;
            this.gameComplete = game.isGameComplete();
            this.gameId = game.identifier;
            this.currentRoll = game.diceRoll;
            List<Marble> movable = game.awaitingHumanMove ? game.movableMarbles() : List.of();
            this.states = Arrays.stream(game.players)
                .map(p -> PlayerState.from(p, movable))
                .toList();
            this.lastUpdated = game.lastUpdated;
            this.rolledThreeSixes = game.rolledThreeSixes;
            this.penaltyApplied = game.penaltyApplied;
            this.rollNumber = game.rollCount();
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
