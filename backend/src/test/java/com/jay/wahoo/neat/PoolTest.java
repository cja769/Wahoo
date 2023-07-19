package com.jay.wahoo.neat;

import com.jay.wahoo.neat.config.NEAT_Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PoolTest {

    @Test
    public void test_removeStaleSpecies_base() {
        Pool pool = new Pool();
        Species s1 = new Species();
        Species s2 = new Species();
        Genome g1 = new Genome();
        Genome g2 = new Genome();
        g1.setFitness(1);
        g2.setFitness(2);
        s1.getGenomes().add(g1);
        s2.getGenomes().add(g2);
        pool.getSpecies().add(s1);
        pool.getSpecies().add(s2);
        pool.removeStaleSpecies();
        assertThat(s1.getStaleness()).isEqualTo(1);
        assertThat(s2.getStaleness()).isEqualTo(0);
        assertThat(pool.getSpecies().size()).isEqualTo(2);
    }

    @Test
    public void test_removeStaleSpecies_reset() {
        Pool pool = new Pool();
        Species s1 = new Species();
        Species s2 = new Species();
        Genome g1 = new Genome();
        Genome g2 = new Genome();
        g1.setFitness(1);
        g2.setFitness(2);
        s1.getGenomes().add(g1);
        s2.getGenomes().add(g2);
        s2.setStaleness(10);
        pool.getSpecies().add(s1);
        pool.getSpecies().add(s2);
        pool.removeStaleSpecies();
        assertThat(s1.getStaleness()).isEqualTo(1);
        assertThat(s2.getStaleness()).isEqualTo(0);
        assertThat(pool.getSpecies().size()).isEqualTo(2);
    }

    @Test
    public void test_removeStaleSpecies_removeStale() {
        Pool pool = new Pool();
        Species s1 = new Species();
        Species s2 = new Species();
        Genome g1 = new Genome();
        Genome g2 = new Genome();
        g1.setFitness(1);
        g2.setFitness(2);
        s1.getGenomes().add(g1);
        s2.getGenomes().add(g2);
        s1.setStaleness(NEAT_Config.STALE_SPECIES - 1);
        s2.setStaleness(10);
        pool.getSpecies().add(s1);
        pool.getSpecies().add(s2);
        pool.removeStaleSpecies();
        assertThat(s1.getStaleness()).isEqualTo(NEAT_Config.STALE_SPECIES);
        assertThat(s2.getStaleness()).isEqualTo(0);
        assertThat(pool.getSpecies().size()).isEqualTo(1);
    }
}
