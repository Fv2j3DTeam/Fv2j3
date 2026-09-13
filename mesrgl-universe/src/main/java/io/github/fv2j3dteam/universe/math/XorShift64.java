package io.github.fv2j3dteam.universe.math;

import java.util.Objects;

/**
 * Deterministic 64-bit PRNG (xorshift64*) with explicit streams.
 * Independent of any global mutable state (§5). No synchronisation is needed
 * because each instance owns its state.
 */
public final class XorShift64 {
    private long state;

    public XorShift64(long seed) {
        this.state = normaliseSeed(seed);
    }

    private static long normaliseSeed(long seed) {
        if (seed == 0L) {
            // xorshift requires non-zero state; map 0 to an arbitrary constant.
            return 0x9E3779B97F4A7C15L;
        }
        return seed;
    }

    public long nextLong() {
        long s = state;
        s ^= s >>> 12;
        s ^= s << 25;
        s ^= s >>> 27;
        state = s;
        return s * 0x2545F4914F6CDD1DL;
    }

    /** Returns a uniformly distributed double in [0,1). */
    public double nextDouble() {
        // Use the top 53 bits.
        return (nextLong() >>> 11) * (1.0 / (1L << 53));
    }

    /** Returns a uniformly distributed double in [min, max). */
    public double nextDouble(double min, double max) {
        Objects.requireNonNull(this, "this");
        if (max < min) throw new IllegalArgumentException("max < min");
        return min + nextDouble() * (max - min);
    }

    public int nextInt(int minInclusive, int maxExclusive) {
        Objects.requireNonNull(this, "this");
        if (maxExclusive <= minInclusive) throw new IllegalArgumentException("bad range");
        long range = (long) maxExclusive - (long) minInclusive;
        long r = Math.floorMod(nextLong(), range);
        return (int) r + minInclusive;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0L;
    }

    /** Returns the raw state; primarily for diagnostics. */
    public long state() {
        return state;
    }
}