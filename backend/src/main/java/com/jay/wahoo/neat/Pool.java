package com.jay.wahoo.neat;

/**
 * Created by vishnu on 7/1/17.
 */

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jay.wahoo.neat.config.NEAT_Config;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class Pool {


    private ArrayList<Species> species = new ArrayList<>();
    private int generations = 0;
    private float topFitness;
    private boolean freshPool = true;


    public ArrayList<Species> getSpecies() {
        return species;
    }

    public void initializePool() {
        Species specie = new Species();
        for (int i = 1; i <= NEAT_Config.POPULATION; i++) {
            specie.getGenomes().add(new Genome());
            if (i % getSizeOfSpecies() == 0) {
                species.add(specie);
                specie = new Species();
            }
        }
    }

    public boolean isFreshPool() {
        return freshPool;
    }

    public void setFreshPool(boolean isFreshPool) {
        this.freshPool = isFreshPool;
    }

    private int getSizeOfSpecies() {
        return NEAT_Config.POPULATION / getNumberOfSpecies();
    }

    private int getNumberOfSpecies() {
        return 8;
    }

    public int getGenerations() {
        return generations; // Add for serialization
    }

    public void evaluateFitness(Environment environment) {
        environment.evaluateFitness(species);
        rankGlobally();
    }

    // experimental
    private void rankGlobally() {                // set fitness to rank
        ArrayList<Genome> allGenome = new ArrayList<>();

        for (Species s : species) {
            for (Genome g : s.getGenomes()) {
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome);
        //      allGenome.get(allGenome.size()-1).writeTofile();
        //       System.out.println("TopFitness : "+ allGenome.get(allGenome.size()-1).getFitness());
        for (int i = 0; i < allGenome.size(); i++) {
            allGenome.get(i).setPoints(allGenome.get(i).getFitness());      //TODO use adjustedFitness and remove points
            allGenome.get(i).setFitness(i);
        }
    }

    @JsonIgnore
    public Genome getTopGenome() {
        ArrayList<Genome> allGenome = new ArrayList<>();

        for (Species s : species) {
            for (Genome g : s.getGenomes()) {
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome, Collections.reverseOrder());

        return allGenome.get(0);
    }

    // all species must have the totalAdjustedFitness calculated
    @JsonIgnore
    public float calculateGlobalAdjustedFitness() {
        float total = 0;
        for (Species s : species) {
            total += s.getTotalAdjustedFitness();
        }
        return total;
    }

    public void removeStaleSpecies() {
        ArrayList<Species> survived = new ArrayList<>();
        List<Species> orderedSpecies = species.stream()
            .sorted((a, b) -> b.getTopGenome().compareTo(a.getTopGenome()))
            .toList();

        for (Species s : species) {
            Genome top = s.getTopGenome();
            if (top.getFitness() > s.getTopFitness() || top.getFitness() >= getTopFitness()) {
                s.setTopFitness(top.getFitness());
            }
            int rank = orderedSpecies.indexOf(s);
            log.info("Species finished " + (rank + 1) + " of " + species.size());
            if (rank >= species.size() * NEAT_Config.STALE_POS_THRESHOLD) {
                s.setStaleness(s.getStaleness() + 1);
                log.info("Staleness increased");
            } else {
                s.setStaleness(0);
                log.info("Staleness reset");
            }
            log.info("Staleness : " + s.getStaleness());
            if (s.getStaleness() < NEAT_Config.STALE_SPECIES) {
                survived.add(s);
            } else {
                log.info("Species was removed because of staleness");
            }
        }

        Collections.sort(survived, Collections.reverseOrder());
        species = survived;
    }

    protected void removeUnderperformingGenomes(boolean keepOnlyBest) {
        for (Species s : species) {
            int keep = keepOnlyBest ? 1 : (int) (s.getGenomes().size() * NEAT_Config.SURVIVAL_RATE);
            List<Genome> genomesToRemove = s.getGenomes().stream()
                .skip(keep)
                .toList();
            if (genomesToRemove.isEmpty()) {
                throw new IllegalArgumentException("Somehow we're not removing any genomes");
            }
            if (genomesToRemove.size() == s.getGenomes().size()) {
                throw new IllegalArgumentException("Somehow we're about to remove everything from a species");
            }
            if (genomesToRemove.size() != s.getGenomes().size() - keep) {
                throw new IllegalArgumentException("Somehow we're not removing the correct amount of genomes");
            }
            s.getGenomes().removeAll(genomesToRemove);
        }
    }

    public void calculateGenomeAdjustedFitness() {
        for (Species s : species) {
            s.calculateGenomeAdjustedFitness();
        }
    }

    @JsonIgnore
    public void breedNewGeneration() {
        calculateGenomeAdjustedFitness();
        ArrayList<Species> survived = new ArrayList<>();
        removeStaleSpecies();
        removeUnderperformingGenomes(false);
        for (int index = 0; index < species.size(); index++) {
            Species s = species.get(index);
            survived.add(s);
            while (s.getGenomes().size() < getSizeOfSpecies()) {
                s.getGenomes().add(s.breedChild());
            }
        }
        List<Species> newSpecies = new ArrayList<>();
        for (int i = 0; i < (getNumberOfSpecies() - survived.size()); i++) {
            Species s = new Species();
            int k = 0;
            for (int j = 0; j < getSizeOfSpecies(); j++) {
                if (k == survived.size()) {
                    k = 0;
                }
                s.getGenomes().add(survived.get(k).breedChild());
                k++;
            }
            newSpecies.add(s);
        }
        survived.addAll(newSpecies);
        species = survived;
        generations++;
    }

    @JsonIgnore
    public float getTopFitness() {
        float topFitness = 0;
        Genome topGenome = null;
        for (Species s : species) {
            topGenome = s.getTopGenome();
            if (topGenome.getFitness() > topFitness) {
                topFitness = topGenome.getFitness();
            }
        }
        return topFitness;
    }


}
