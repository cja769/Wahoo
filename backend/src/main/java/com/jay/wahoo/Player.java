package com.jay.wahoo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Player {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private final List<Marble> marbles;
    private final SafeBoard safeBoard = new SafeBoard();
    private final StartBoard startBoard = new StartBoard();
    private final PlayerBoard playerBoard = new PlayerBoard();
    private final int seat;
    private final String identifier;
    private final String botName;
    private String name;
    private Player partner;
    private volatile String seatToken;

    public Player(int seat) {
        this.seat = seat;
        identifier = "p" + seat;
        botName = "CPU " + (seat + 1);
        name = botName;
        List<Marble> initialMarbles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Marble marble = new Marble(this, i);
            initialMarbles.add(marble);
            startBoard.add(marble);
        }
        marbles = List.copyOf(initialMarbles);
    }

    String makeHuman(String name) {
        byte[] token = new byte[32];
        TOKEN_RANDOM.nextBytes(token);
        this.name = name;
        seatToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        return seatToken;
    }

    public boolean hasSeatToken(String providedToken) {
        String assignedToken = seatToken;
        return assignedToken != null && providedToken != null
            && MessageDigest.isEqual(assignedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }

    void makeBot() {
        name = botName;
        seatToken = null;
    }

    public String getName() { return name; }
    public boolean isHuman() { return seatToken != null; }
    public int seat() { return seat; }
    public int team() { return seat % 2; }
    public String identifier() { return identifier; }
    public Player partner() { return partner; }
    public List<Marble> marbles() { return marbles; }
    public SafeBoard safeBoard() { return safeBoard; }
    public StartBoard startBoard() { return startBoard; }
    public PlayerBoard playerBoard() { return playerBoard; }

    void setPartner(Player partner) {
        this.partner = partner;
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

}
