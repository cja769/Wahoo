package com.jay.wahoo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WahooEnvironmentTest {

    @Test
    void test_shouldShortCircuit_true() {
        List<Integer> winList = List.of(15, 23, 8, 7);
        assertThat(WahooEnvironment.shouldShortCircuit(17, 3, 2, 60, winList)).isTrue();
    }

    @Test
    void test_shouldShortCircuit_false() {
        List<Integer> winList = List.of(15, 22, 8, 7);
        assertThat(WahooEnvironment.shouldShortCircuit(17, 3, 1, 60, winList)).isFalse();
    }
}
