package com.jay.wahoo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.Pool;
import com.jay.wahoo.neat.Species;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
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
            pool = new ObjectMapper().readerFor(Pool.class).readValue(poolJson);
        } else {
            pool.initializePool();
        }
        return pool;
    }

    public List<Genome> getPlayersFromPool(Pool pool, int numPlayers) {
        ArrayList<Genome> allGenome = new ArrayList<>();

        for(Species s: pool.getSpecies()){
            for(Genome g: s.getGenomes()){
                allGenome.add(g);
            }
        }
        Collections.sort(allGenome,Collections.reverseOrder());

        List<Genome> players = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            players.add(allGenome.get(i));
        }
        return players;
    }

    public void savePool(Pool pool) throws IOException {
        String serialized = new ObjectMapper().writer().writeValueAsString(pool);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("pool.json"), "utf-8"))) {
            writer.write(serialized);
        }
    }
}
