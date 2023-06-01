package com.jay.wahoo.service;

import com.jay.wahoo.Board.TestMove;
import com.jay.wahoo.Marble;
import com.jay.wahoo.Player;
import com.jay.wahoo.neat.config.NEAT_Config;

import java.util.*;
import java.util.Map.Entry;

public class DeciderService {

    public static Marble decide(Player player, List<TestMove> unsortedMoves, int diceRoll, int numSixes, Player[] players) {
        List<TestMove> moves = unsortedMoves.stream()
            .sorted(Comparator.comparing(a -> a.marble.identifier()))
            .toList();
        List<Marble> flattenedBoard = player.playerBoard().getFlattenedBoard();
        float[] inputs = new float[NEAT_Config.INPUTS];
        int inputIndex = 0;
        for (int i = 0; i < moves.size(); i++) {
            // Can marble move (0, 21, 42, 63)
            TestMove move = moves.get(i);
            inputs[inputIndex] = move.isMovable ? 1 : -1;
            inputIndex++;
            // Is new position out of home area (1, 22, 43, 64)
            int marblePositionOnTable = player.startBoard().getMarblePositionOnTable(move.marble);
            inputs[inputIndex] = marblePositionOnTable == -1 && move.newLocation != -1 ? 1 : -1;
            inputIndex++;
            // Is new location on left opponent start (2, 23, 44, 65)
            inputs[inputIndex] = move.isOnLeftOpponentStart ? 1 : -1;
            inputIndex++;
            // Is new location on right opponent start (3, 24, 45, 66)
            inputs[inputIndex] = move.isOnRightOpponentStart ? 1 : -1;
            inputIndex++;
            // Is new location on teammate start (4, 25, 46, 67)
            inputs[inputIndex] = move.isOnTeammateStart ? 1 : -1;
            inputIndex++;
            // Location in safe area (5, 26, 47, 68)
            float safeLocation = ((2 * move.safeAreaLocation) - 4) / 6f;
            inputs[inputIndex] = safeLocation;
            inputIndex++;
            // Is in safe area (6, 27, 48, 69)
            inputs[inputIndex] = ((int) safeLocation) != -1 ? 1 : -1;
            inputIndex++;
            // Location on board (7, 28, 49, 70)
            float boardLocation = ((2 * move.newLocation) - 51) / 53f;
            inputs[inputIndex] = boardLocation;
            inputIndex++;
            // Is on board (8, 29, 50, 71)
            inputs[inputIndex] = ((int) boardLocation) != -1 && ((int) boardLocation) != 1 ? 1 : -1;
            inputIndex++;
            boolean wouldKillTeammate = move.marbleKilled.isPresent() && move.marbleKilled.get().player().equals(move.marble.player().partner());
            boolean wouldKillOpponent = move.marbleKilled.isPresent() && !move.marbleKilled.get().player().equals(move.marble.player().partner());
            // Move kills opponent (9, 30, 51, 72)
            inputs[inputIndex] = wouldKillOpponent ? 1 : -1;
            inputIndex++;
            // Move kills teammate (10, 31, 52, 73)
            inputs[inputIndex] = wouldKillTeammate ? 1 : -1;
            inputIndex++;
            // How far opponent was that was killed (11, 32, 53, 74)
            int opponentTableLocation = wouldKillOpponent ? move.marbleKilled.get().player().startBoard().getMarblePositionOnTable(move.marbleKilled.get()) : -1;
            inputs[inputIndex] = ((2 * opponentTableLocation) - 50) / 52f; // One less than normal board location because will never be safe but -1 is still used as not killed
            inputIndex++;
            // How far teammate was that was killed (12, 33, 54, 75)
            int teammateTableLocation = wouldKillTeammate ? move.marbleKilled.get().player().startBoard().getMarblePositionOnTable(move.marbleKilled.get()) : -1;
            inputs[inputIndex] = ((2 * teammateTableLocation) - 50) / 52f; // One less than normal board location because will never be safe but -1 is still used as not killed
            inputIndex++;
            List<Marble> opponentMarbles = Arrays.stream(players)
                .filter(p -> p != player && p.partner() != player)
                .map(Player::marbles)
                .flatMap(Collection::stream)
                .toList();
            List<Marble> teammateMarbles = Arrays.stream(players)
                .filter(p -> p == player.partner())
                .map(Player::marbles)
                .flatMap(Collection::stream)
                .toList();
            // How far the closest opponent is in front (13, 34, 55, 76)
            float closestMarbleInFrontOpponent = getClosestMarbleInFront(move.marble, flattenedBoard, opponentMarbles);
            inputs[inputIndex] = closestMarbleInFrontOpponent;
            inputIndex++;
            // Is there a closest opponent in front (14, 35, 56, 77)
            inputs[inputIndex] = ((int) closestMarbleInFrontOpponent) != -1 ? 1 : -1;
            inputIndex++;
            // How far the closest teammate is in front (15, 36, 57, 78)
            float closestMarbleInFrontTeammate = getClosestMarbleInFront(move.marble, flattenedBoard, teammateMarbles);
            inputs[inputIndex] = closestMarbleInFrontTeammate;
            inputIndex++;
            // is there a closest teammate in front (16, 37, 58, 79)
            inputs[inputIndex] = ((int) closestMarbleInFrontTeammate) != -1 ? 1 : -1;
            inputIndex++;
            // How far the closest opponent is behind (17, 38, 59, 80)
            float closestMarbleBehindOpponent = getClosestMarbleBehind(move.marble, flattenedBoard, opponentMarbles);
            inputs[inputIndex] = closestMarbleBehindOpponent;
            inputIndex++;
            // Is there a closest opponent behind (18, 39, 60, 81)
            inputs[inputIndex] = ((int) closestMarbleBehindOpponent) != -1 ? 1 : -1;
            inputIndex++;
            // How far the closest teammate is behind (19, 40, 61, 82)
            float closestMarbleBehindTeammate = getClosestMarbleBehind(move.marble, flattenedBoard, teammateMarbles);
            inputs[inputIndex] = closestMarbleBehindTeammate;
            inputIndex++;
            // Is there a closest teammate behind (20, 41, 62, 83)
            inputs[inputIndex] = ((int) closestMarbleBehindTeammate) != -1 ? 1 : -1;
            inputIndex++;
        }
        List<Player> playerOrderForPlayer = getPlayerOrderForPlayer(player, players);
        for (Player p : playerOrderForPlayer) {
            if (p != player) {
                int inStart = p.startBoard().getNumberOfMarbles();
                long inSafe = p.safeBoard().getNumberOfMarbles();
                // Num marbles in start (84, 88, 92)
                inputs[inputIndex] = ((2 * (inStart - 1)) - 2) / 4f;
                inputIndex++;
                // Num marbles finished (85, 89, 93)
                inputs[inputIndex] = ((2 * (p.safeBoard().getNumberOfMarblesComplete() - 1)) - 2) / 4f;
                inputIndex++;
                // Num marbles in safe area (86, 90, 94)
                inputs[inputIndex] = ((2 * (inSafe - 1)) - 2) / 4f;
                inputIndex++;
                // Num marbles on playing board (87, 91, 95)
                inputs[inputIndex] = ((2 * (4 - inStart - inSafe - 1)) - 2) / 4f;
                inputIndex++;
            }
        }
        // Num sixes (96)
        inputs[inputIndex] = (2 * (numSixes - 1)) / 2f;
        inputIndex++;
        // Was roll a six (97)
        inputs[inputIndex] = diceRoll == 6 ? 1 : -1;
        // Bias (98) (added elsewhere)
        return evaluate(inputs, player, moves.stream().filter(m -> m.isMovable).map(m -> m.marble).toList());
    }

