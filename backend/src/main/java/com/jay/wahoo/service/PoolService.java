package com.jay.wahoo.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.Pool;
import com.jay.wahoo.neat.Species;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class PoolService {

    public Pool getPool() throws IOException {
        Pool pool = new Pool();
        if (new File("pool.json").exists()) {
            String poolJson;
            try(BufferedReader br = new BufferedReader(new FileReader("pool.json"))) {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                poolJson = sb.toString();
            }
            pool = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readerFor(Pool.class)
                .readValue(poolJson);
        } else {
            pool.initializePool();
        }
        return pool;
    }

    public List<Genome> getPlayersFromPool() throws IOException {
        if (new File("best.json").exists()) {
            String poolJson;
            try(BufferedReader br = new BufferedReader(new FileReader("best.json"))) {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                poolJson = sb.toString();
            }
            Best best = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readerFor(Best.class)
                .readValue(poolJson);
            return best.genomes;
        }
        Pool pool = getPool();
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: pool.getSpecies()){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome,Collections.reverseOrder());

        List<Genome> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            players.add(allGenome.get(i));
        }
        return players;
    }

    public void savePool(Pool pool) throws IOException {
        String serialized = new ObjectMapper().writer().writeValueAsString(pool);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("pool.json"), "utf-8"))) {
            writer.write(serialized);
        }
        List<Genome> genomes = pool.getSpecies().stream()
            .map(Species::getTopGenome)
            .sorted(Comparator.reverseOrder())
            .limit(4)
            .toList();
        String best = new ObjectMapper().writer().writeValueAsString(new Best(genomes));
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("best.json"), "utf-8"))) {
            writer.write(best);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class Best {
        public List<Genome> genomes;
    }
}
