package com.jay.wahoo.service;

import com.jay.wahoo.neat.ConnectionGene;
import com.jay.wahoo.neat.Genome;
import com.jay.wahoo.neat.config.NEAT_Config;
import com.jay.wahoo.service.GenomeService.Network.Node;
import com.jay.wahoo.service.GenomeService.Network.Node.Connection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GenomeService {

    public Network getNetwork(Genome genome) {
        Map<Integer, List<ConnectionGene>> connectionMap = genome.getConnectionGeneList().stream()
            .filter(ConnectionGene::isEnabled)
            .collect(Collectors.groupingBy(ConnectionGene::getInto));
        List<Node> inputNodes = new ArrayList<>();
        // This is <= instead of < because of the bias node
        for (int i = 0; i <= NEAT_Config.INPUTS; i++) {
            inputNodes.add(getNextLevel(connectionMap.getOrDefault(i, List.of()), i, connectionMap));
        }
        Map<Integer, List<Node>> nodeMap = inputNodes.stream()
            .flatMap(n -> n.connections.stream().map(c -> c.outNode))
            .collect(Collectors.groupingBy(n -> n.nodeId));
        inputNodes.stream()
            .flatMap(n -> n.connections.stream())
            .forEach(conn -> conn.outNode = nodeMap.get(conn.outNode.nodeId).get(0));
        return new Network(inputNodes);
    }

    private Node getNextLevel(List<ConnectionGene> currentConnections, int nodeId, Map<Integer, List<ConnectionGene>> connectionMap) {
        List<Connection> connections = currentConnections.stream()
            .map(conn -> new Connection(getNextLevel(connectionMap.getOrDefault(conn.getOut(), List.of()), conn.getOut(), connectionMap), conn.getWeight()))
            .toList();
        return new Node(nodeId, connections);
    }

    @AllArgsConstructor
    public static class Network {
        public List<Node> nodes;

        @AllArgsConstructor
        public static class Node {
            public int nodeId;
            public List<Connection> connections;

            @AllArgsConstructor
            public static class Connection {
                public Node outNode;
                public float weight;
            }

        }
    }

}
