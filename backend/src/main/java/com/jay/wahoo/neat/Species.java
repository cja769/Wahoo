package com.jay.wahoo.neat;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jay.wahoo.neat.config.NEAT_Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Created by vishnu on 7/1/17.
 */
public class Species implements Comparable{
    private ArrayList<Genome> genomes = new ArrayList<>();
    private float topFitness = 0;
    private int staleness = 0;

    public Species() {
        super();
    }

    public void calculateGenomeAdjustedFitness(){
        for(Genome g : genomes){
            g.setAdjustedFitness(g.getFitness()/genomes.size());
        }
    }

    @JsonIgnore
    public float getTotalAdjustedFitness(){
        float totalAdjustedFitness = 0;
        for(Genome g: genomes){
            totalAdjustedFitness += g.getAdjustedFitness();
        }

         return totalAdjustedFitness;
    }

    private void  sortGenomes(){
        //sort internally genomes
        Collections.sort(genomes,Collections.reverseOrder());
    }

    @JsonIgnore
    public Genome getTopGenome(){
        sortGenomes();
        return genomes.get(0);
    }


    @JsonIgnore
    public Genome breedChild(){
        int index = new Random().nextInt(getGenomes().size());
        Genome child = getGenomes().get(index);
        child = new Genome(child);
        child.mutate();
        return child;
    }

    public ArrayList<Genome> getGenomes() {
        return genomes;
    }

    public float getTopFitness() {
        return topFitness;
    }

    public void setTopFitness(float topFitness) {
        this.topFitness = topFitness;
    }

    public int getStaleness() {
        return staleness;
    }

    public void setStaleness(int staleness) {
        this.staleness = staleness;
    }

    @Override
    public int compareTo(Object o) {
        Species s = (Species)o;
        float top = getTopFitness();
        float otherTop = s.getTopFitness();

        if (top==otherTop)
            return 0;
        else if(top >otherTop)
            return 1;
        else
            return -1;
    }
}
