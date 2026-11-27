package com.ringoway.ringolib.core.content;

import java.util.List;

/**
 * Integer weight-scaling math for rewriting vanilla {@code LootPool} entries.
 */
public final class LootWeightMath {

    private static final long MAX_SCALED_WEIGHT = 1_000_000L;
    private static final int MAX_EXACT_SPLIT_SUM = 10_000;
    private static final int[] DECIMAL_SCALES = {1, 10, 100, 1_000, 10_000};

    private LootWeightMath() {
    }

    public record IntegerWeights(int[] parts, int denominator) {
    }

    public static IntegerWeights toExactIntegerWeights(double[] weights) {
        if (weights == null || weights.length == 0) {
            throw new IllegalArgumentException("Weights array must be non-empty");
        }
        double total = 0.0;
        for (double w : weights) {
            if (!Double.isFinite(w) || w <= 0.0) {
                throw new IllegalArgumentException("Every weight must be finite and > 0, got: " + w);
            }
            total += w;
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("Total weight must be finite and > 0, got: " + total);
        }

        for (int scale : DECIMAL_SCALES) {
            IntegerWeights exact = tryExactDecimalScale(weights, scale);
            if (exact != null) {
                return exact;
            }
        }

        long[] raw = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            double normalized = (weights[i] / total) * 1_000.0;
            raw[i] = Math.max(1L, Math.round(normalized));
        }
        return reduceToIntegerWeights(raw);
    }

    private static IntegerWeights tryExactDecimalScale(double[] weights, int scale) {
        long[] scaled = new long[weights.length];
        for (int i = 0; i < weights.length; i++) {
            double val = weights[i] * scale;
            if (!Double.isFinite(val) || val > 1_000_000_000.0) {
                return null;
            }
            long rounded = Math.round(val);
            if (rounded < 1L || Math.abs(val - rounded) > 1e-6) {
                return null;
            }
            scaled[i] = rounded;
        }
        IntegerWeights reduced = reduceToIntegerWeights(scaled);
        return reduced.denominator() <= MAX_EXACT_SPLIT_SUM ? reduced : null;
    }

    private static IntegerWeights reduceToIntegerWeights(long[] raw) {
        long g = raw[0];
        for (int i = 1; i < raw.length; i++) {
            g = gcd(g, raw[i]);
        }
        g = Math.max(1L, g);

        int[] parts = new int[raw.length];
        long sum = 0L;
        for (int i = 0; i < raw.length; i++) {
            long part = raw[i] / g;
            if (part <= 0L || part > Integer.MAX_VALUE) {
                throw new ArithmeticException("Scaled weight out of int range: " + part);
            }
            parts[i] = (int) part;
            sum += part;
            if (sum > Integer.MAX_VALUE) {
                throw new ArithmeticException("Denominator out of int range: " + sum);
            }
        }
        return new IntegerWeights(parts, (int) sum);
    }

    public static int computePoolScale(List<Integer> denominators, int maxOriginalWeight) {
        if (denominators == null || denominators.isEmpty()) {
            return 1;
        }
        long clampedMaxOrig = Math.max(1L, maxOriginalWeight);
        long maxAllowedScale = Math.max(1L, MAX_SCALED_WEIGHT / clampedMaxOrig);

        long current = 1L;
        for (Integer denomObj : denominators) {
            if (denomObj == null || denomObj <= 1) {
                continue;
            }
            long d = denomObj;
            long g = gcd(current, d);
            long factor = d / g;
            if (current > maxAllowedScale / Math.max(1L, factor)) {
                return (int) Math.min(1_000L, maxAllowedScale);
            }
            current *= factor;
        }
        return (int) Math.max(1L, current);
    }

    public static int[] distributeWeight(int originalWeight, int poolScale, IntegerWeights split) {
        int[] parts = split.parts();
        int n = parts.length;
        int denom = Math.max(1, split.denominator());
        long budget = (long) Math.max(1, originalWeight) * Math.max(1, poolScale);

        int[] result = new int[n];
        if (budget % denom == 0L) {
            long unit = budget / denom;
            for (int i = 0; i < n; i++) {
                result[i] = (int) Math.min(Integer.MAX_VALUE, parts[i] * unit);
            }
            return result;
        }

        double[] exact = new double[n];
        long allocated = 0L;
        for (int i = 0; i < n; i++) {
            exact[i] = ((double) budget * parts[i]) / denom;
            int floor = (int) Math.floor(exact[i]);
            result[i] = Math.max(1, floor);
            allocated += result[i];
        }

        long diff = budget - allocated;
        while (diff > 0L) {
            int bestIdx = -1;
            double bestRemainder = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double rem = exact[i] - result[i];
                if (rem > bestRemainder) {
                    bestRemainder = rem;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                break;
            }
            result[bestIdx]++;
            diff--;
        }

        while (diff < 0L) {
            int bestIdx = -1;
            double worstRemainder = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (result[i] > 1) {
                    double rem = exact[i] - result[i];
                    if (rem < worstRemainder) {
                        worstRemainder = rem;
                        bestIdx = i;
                    }
                }
            }
            if (bestIdx < 0) {
                break;
            }
            result[bestIdx]--;
            diff++;
        }

        return result;
    }

    public static long gcd(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0L) {
            long t = x % y;
            x = y;
            y = t;
        }
        return x == 0L ? 1L : x;
    }

    public static long lcm(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        long x = Math.abs(a);
        long y = Math.abs(b);
        return (x / gcd(x, y)) * y;
    }
}