    protected static float getClosestMarbleInFront(Marble startingMarble, List<Marble> flattenedBoard, List<Marble> matching) {
        int indexOfMarble = -1;
        int distanceToClosest = -1; // Seems weird, but I'm using -1 for not found and 0 isn't possible so in order to get the complete range I'm offsetting the distance by 1 less
        for (int i = 0; i < flattenedBoard.size() - 4; i++) {
            if (indexOfMarble != -1) {
                distanceToClosest++;
            }
            Marble m = flattenedBoard.get(i);
            if (m != null) {
                if (m == startingMarble) {
                    indexOfMarble = i;
                } else {
                    if (matching.contains(m)) {
                        break;
                    }
                }
            }
        }
        return ((2 * distanceToClosest) - 49) / 51f;
    }

    protected static float getClosestMarbleBehind(Marble startingMarble, List<Marble> flattenedBoard, List<Marble> matching) {
        return matching.stream()
            .map(m -> getClosestMarbleInFront(m, flattenedBoard, List.of(startingMarble)))
            .min(Float::compareTo)
            .orElse(-1f);
    }

    public static Marble decideLegacy(Player player, List<Marble> canMove, int diceRoll, int numSixes, Player[] players) {
        List<Integer> positions = getPlayerOrderForPlayer(player, players).stream()
            .flatMap(p -> p.marbles().stream()
                .map(m -> p.startBoard().getMarblePositionOnTable(m)))
            .toList();
        float[] inputs = new float[NEAT_Config.INPUTS];
        for (int i = 0; i < positions.size(); i++) {
            inputs[i] = positions.get(i);
        }
        inputs[NEAT_Config.INPUTS - 2] = diceRoll;
        inputs[NEAT_Config.INPUTS - 1] = numSixes;
        return evaluate(inputs, player, canMove);
    }

    protected static Marble evaluate(float[] inputs, Player player, List<Marble> canMove) {
        float[] outputs = player.genome().evaluateNetwork(inputs);
        Map<Marble, Float> possible = new HashMap<>();
        canMove
            .stream()
            .sorted(Comparator.comparing(Marble::identifier))
            .forEach(m -> possible.put(m, outputs[m.identifier()]));
        return possible.entrySet().stream()
            .max(Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .get();
    }

    protected static List<Player> getPlayerOrderForPlayer(Player player, Player[] players) {
        List<Player> correctOrder = new ArrayList<>();
        int correctOrderIndex = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].equals(player)) {
                correctOrderIndex = 0;
            }
            correctOrder.add(correctOrderIndex, players[i]);
            correctOrderIndex++;
        }
        return correctOrder;
    }
}
