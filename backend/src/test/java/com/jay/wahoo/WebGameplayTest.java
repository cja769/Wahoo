package com.jay.wahoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebGameplayTest {
    @Autowired MockMvc web;
    @Autowired ObjectMapper json;

    @Test
    void createsAPlayableGameAndPreservesTheHumanJoinAndForfeitContracts() throws Exception {
        String created = web.perform(get("/api/create"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).path("games").get(0).path("gameId").asText();

        String token = web.perform(post("/api/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", id, "playerId", "p0", "playerName", "Human"
                ))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        web.perform(get("/api/game/{gameId}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.states.length()").value(4))
            .andExpect(jsonPath("$.states[0].playerName").value("Human"))
            .andExpect(jsonPath("$.states[0].homePositions.length()").value(4))
            .andExpect(jsonPath("$.hasStarted").value(false));

        web.perform(post("/api/forfeit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", id, "playerId", "p0", "playerToken", token
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endReason").value("FORFEIT"))
            .andExpect(jsonPath("$.winningPlayers.length()").value(2));
        web.perform(get("/api/game/{gameId}", id))
            .andExpect(jsonPath("$.endReason").value("FORFEIT"));
        web.perform(get("/ws/info")).andExpect(status().isOk());
        web.perform(get("/api/train/1")).andExpect(status().isGone());
    }
}
