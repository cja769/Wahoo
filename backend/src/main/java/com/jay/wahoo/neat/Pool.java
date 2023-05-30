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
    private float topFitness ;
    private int poolStaleness = 0;
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

    public void evaluateFitness(Environment environment){
        environment.evaluateFitness(getAllGenomes());
        rankGlobally();
    }

    private ArrayList<Genome> getAllGenomes() {
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: species){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        return allGenome;
    }

    // experimental
    private void rankGlobally(){                // set fitness to rank
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: species){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome);
  //      allGenome.get(allGenome.size()-1).writeTofile();
 //       System.out.println("TopFitness : "+ allGenome.get(allGenome.size()-1).getFitness());
        for (int i =0 ; i<allGenome.size(); i++) {
            allGenome.get(i).setPoints(allGenome.get(i).getFitness());      //TODO use adjustedFitness and remove points
            allGenome.get(i).setFitness(i);
        }
    }

    @JsonIgnore
    public Genome getTopGenome(){
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: species){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome,Collections.reverseOrder());

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

    public boolean removeStaleSpecies(){
        ArrayList<Species> survived = new ArrayList<>();

        if(topFitness<getTopFitness()){
            poolStaleness = 0;
            topFitness = getTopFitness();
        }


        for(Species s: species){
            Genome top  = s.getTopGenome();
            if(top.getFitness()>s.getTopFitness()){
                s.setTopFitness(top.getFitness());
                s.setStaleness(0);
            }
            else{
                s.setStaleness(s.getStaleness()+1);     // increment staleness
            }

            if(s.getStaleness()< NEAT_Config.STALE_SPECIES || s.getTopFitness()>= this.getTopFitness()){
                survived.add(s);
            }
        }

        Collections.sort(survived,Collections.reverseOrder());

        boolean poolStale = false;
        if(poolStaleness>NEAT_Config.STALE_POOL){
            Species newBase = survived.get(0);
            survived = new ArrayList<>();
            survived.add(newBase);
            poolStale = true;
        }

        species = survived;
        poolStaleness++;
        return poolStale;
    }

    public void calculateGenomeAdjustedFitness(){
        for (Species s: species) {
            s.calculateGenomeAdjustedFitness();
        }
    }

    @JsonIgnore
    public void breedNewGeneration() {
        log.info("Breeding new generation");
        calculateGenomeAdjustedFitness();
        ArrayList<Species> survived = new ArrayList<>();
        boolean wasStale = removeStaleSpecies();
        log.info("The pool " + (wasStale ? "was" : "wasn't") + " stale");
        for (Species s : species) {
            Species newSpecies = new Species(s.getTopGenome());
            survived.add(newSpecies);
            for (int i = 1; i < getSizeOfSpecies(); i++) {
                newSpecies.getGenomes().add(s.breedChild());
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
    public float getTopFitness(){
        float topFitness = 0;
        Genome topGenome =null;
        for(Species s : species){
            topGenome = s.getTopGenome();
            if(topGenome.getFitness()>topFitness){
                topFitness = topGenome.getFitness();
            }
        }
        return topFitness;
    }


}
