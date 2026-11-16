package com.jay.wahoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.wahoo.service.GameService;
import com.jay.wahoo.ai.Decision;
import com.jay.wahoo.ai.Policy;
import com.jay.wahoo.config.PolicyConfig.BotPolicies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebGameplayTest {
    @Autowired MockMvc web;
    @Autowired ObjectMapper json;
    @Autowired GameService games;

    @TestConfiguration
    static class TestPolicies {
        @Bean
        @Primary
        BotPolicies testBotPolicies() {
            return new BotPolicies(Policy.HEURISTIC, Optional.of(Policy.HEURISTIC));
        }
    }

    @Test
    void createsAPlayableGameAndPreservesTheHumanJoinAndForfeitContracts() throws Exception {
        String id = games.createGame(false).identifier;

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

    @Test
    void enablesPreviewGamesWithoutChangingTheDefault() throws Exception {
        web.perform(get("/api/bots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rulesSchema").value(Decision.SCHEMA))
            .andExpect(jsonPath("$.ppoDefault").value(false))
            .andExpect(jsonPath("$.ppoPreview").value(true));

        web.perform(get("/api/create"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.games[?(@.ppoPreview == false)]").isNotEmpty());
        web.perform(get("/api/create").param("ppoPreview", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.games[?(@.ppoPreview == true)]").isNotEmpty());
    }

    @Test
    void seatTokensArePrivateAndRotateOnRejoin() throws Exception {
        Game game = games.createGame(false);
        String first = games.joinGame(game, "First", "p0");

        web.perform(get("/api/start/{id}", game.identifier))
            .andExpect(status().isForbidden());
        web.perform(post("/api/forfeit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", game.identifier, "playerId", "p1", "playerToken", first
                ))))
            .andExpect(status().isForbidden());
        web.perform(post("/api/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", game.identifier, "playerId", "p0", "playerToken", first
                ))))
            .andExpect(status().isOk());
        String second = games.joinGame(game, "Second", "p0");
        assertNotEquals(first, second);
        web.perform(get("/api/start/{id}", game.identifier).header("X-Wahoo-Seat", first))
            .andExpect(status().isForbidden());
        String state = web.perform(get("/api/game/{id}", game.identifier))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(state.contains(second));
    }
}
