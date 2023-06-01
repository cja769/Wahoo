package com.jay.wahoo.neat;

import com.jay.wahoo.neat.config.NEAT_Config;

import javax.management.RuntimeErrorException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by vishnughosh on 28/02/17.
 */
public class Genome implements Comparable {
    private static Random rand = new Random();
    private float fitness;                                          // Global Percentile Rank (higher the better)
    private float points;
    private ArrayList<ConnectionGene> connectionGeneList = new ArrayList<>();           // DNA- MAin archive of gene information
    private TreeMap<Integer, NodeGene> nodes = new TreeMap<>();                          // Generated while performing network operation
    private float adjustedFitness;                                      // For number of child to breed in species

    private HashMap<MutationKeys, Float> mutationRates = new HashMap<>();

    private enum MutationKeys {
        STEPS,
        PERTURB_CHANCE,
        WEIGHT_CHANCE,
        WEIGHT_MUTATION_CHANCE,
        NODE_MUTATION_CHANCE,
        CONNECTION_MUTATION_CHANCE,
        BIAS_CONNECTION_MUTATION_CHANCE,
        DISABLE_MUTATION_CHANCE,
        ENABLE_MUTATION_CHANCE
    }
    /*    private class MutationRates{
            float STEPS;
            float PERTURB_CHANCE;
            float WEIGHT_CHANCE;
            float WEIGHT_MUTATION_CHANCE;
            float NODE_MUTATION_CHANCE;
            float CONNECTION_MUTATION_CHANCE;
            float BIAS_CONNECTION_MUTATION_CHANCE;
            float DISABLE_MUTATION_CHANCE;
            float ENABLE_MUTATION_CHANCE;

             MutationRates() {
                this.STEPS = NEAT_Config.STEPS;
                this.PERTURB_CHANCE = NEAT_Config.PERTURB_CHANCE;
                this.WEIGHT_CHANCE = NEAT_Config.WEIGHT_CHANCE;
                this.WEIGHT_MUTATION_CHANCE = NEAT_Config.WEIGHT_MUTATION_CHANCE;
                this.NODE_MUTATION_CHANCE = NEAT_Config.NODE_MUTATION_CHANCE;
                this.CONNECTION_MUTATION_CHANCE = NEAT_Config.CONNECTION_MUTATION_CHANCE;
                this.BIAS_CONNECTION_MUTATION_CHANCE = NEAT_Config.BIAS_CONNECTION_MUTATION_CHANCE;
                this.DISABLE_MUTATION_CHANCE = NEAT_Config.DISABLE_MUTATION_CHANCE;
                this.ENABLE_MUTATION_CHANCE = NEAT_Config.ENABLE_MUTATION_CHANCE;
            }
        }*/
    public Genome(){

        this.mutationRates.put(MutationKeys.STEPS, NEAT_Config.STEPS);
        this.mutationRates.put(MutationKeys.PERTURB_CHANCE, NEAT_Config.PERTURB_CHANCE);
        this.mutationRates.put(MutationKeys.WEIGHT_CHANCE,NEAT_Config.WEIGHT_CHANCE);
        this.mutationRates.put(MutationKeys.WEIGHT_MUTATION_CHANCE, NEAT_Config.WEIGHT_MUTATION_CHANCE);
        this.mutationRates.put(MutationKeys.NODE_MUTATION_CHANCE , NEAT_Config.NODE_MUTATION_CHANCE);
        this.mutationRates.put(MutationKeys.CONNECTION_MUTATION_CHANCE , NEAT_Config.CONNECTION_MUTATION_CHANCE);
        this.mutationRates.put(MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE , NEAT_Config.BIAS_CONNECTION_MUTATION_CHANCE);
        this.mutationRates.put(MutationKeys.DISABLE_MUTATION_CHANCE , NEAT_Config.DISABLE_MUTATION_CHANCE);
        this.mutationRates.put(MutationKeys.ENABLE_MUTATION_CHANCE , NEAT_Config.ENABLE_MUTATION_CHANCE);
    }

    public Genome(Genome child) {

        for (ConnectionGene c:child.connectionGeneList){
            this.connectionGeneList.add(new ConnectionGene(c));
        }

        this.fitness = child.fitness;
        this.adjustedFitness = child.adjustedFitness;

        this.mutationRates = (HashMap<MutationKeys, Float>) child.mutationRates.clone();

    }

