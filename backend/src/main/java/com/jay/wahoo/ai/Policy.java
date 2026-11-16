package com.jay.wahoo.ai;

@FunctionalInterface
public interface Policy {
    int choose(float[] observation, int mask);

    static int bestLegal(float[] scores, int mask) {
        if (mask <= 0 || mask > 15) {
            throw new IllegalArgumentException("Expected a nonempty four-action mask");
        }
        int best = Integer.numberOfTrailingZeros(mask);
        for (int action = best + 1; action < 4; action++) {
            if ((mask & (1 << action)) != 0 && scores[action] > scores[best]) {
                best = action;
            }
        }
        return best;
    }

    Policy HEURISTIC = (observation, mask) -> {
        float[] scores = new float[4];
        for (int action = 0; action < 4; action++) {
            int i = Decision.ACTION_OFFSET + action * Decision.ACTION_FEATURES;
            scores[action] = 0.7f * observation[i + 1] + 3 * observation[i + 3]
                + observation[i + 4] + observation[i + 5] * (0.5f + 2 * observation[i + 6])
                - 3 * observation[i + 7] - 1.2f * observation[i + 8];
        }
        return bestLegal(scores, mask);
    };
}
