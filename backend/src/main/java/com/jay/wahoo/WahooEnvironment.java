package com.jay.wahoo;

import com.jay.wahoo.neat.Environment;
import com.jay.wahoo.neat.Genome;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WahooEnvironment implements Environment {
    @Override
    public void evaluateFitness(ArrayList<Genome> population) {
        List<Genome> remaining = population;
        while (remaining.size() >= 4) {
            remaining = playRound(remaining);
        }
        remaining.forEach(g -> g.setFitness(g.getFitness() + g.getFitness()));
    }

    protected List<Genome> playRound(List<Genome> population) {
        List<Genome> players = new ArrayList<>();
        List<Future<List<Genome>>> inProgress = new ArrayList<>();
        population.forEach(g -> g.setFitness(0));
        final ExecutorService threads = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);
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
        }
        List<Genome> winners = new ArrayList<>();
        while (!inProgress.isEmpty()) {
            if (inProgress.get(0).isDone()) {
                try {
                    winners.addAll(inProgress.get(0).get());
                } catch (Throwable t) {}
                inProgress.remove(0);
            }
        }
        threads.shutdown();
        return winners;
    }

    protected Future<List<Genome>> playMatch(List<Genome> players, ExecutorService threads) {
        return threads.submit(() -> {
            List<Genome> currentGame = new ArrayList<>();
            Map<Genome, Integer> winnerMap = new HashMap<>();
            for (int i = 1; i < 4; i++) {
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
                currentGame = new ArrayList<>();
            }
            Integer mostWins = winnerMap.entrySet().stream()
                .max(Comparator.comparing(Entry::getValue))
                .map(Entry::getValue)
                .get();
            return winnerMap.entrySet().stream()
                .filter(entry -> entry.getValue() != mostWins)
                .map(Map.Entry::getKey)
                .toList();
        });
    }
}