    public float getFitness() {
        return fitness;
    }

    public void setFitness(float fitness) {
        this.fitness = fitness;
    }

    public ArrayList<ConnectionGene> getConnectionGeneList() {
        return connectionGeneList;
    }

    public Map<Integer, NodeGene> getNodes() {
        return nodes;
    }

    public void setConnectionGeneList(ArrayList<ConnectionGene> connectionGeneList) {
        this.connectionGeneList = connectionGeneList;
    }

    private void generateNetwork() {

        nodes.clear();
        //  Input layer
        for (int i = 0; i < NEAT_Config.INPUTS; i++) {
            nodes.put(i, new NodeGene(0));                    //Inputs
        }
        nodes.put(NEAT_Config.INPUTS, new NodeGene(1));        // Bias

        //output layer
        for (int i = NEAT_Config.INPUTS + NEAT_Config.HIDDEN_NODES; i < NEAT_Config.INPUTS + NEAT_Config.HIDDEN_NODES + NEAT_Config.OUTPUTS; i++) {
            nodes.put(i, new NodeGene(0));
        }

        // hidden layer
        for (ConnectionGene con : connectionGeneList) {
            if (!nodes.containsKey(con.getInto()))
                nodes.put(con.getInto(), new NodeGene(0));
            if (!nodes.containsKey(con.getOut()))
                nodes.put(con.getOut(), new NodeGene(0));
            nodes.get(con.getOut()).getIncomingCon().add(con);
        }


    }

    public float[] evaluateNetwork(float[] inputs) {
        float output[] = new float[NEAT_Config.OUTPUTS];
        generateNetwork();

        for (int i = 0; i < NEAT_Config.INPUTS; i++) {
            nodes.get(i).setValue(inputs[i]);
        }

        for (Map.Entry<Integer, NodeGene> mapEntry : nodes.entrySet()) {
            float sum = 0;
            int key = mapEntry.getKey();
            NodeGene node = mapEntry.getValue();

            if (key > NEAT_Config.INPUTS) {
                for (ConnectionGene conn : node.getIncomingCon()) {
                    if (conn.isEnabled()) {
                        sum += nodes.get(conn.getInto()).getValue() * conn.getWeight();
                    }
                }
                node.setValue((float) Math.tanh(sum));
            }
        }

        for (int i = 0; i < NEAT_Config.OUTPUTS; i++) {
            output[i] = nodes.get(NEAT_Config.INPUTS + NEAT_Config.HIDDEN_NODES + i).getValue();
        }
        return output;
    }

    // Mutations

    public void mutate() {
        // Mutate mutation rates
        for (Map.Entry<MutationKeys, Float> entry : mutationRates.entrySet()) {
            if(rand.nextBoolean())
                mutationRates.put(entry.getKey(), 0.95f * entry.getValue() );
            else
                mutationRates.put(entry.getKey(), 1.05263f * entry.getValue() );
        }


        if (rand.nextFloat() <= mutationRates.get(MutationKeys.WEIGHT_MUTATION_CHANCE))
            mutateWeight();
        if (rand.nextFloat() <= mutationRates.get(MutationKeys.CONNECTION_MUTATION_CHANCE))
            mutateAddConnection(false);
        if (rand.nextFloat() <= mutationRates.get(MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE))
            mutateAddConnection(true);
        if (rand.nextFloat() <= mutationRates.get(MutationKeys.NODE_MUTATION_CHANCE))
            mutateAddNode();
        if (rand.nextFloat() <= mutationRates.get(MutationKeys.DISABLE_MUTATION_CHANCE))
            disableMutate();
        if (rand.nextFloat() <= mutationRates.get(MutationKeys.ENABLE_MUTATION_CHANCE))
            enableMutate();
    }

    void mutateWeight() {

        for (ConnectionGene c : connectionGeneList) {
            if (rand.nextFloat() < NEAT_Config.WEIGHT_CHANCE) {
                if (rand.nextFloat() < NEAT_Config.PERTURB_CHANCE)
                    c.setWeight(c.getWeight() + (2 * rand.nextFloat() - 1) * NEAT_Config.STEPS);
                else c.setWeight(4 * rand.nextFloat() - 2);
            }
        }
    }

