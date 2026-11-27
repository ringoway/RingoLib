package com.ringoway.ringolib.core.content;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootWeightMathTest {

    @Test
    void reducesPercentageAndDecimalWeightsExactly() {
        LootWeightMath.IntegerWeights intSplit =
                LootWeightMath.toExactIntegerWeights(new double[]{55.0, 20.0, 20.0, 5.0});
        assertArrayEquals(new int[]{11, 4, 4, 1}, intSplit.parts());
        assertEquals(20, intSplit.denominator());

        LootWeightMath.IntegerWeights decimalSplit =
                LootWeightMath.toExactIntegerWeights(new double[]{0.55, 0.20, 0.20, 0.05});
        assertArrayEquals(new int[]{11, 4, 4, 1}, decimalSplit.parts());
        assertEquals(20, decimalSplit.denominator());

        LootWeightMath.IntegerWeights simpleSplit =
                LootWeightMath.toExactIntegerWeights(new double[]{1.0, 2.0});
        assertArrayEquals(new int[]{1, 2}, simpleSplit.parts());
        assertEquals(3, simpleSplit.denominator());
    }

    @Test
    void scalesFoundryHallPoolWeightsWithoutDistortion() {
        LootWeightMath.IntegerWeights split =
                LootWeightMath.toExactIntegerWeights(new double[]{55.0, 20.0, 20.0, 5.0});
        int poolScale = LootWeightMath.computePoolScale(List.of(split.denominator()), 3);
        assertEquals(20, poolScale);

        int[] distributed = LootWeightMath.distributeWeight(3, poolScale, split);
        assertArrayEquals(new int[]{33, 12, 12, 3}, distributed);
        assertEquals(3 * poolScale, Arrays.stream(distributed).sum());
    }

    @Test
    void handlesIrrationalWeightsAndCappedLcmFallback() {
        LootWeightMath.IntegerWeights irrational =
                LootWeightMath.toExactIntegerWeights(new double[]{Math.PI, 1.0, Math.E});
        assertEquals(3, irrational.parts().length);
        assertEquals(irrational.denominator(), Arrays.stream(irrational.parts()).sum());
        for (int part : irrational.parts()) {
            assertTrue(part >= 1);
        }

        int maxOrigWeight = 10;
        int cappedScale = LootWeightMath.computePoolScale(List.of(101, 103, 107), maxOrigWeight);
        assertTrue(cappedScale >= 1);
        assertTrue((long) maxOrigWeight * cappedScale <= 1_000_000L);

        int[] distributed = LootWeightMath.distributeWeight(maxOrigWeight, cappedScale, irrational);
        assertEquals((long) maxOrigWeight * cappedScale, Arrays.stream(distributed).asLongStream().sum());
        for (int w : distributed) {
            assertTrue(w >= 1);
        }
    }

    @Test
    void rejectsInvalidWeightArrays() {
        assertThrows(IllegalArgumentException.class, () -> LootWeightMath.toExactIntegerWeights(null));
        assertThrows(IllegalArgumentException.class, () -> LootWeightMath.toExactIntegerWeights(new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> LootWeightMath.toExactIntegerWeights(new double[]{0.0, 1.0}));
        assertThrows(IllegalArgumentException.class, () -> LootWeightMath.toExactIntegerWeights(new double[]{-1.0, 2.0}));
        assertThrows(IllegalArgumentException.class, () -> LootWeightMath.toExactIntegerWeights(new double[]{Double.NaN, 1.0}));
    }
}
