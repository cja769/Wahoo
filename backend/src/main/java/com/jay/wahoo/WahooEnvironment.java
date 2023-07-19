package com.jay.wahoo;

import com.jay.wahoo.neat.Environment;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.Species;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

@Slf4j
public class WahooEnvironment implements Environment {

    private final Integer maxTurns = 500;

    @Override
    public void evaluateFitness(List<Species> species) {
        log.info("Starting training");
        List<Mono<Genome>> newTops = species.stream()
            .map(this::playSpecies)
            .toList();
        Flux.fromIterable(newTops)
            .flatMap(Function.identity(), 2)
            .collectList()
            .flatMap(this::playTopSpeciesRoundRobin)
            .block();
    }

    protected Mono<Genome> playTopSpeciesRoundRobin(List<Genome> genomes) {
        log.info("Starting species round robin");
        List<Mono<MatchResult>> results = new ArrayList<>();
        for (int i = 0; i < genomes.size() - 1; i++) {
            for (int j = (i + 1); j < genomes.size(); j++) {
                results.add(playMatch(genomes.get(i), genomes.get(j), false));
            }
        }
        return Flux.fromIterable(results)
            .flatMap(Function.identity(), 1)
            .collectList()
            .flatMap(matchResults -> {
                int additionalFitness = 10;
                Map<Genome, Integer> winMap = new HashMap<>();
                genomes.forEach(g -> winMap.put(g, 0));
                for (MatchResult result : matchResults) {
                    {
                        Integer wins = winMap.get(result.getPlayerOne());
                        wins += result.getPlayerOneWins();
                        winMap.put(result.getPlayerOne(), wins);
                    }
                    {
                        Integer wins = winMap.get(result.getPlayerTwo());
                        wins += result.getPlayerTwoWins();
                        winMap.put(result.getPlayerTwo(), wins);
                    }
                }
                List<Entry<Genome, Integer>> sortedEntries = winMap.entrySet().stream()
                    .sorted(Entry.comparingByValue())
                    .toList();
                for (Entry<Genome, Integer> sortedEntry : sortedEntries) {
                    Genome genome = sortedEntry.getKey();
                    genome.setFitness(genome.getFitness() + additionalFitness);
                    additionalFitness += 10;
                }
                return playMatch(sortedEntries.get(sortedEntries.size() - 1).getKey(), sortedEntries.get(sortedEntries.size() - 2).getKey(), true, 10)
                    .map(mr -> sortedEntries.get(sortedEntries.size() - 1).getKey());
            });
    }

    protected Mono<Genome> playSpecies(Species species) {
        Genome top = species.getTopGenome();
        top.setFitness(0);
        List<Mono<MatchResult>> matches = species.getGenomes().stream()
            .filter(g -> !g.equals(top))
            .map(g -> {
                g.setFitness(0);
                return playMatch(top, g, false);
            })
            .toList();
        return Flux.fromIterable(matches)
            .flatMap(Function.identity(), 1)
            .collectList()
            .map(winnerMaps -> {
                Map<Boolean, List<MatchResult>> collectedOnComparativeWins = winnerMaps.stream()
                    .collect(Collectors.groupingBy(mr -> mr.playerTwoWins > mr.playerOneWins));
                List<MatchResult> beatTop = collectedOnComparativeWins.getOrDefault(true, List.of());
                List<MatchResult> lostToTop = collectedOnComparativeWins.getOrDefault(false, List.of());
                List<MatchResult> sortedBeatTop = beatTop.stream()
                    .sorted(Comparator.comparingInt(a -> a.playerTwoWins))
                    .toList();
                List<MatchResult> sortedLost = lostToTop.stream()
                    .sorted(Comparator.comparingInt(a -> a.playerTwoWins))
                    .toList();
                int fitness = 10;
                for (MatchResult matchResult : sortedLost) {
                    matchResult.getPlayerTwo().setFitness(fitness);
                    fitness += 10;
                }
                top.setFitness(fitness);
                fitness += 10;
                for (MatchResult matchResult : sortedBeatTop) {
                    matchResult.getPlayerTwo().setFitness(fitness);
                    fitness += 10;
                }
                log.info("Species(" + species.getIdentifier() + ") training finished");
                if (beatTop.size() == 0) {
                    log.info("No genomes did better than the previous top genome");
                    return top;
                }
                log.info(beatTop.size() + " genome(s) did better than the previous top genome");
                return beatTop.get(beatTop.size() - 1).getPlayerTwo();
            })
            .doOnSubscribe(s -> log.info("Species(" + species.getIdentifier() + ") training started"));
    }

    protected Mono<MatchResult> playMatch(Genome p1, Genome p2, boolean verbose) {
        return playMatch(p1, p2, verbose, 30);
    }

    protected Mono<MatchResult> playMatch(Genome p1, Genome p2, boolean verbose, int rounds) {
        return Mono.defer(() -> {
            List<Genome> currentGame = new ArrayList<>();
            currentGame.add(p1);
            currentGame.add(p2);
            currentGame.add(p1);
            currentGame.add(p2);
            Map<Genome, Integer> winnerMap = new HashMap<>();
            winnerMap.put(p1, 0);
            winnerMap.put(p2, 0);
            for (int round = 0; round < rounds; round++) {
                Genome winner = new Game(currentGame, verbose, maxTurns, true).play().stream()
                    .findFirst()
                    .get();
                Integer wins = winnerMap.get(winner);
                if (wins == null) {
                    wins = 1;
                } else {
                    wins++;
                }
                winnerMap.put(winner, wins);
            }
            return Mono.just(MatchResult.builder()
                .playerOneWins(winnerMap.get(p1))
                .playerTwoWins(winnerMap.get(p2))
                .playerOne(p1)
                .playerTwo(p2)
                .build());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Builder
    @Data
    private static class MatchResult {
        int playerOneWins;
        Genome playerOne;
        int playerTwoWins;
        Genome playerTwo;
    }

}
