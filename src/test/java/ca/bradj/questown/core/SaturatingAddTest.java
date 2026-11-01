package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Room recipe weights are summed per ingredient, and a "never recommend" item weighs
 * {@code Integer.MAX_VALUE} (the plate): four plates must not overflow a plain int sum to -4 and
 * make the dining room weigh less than the empty room (0).
 */
class SaturatingAddTest {

    @Test
    void fourNeverRecommendWeights_stayAtMax() {
        int weight = 0;
        for (int i = 0; i < 4; i++) {
            weight = UtilClean.saturatingAdd(weight, Integer.MAX_VALUE);
        }

        Assertions.assertEquals(Integer.MAX_VALUE, weight);
    }

    @Test
    void ordinaryWeights_addNormally() {
        Assertions.assertEquals(52, UtilClean.saturatingAdd(22, 30));
    }
}
