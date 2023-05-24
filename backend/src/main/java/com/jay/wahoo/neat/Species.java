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
    private int staleness =0;
    Random rand = new Random();

    public Species() {
        super();
    }

    public Species(Genome top){
        this.genomes.add(top);
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

    public void removeWeakGenomes(boolean allButOne){
        sortGenomes();
        int surviveCount = 1;
        if(!allButOne)
            surviveCount = (int)Math.ceil(genomes.size()/2f);

        ArrayList<Genome> survivedGenomes = new ArrayList<>();
        for(int i=0; i<surviveCount; i++){
            survivedGenomes.add(new Genome(genomes.get(i)));
        }
        genomes = survivedGenomes;
    }

    @Deprecated
    public void removeWeakGenome(int childrenToRemove){
        sortGenomes();
        ArrayList<Genome> survived = new ArrayList<>();
        for (int i = 0; i < genomes.size() - childrenToRemove; i++) {
            survived.add(genomes.get(i));
        }
        genomes = survived;
    }

    @JsonIgnore
    public Genome getTopGenome(){
        sortGenomes();
        return genomes.get(0);
    }


    @JsonIgnore
    public Genome breedChild(){
        Genome child = genomes.get(rand.nextInt(genomes.size()));
        child = new Genome(child);
        child.mutate();
        return child;
    }

    public ArrayList<Genome> getGenomes() {
        return genomes;
    }

    public float getTopFitness() {
        topFitness = getTopGenome().getFitness();
        topFitness = getTopGenome().getFitness();
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
