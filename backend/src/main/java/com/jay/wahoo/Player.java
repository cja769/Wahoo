package com.jay.wahoo;

import com.jay.wahoo.neat.Genome;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final List<Marble> marbles;
    private final SafeBoard safeBoard;
    private final StartBoard startBoard;
    private final PlayerBoard playerBoard;
    private final String identifier;
    private String name;
    private Player partner;
    private Genome genome;
    private Genome originalGenome;
    private String originalName;
    @Getter
    private int correctMoves;
    @Getter
    private int incorrectMoves;
    @Getter
    private List<Integer> diceRolls = new ArrayList<>();
    @Getter
    @Setter
    private boolean useDiceRolls;
    @Getter
    private int noMoves;
    @Getter
    private int netKills;

    public Player(Genome genome, Integer identifier, String name) {
        this.genome = genome;
        this.name = name;
        this.originalGenome = genome;
        this.originalName = name;
        this.identifier = "p" + identifier;
        this.marbles = new ArrayList<>();
        this.safeBoard = new SafeBoard(this);
        this.startBoard = new StartBoard();
        this.playerBoard = new PlayerBoard();
        this.playerBoard.setPlayer(this);
        for (int i = 0; i < 4; i++) {
            Marble marble = new Marble(this, i);
            marbles.add(marble);
            startBoard.addToBoard(marble);
        }
    }

    public Integer popDiceRoll() {
        return this.diceRolls.remove(diceRolls.size() - 1);
    }

    public void setDiceRolls(List<Integer> diceRolls) {
        this.diceRolls = diceRolls;
        this.useDiceRolls = true;
    }
    public void addDiceRoll(int diceRoll) {
        getDiceRolls().add(diceRoll);
    }

    public void addCorrectMove() {
        correctMoves++;
    }

    public void addIncorrectMove() {
        incorrectMoves++;
    }

    public void addNoMove() {
        if (shouldCountNoMove()) {
            noMoves++;
        }
    }

    public void addTeammateKill() {
        if (shouldCountKill()) {
            netKills--;
        }
    }

    public void addOpponentKill() {
        if (shouldCountKill()) {
            netKills++;
        }
    }

    private boolean shouldCountNoMove() {
        boolean hasHadChanceToGetOut = correctMoves > 0 || incorrectMoves > 0;
        boolean hasLessThanThreeMarblesHome = safeBoard.getNumberOfMarblesComplete() < 3;
        return hasHadChanceToGetOut && hasLessThanThreeMarblesHome && !isHuman();
    }

    private boolean shouldCountKill() {
        return safeBoard.getNumberOfMarblesComplete() < 3 && !isHuman();
    }

    public Double getValidMovePercentage() {
        return correctMoves / (correctMoves + incorrectMoves + .0);
    }

    public Double getOverallMovePercentage() {
        return correctMoves / (correctMoves + incorrectMoves + noMoves + .0);
    }

    public int getOverallMoveFitness() {
       return getOverallMovePercentage() >= .9 ? 1 : 0;
    }

    public void makeHuman(String name) {
        this.name = name;
        this.genome = null;
    }

    public void makeBot() {
        this.name = this.originalName;
        this.genome = this.originalGenome;
    }

    public String getName() {
        return  name;
    }

    public Genome genome() {
        return genome;
    }

    public boolean isHuman() {
        return genome == null;
    }

    public void setPartner(Player partner) {
        this.partner = partner;
    }

    public Player partner() {
        return partner;
    }

    public String identifier() {
        return identifier;
    }

    public List<Marble> marbles() {
        return marbles;
    }

    public SafeBoard safeBoard() {
        return safeBoard;
    }

    public StartBoard startBoard() {
        return startBoard;
    }

    public PlayerBoard playerBoard() {
        return playerBoard;
    }

    public boolean isTeammate(Player p) {
        return partner.equals(p);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Player) {
            Player other = (Player) obj;
            return identifier().equals(other.identifier());
        }
        return false;
    }

    public static class PlayerState {
        public String playerId;
        public String playerName;
        public List<MarblePosition> safePositions = new ArrayList<>();
        public List<MarblePosition> homePositions = new ArrayList<>();
        public List<MarblePosition> playerAreaPositions = new ArrayList<>();

        public static PlayerState from(Player player, List<Marble> validMarblesToMove) {
            PlayerState playerState = new PlayerState();
            playerState.playerId = player.identifier();
            playerState.playerName = player.getName();
            playerState.safePositions = player.safeBoard.getState(validMarblesToMove);
            playerState.homePositions = player.startBoard.getState(validMarblesToMove);
            playerState.playerAreaPositions = player.playerBoard().getState(validMarblesToMove);
            return playerState;
        }

        public static class MarblePosition {
            public int position;
            public String playerId;
            public int marbleIdentifier;
            public boolean isMovable;

            public static MarblePosition from(Marble m, boolean isMovable, int position) {
                MarblePosition pos = new MarblePosition();
                pos.position = position;
                pos.playerId = m.player().identifier();
                pos.marbleIdentifier = m.identifier();
                pos.isMovable = isMovable;
                return pos;
            }
        }
    }

    @Override
    public String toString() {
        return identifier;
    }
}
