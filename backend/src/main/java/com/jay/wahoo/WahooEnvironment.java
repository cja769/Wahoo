package com.jay.wahoo;

import com.jay.wahoo.neat.Environment;
import com.jay.wahoo.neat.Genome;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class WahooEnvironment implements Environment {
    @Override
    public void evaluateFitness(ArrayList<Genome> population) {
        List<Genome> remaining = population;
        int round = 1;
        while (remaining.size() >= 4) {
            log.info("Starting round " + round);
            log.info("Population Size: " + remaining.size());
            remaining = playRound(remaining);
            log.info("Round " + round + " complete.");
            round++;
        }
        remaining.forEach(g -> g.setFitness(g.getFitness() + g.getFitness()));
    }

    protected List<Genome> playRound(List<Genome> population) {
        List<Genome> players = new ArrayList<>();
        List<Future<Genome>> inProgress = new ArrayList<>();
        population.forEach(g -> g.setFitness(0));
        final ExecutorService threads = Executors.newFixedThreadPool(2);
        for (int i = 0; i < population.size(); i++) {
            if (players.size() == 4) {
                inProgress.add(playMatch(players, threads));
                players = new ArrayList<>();
            }
            Genome player = population.get(i);
            if (player.getFitness() == 0) {
                player.setFitness(10);
            } else {
                player.setFitness(player.getFitness() + player.getFitness());
            }
            players.add(player);
        }
        if (players.size() == 4) {
            inProgress.add(playMatch(players, threads));
            players = new ArrayList<>();
        }
        List<Genome> winners = new ArrayList<>();
        log.info("There's " + players.size() + " number of players that were omitted from matches");
        log.info("Number of in progress matches " + inProgress.size());
        while (!inProgress.isEmpty()) {
            if (inProgress.get(0).isDone()) {
                try {
                    winners.add(inProgress.get(0).get());
                    inProgress.remove(0);
                    log.info("Number of in progress matches " + inProgress.size());
                } catch (Throwable t) {
                    log.error("Error running match", t);
                }
            }
        }
        threads.shutdown();
        return winners;
    }

    protected Future<Genome> playMatch(List<Genome> players, ExecutorService threads) {
        return threads.submit(() -> {
            List<Genome> currentGame = new ArrayList<>();
            Map<Genome, Integer> winnerMap = new HashMap<>();
            int rounds = 20;
            int maxGames = rounds * 3;
            boolean shouldBreak = false;
            for (int round = 0; round < rounds && !shouldBreak; round++) {
                for (int i = 1; i < 4 && !shouldBreak; i++) {
                    currentGame.add(players.get(0));
                    currentGame.add(players.get(i));
                    for (int j = 1; j < 4; j++) {
                        if (i != j) {
                            currentGame.add(players.get(j));
                        }
                    }
                    List<Genome> winners = new Game(currentGame, false).play();
                    winners.forEach(w -> {
                        Integer wins = winnerMap.get(w);
                        if (wins == null) {
                            wins = 1;
                        } else {
                            wins++;
                        }
                        winnerMap.put(w, wins);
                    });
                    shouldBreak = shouldShortCircuit(round, 3, i, maxGames, winnerMap.values());
                    currentGame = new ArrayList<>();
                }
            }
            Integer mostWins = winnerMap.entrySet().stream()
                .max(Comparator.comparing(Entry::getValue))
                .map(Entry::getValue)
                .get();
            return winnerMap.entrySet().stream()
                .filter(entry -> entry.getValue() != mostWins)
                .map(Map.Entry::getKey)
                .toList()
                .get(0);
        });
    }

    protected static boolean shouldShortCircuit(int roundsComplete, int gamesInRound, int gamesFinishedInRound, int maxGames, Collection<Integer> wins) {
        int playedGames = (roundsComplete * gamesInRound) + gamesFinishedInRound;
        int remainingGames = maxGames - playedGames;
        Integer maxWins = wins.stream()
            .max(Integer::compareTo)
            .orElse(0);
        int possibleWinners = wins.stream()
            .filter(numWins -> (numWins + remainingGames) >= maxWins)
            .toList()
            .size();
        return possibleWinners == 1;
    }
}
