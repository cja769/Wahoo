package com.jay.wahoo;

import com.jay.wahoo.neat.Environment;
import com.jay.wahoo.neat.Genome;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class WahooEnvironment implements Environment {
    @Override
    public void evaluateFitness(ArrayList<Genome> population) {
        population.forEach(g -> g.setFitness(0));
        Collections.shuffle(population);
        start(Mono.just(population))
            .map(remaining -> {
                remaining.forEach(g -> g.setFitness(g.getFitness() + g.getFitness()));
                return remaining;
            }).block();
    }

    protected Mono<List<Genome>> start(Mono<List<Genome>> population) {
        return population
            .flatMap(pop -> {
                log.info("Population size is " + pop.size());
                if (pop.size() >= 4) {
                    return start(playRound(pop, pop.size() == 4 ? 1 : 2));
                }
                return Mono.just(pop);
            });
    }

    protected Mono<List<Genome>> playRound(List<Genome> population, int numWinnersToReturn) {
        List<Genome> players = new ArrayList<>();
        List<Mono<List<Genome>>> inProgress = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) {
            if (players.size() == 4) {
                inProgress.add(playMatch(players, numWinnersToReturn));
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
            inProgress.add(playMatch(players, numWinnersToReturn));
        }
        return Flux.fromIterable(inProgress)
            .flatMap(Function.identity(), 2)
            .flatMap(Flux::fromIterable)
            .collectList();
    }

    protected Mono<List<Genome>> playMatch(List<Genome> players, int numWinnersToReturn) {
        return Mono.defer(() -> {
            List<Genome> currentGame = new ArrayList<>();
            Map<Genome, Integer> winnerMap = new HashMap<>();
            players.forEach(g -> winnerMap.put(g, 0));
            int rounds = 20;
            int maxGames = rounds * 3;
            boolean shouldBreak = false;
            Map<Genome, Float> playerFitness = players.stream()
                .collect(Collectors.toMap(Function.identity(), Genome::getFitness));
            players.forEach(p -> {
                p.setFitness(10);
            });
            for (int round = 0; round < rounds && !shouldBreak; round++) {
                for (int i = 1; i < 4 && !shouldBreak; i++) {
                    currentGame.add(players.get(0));
                    currentGame.add(players.get(i));
                    for (int j = 1; j < 4; j++) {
                        if (i != j) {
                            currentGame.add(players.get(j));
                        }
                    }
                    new Game(currentGame, false).play().stream()
                        .filter(g -> g.getFitness() > 0)
                        .forEach(w -> {
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
                    players.forEach(p -> {
                        p.setFitness(10);
                    });
                }
            }
            players.forEach(p -> {
                p.setFitness(playerFitness.get(p));
            });
            List<Genome> winners = winnerMap.entrySet().stream()
                .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(numWinnersToReturn)
                .map(Entry::getKey)
                .toList();
            return Mono.just(winners);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    protected static boolean shouldShortCircuit(int roundsComplete, int gamesInRound, int gamesFinishedInRound, int maxGames, Collection<Integer> wins) {
        int playedGames = (roundsComplete * gamesInRound) + gamesFinishedInRound;
        int remainingGames = maxGames - playedGames;
        Integer maxWins = wins.stream()
            .max(Integer::compareTo)
            .orElse(0);
        long possibleWinners = wins.stream()
            .filter(numWins -> (numWins + remainingGames) >= maxWins)
            .count();
        return possibleWinners == 1;
    }
}
