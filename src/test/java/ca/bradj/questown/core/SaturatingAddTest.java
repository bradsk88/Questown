package ca.bradj.questown.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * r16 #5: room recipe weights are summed per ingredient, and a "never recommend" item weighs
 * {@code Integer.MAX_VALUE} (the plate). Four plates overflowed a plain int sum to -4, so the
 * dining room weighed less than the empty room (0) and was announced as "Empty Room".
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