    void mutateAddConnection(boolean forceBais) {
        generateNetwork();
        int i = 0;
        int j = 0;
        int random2 = rand.nextInt(nodes.size() - NEAT_Config.INPUTS - 1) + NEAT_Config.INPUTS + 1;
        int random1 = rand.nextInt(nodes.size());
        if(forceBais)
            random1 = NEAT_Config.INPUTS;
        int node1 = -1;
        int node2 = -1;

        for (int k : nodes.keySet()) {
            if (random1 == i) {
                node1 = k;
                break;
            }
            i++;
        }

        for (int k : nodes.keySet()) {
            if (random2 == j) {
                node2 = k;
                break;
            }
            j++;
        }
//	System.out.println("random1 = "+random1 +" random2 = "+random2);
//	System.out.println("Node1 = "+node1 +" node 2 = "+node2);


        if (node1 >= node2)
            return;

        for (ConnectionGene con : nodes.get(node2).getIncomingCon()) {
            if (con.getInto() == node1)
                return;
        }

        if (node1 < 0 || node2 < 0)
            throw new RuntimeErrorException(null);          // TODO Pool.newInnovation(node1, node2)
        connectionGeneList.add(new ConnectionGene(node1, node2, 4 * rand.nextFloat() - 2, true));                // Add innovation and weight

    }

    void mutateAddNode() {
        generateNetwork();
        if (connectionGeneList.size() > 0) {
            int timeoutCount = 0;
            int nextNode = nodes.size() - NEAT_Config.OUTPUTS;
            ConnectionGene randomCon = connectionGeneList.get(rand.nextInt(connectionGeneList.size()));
            while (!randomCon.isEnabled() || nextNode > randomCon.getOut() || nextNode < randomCon.getInto()) {
                randomCon = connectionGeneList.get(rand.nextInt(connectionGeneList.size()));
                timeoutCount++;
                if (timeoutCount > NEAT_Config.HIDDEN_NODES) {
                    return;
                }
            }
            randomCon.setEnabled(false);
            connectionGeneList.add(new ConnectionGene(randomCon.getInto(), nextNode, 1, true));        // Add innovation and weight
            connectionGeneList.add(new ConnectionGene(nextNode, randomCon.getOut(), randomCon.getWeight(), true));
        }
    }
    void disableMutate() {
        //generateNetwork();                // remove laters
        if (connectionGeneList.size() > 0) {
            ConnectionGene randomCon = connectionGeneList.get(rand.nextInt(connectionGeneList.size()));
            randomCon.setEnabled(false);
        }
    }


    void enableMutate() {
        //generateNetwork();                // remove laters
        if (connectionGeneList.size() > 0) {
            ConnectionGene randomCon = connectionGeneList.get(rand.nextInt(connectionGeneList.size()));
            randomCon.setEnabled(true);
        }
    }

    @Override
    public int compareTo(Object o) {
        Genome g = (Genome)o;
        if (fitness==g.fitness)
            return 0;
        else if(fitness >g.fitness)
            return 1;
        else
            return -1;
    }

    @Override
    public String toString() {
        return "Genome{" +
                "fitness=" + fitness +
                ", connectionGeneList=" + connectionGeneList +
                ", nodeGenes=" + nodes +
                '}';
    }

    public void setAdjustedFitness(float adjustedFitness) {
        this.adjustedFitness = adjustedFitness;
    }

    public float getAdjustedFitness() {
        return adjustedFitness;
    }

    public float getPoints() {
        return points;
    }

    public void setPoints(float points) {
        this.points = points;
    }

    public void writeTofile(){
        BufferedWriter bw = null;
        FileWriter fw = null;
        StringBuilder builder = new StringBuilder();
        for (ConnectionGene conn: connectionGeneList) {
            builder.append(conn.toString()+"\n");
        }
        try {


            fw = new FileWriter("Genome.txt");
            bw = new BufferedWriter(fw);
            bw.write(builder.toString());

            System.out.println("Done");

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            try {

                if (bw != null)
                    bw.close();

                if (fw != null)
                    fw.close();

            } catch (IOException ex) {

                ex.printStackTrace();

            }

        }

    }

}
