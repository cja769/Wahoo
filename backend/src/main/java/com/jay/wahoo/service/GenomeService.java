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

    private static final Integer FIRST_OUTPUT_NODE = NEAT_Config.INPUTS + NEAT_Config.HIDDEN_NODES;
    private static final Integer LAST_INPUT_NODE = NEAT_Config.INPUTS; // include bias

    public Network getNetwork(Genome genome) {
        Map<Integer, List<ConnectionGene>> connectionMap = genome.getConnectionGeneList().stream()
            .filter(ConnectionGene::isEnabled)
            .collect(Collectors.groupingBy(ConnectionGene::getInto));
        List<Node> inputNodes = new ArrayList<>();
        // This is <= instead of < because of the bias node
        for (int i = 0; i <= NEAT_Config.INPUTS; i++) {
            inputNodes.add(getNextLevel(connectionMap.getOrDefault(i, List.of()), i, connectionMap));
        }
        pruneNetwork(inputNodes);
        return new Network(inputNodes);
    }

    private void pruneNetwork(List<Node> inputs) {
        inputs.forEach(this::pruneNetwork);
    }

    private void pruneNetwork(Node node) {
        if (!node.connections.isEmpty()) {
            node.connections.forEach(c -> pruneNetwork(c.outNode));
        }
        node.connections = node.connections.stream()
            .filter(this::shouldKeepConnection)
            .toList();
    }

    private boolean shouldKeepConnection(Connection connection) {
        return shouldKeepNode(connection.outNode);
    }

    private boolean shouldKeepNode(Node node) {
        if (isInputOrOutput(node)) {
            return true;
        }
        if (node.connections.isEmpty()) {
            return false;
        }
        return node.connections.stream().allMatch(this::shouldKeepConnection);
    }

    private boolean isInputOrOutput(Node node) {
        return node.nodeId <= LAST_INPUT_NODE || node.nodeId >= FIRST_OUTPUT_NODE;
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
